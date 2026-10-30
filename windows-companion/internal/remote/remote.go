// Package remote serves the phone API over TLS on the local network.
//
// Only a fixed set of commands exists (/v1/pair, /v1/status, /v1/disks, /v1/unpair).
// Nothing the phone sends is ever executed as a command or interpreted as a path.
package remote

import (
	"context"
	"crypto/tls"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net"
	"net/http"
	"strings"
	"sync"
	"time"

	"diskati/companion/internal/devices"
	"diskati/companion/internal/disks"
	"diskati/companion/internal/pairing"
)

// APIVersion is bumped on incompatible protocol changes.
const APIVersion = 1

const (
	headerDevice = "X-Diskati-Device"
	maxBody      = 4096
)

// Options configures the server.
type Options struct {
	Port         int // 0 picks a free port (tests)
	ComputerName string
	Version      string
}

// Server is the phone-facing TLS server.
type Server struct {
	opts        Options
	cert        tls.Certificate
	fingerprint string
	provider    disks.Provider
	store       *devices.Store
	pair        *pairing.Manager
	log         *log.Logger
	limiter     *limiter

	srv *http.Server
	ln  net.Listener
}

// New creates a server. Call Start to listen.
func New(o Options, cert tls.Certificate, fingerprint string, p disks.Provider, st *devices.Store, pm *pairing.Manager, lg *log.Logger) *Server {
	return &Server{opts: o, cert: cert, fingerprint: fingerprint, provider: p, store: st, pair: pm, log: lg,
		limiter: newLimiter(10, time.Minute)}
}

// Start listens on all interfaces but only accepts private-network peers.
func (s *Server) Start() error {
	ln, err := net.Listen("tcp", fmt.Sprintf(":%d", s.opts.Port))
	if err != nil {
		return err
	}
	s.ln = &filterListener{Listener: ln, log: s.log}
	mux := http.NewServeMux()
	mux.HandleFunc("/v1/pair", s.handlePair)
	mux.HandleFunc("/v1/status", s.authed(http.MethodGet, s.handleStatus))
	mux.HandleFunc("/v1/disks", s.authed(http.MethodGet, s.handleDisks))
	mux.HandleFunc("/v1/unpair", s.authed(http.MethodPost, s.handleUnpair))
	s.srv = &http.Server{
		Handler:           mux,
		ReadHeaderTimeout: 5 * time.Second,
		ReadTimeout:       10 * time.Second,
		WriteTimeout:      15 * time.Second,
		IdleTimeout:       30 * time.Second,
		MaxHeaderBytes:    8 << 10,
		ErrorLog:          s.log,
	}
	tlsCfg := &tls.Config{Certificates: []tls.Certificate{s.cert}, MinVersion: tls.VersionTLS12}
	go func() {
		if err := s.srv.Serve(tls.NewListener(s.ln, tlsCfg)); err != nil && err != http.ErrServerClosed {
			s.log.Printf("remote server error: %v", err)
		}
	}()
	s.log.Printf("phone endpoint listening on port %d (private networks only)", s.Port())
	return nil
}

// Port returns the listening port.
func (s *Server) Port() int { return s.ln.Addr().(*net.TCPAddr).Port }

// Fingerprint returns the certificate fingerprint phones pin.
func (s *Server) Fingerprint() string { return s.fingerprint }

// Shutdown stops the server.
func (s *Server) Shutdown(ctx context.Context) error {
	if s.srv == nil {
		return nil
	}
	return s.srv.Shutdown(ctx)
}

type errBody struct {
	Error string `json:"error"`
}

func writeJSON(w http.ResponseWriter, status int, v any) {
	w.Header().Set("Content-Type", "application/json")
	w.Header().Set("Cache-Control", "no-store")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(v)
}

func clientIP(r *http.Request) string {
	h, _, err := net.SplitHostPort(r.RemoteAddr)
	if err != nil {
		return r.RemoteAddr
	}
	return h
}

type pairReq struct {
	DeviceName string `json:"deviceName"`
	Nonce      string `json:"nonce"`
	Proof      string `json:"proof"`
}

type pairResp struct {
	DeviceID     string `json:"deviceId"`
	Token        string `json:"token"`
	ServerProof  string `json:"serverProof"`
	ComputerName string `json:"computerName"`
}

func (s *Server) handlePair(w http.ResponseWriter, r *http.Request) {
	ip := clientIP(r)
	if r.Method != http.MethodPost {
		writeJSON(w, http.StatusMethodNotAllowed, errBody{"method_not_allowed"})
		return
	}
	if s.limiter.blocked(ip) {
		writeJSON(w, http.StatusTooManyRequests, errBody{"too_many_attempts"})
		return
	}
	var req pairReq
	r.Body = http.MaxBytesReader(w, r.Body, maxBody)
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil || !validNonce(req.Nonce) || len(req.Proof) != 64 {
		s.limiter.fail(ip)
		writeJSON(w, http.StatusBadRequest, errBody{"bad_request"})
		return
	}
	serverProof, ok := s.pair.Complete(s.fingerprint, req.Nonce, req.Proof)
	if !ok {
		s.limiter.fail(ip)
		s.log.Printf("pairing attempt rejected")
		writeJSON(w, http.StatusForbidden, errBody{"pairing_failed"}) // same answer for every cause
		return
	}
	id, token, err := s.store.Add(req.DeviceName)
	if err != nil {
		s.log.Printf("pairing succeeded but device could not be stored: %v", err)
		writeJSON(w, http.StatusInternalServerError, errBody{"cannot_store_device"})
		return
	}
	s.log.Printf("device paired: %s", id)
	writeJSON(w, http.StatusOK, pairResp{DeviceID: id, Token: token, ServerProof: serverProof, ComputerName: s.opts.ComputerName})
}

func validNonce(n string) bool {
	b, err := base64.StdEncoding.DecodeString(n)
	return err == nil && len(b) == 16
}

// authed requires the right method and a valid device id + bearer token.
func (s *Server) authed(method string, h func(w http.ResponseWriter, r *http.Request, d devices.Public)) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		ip := clientIP(r)
		if r.Method != method {
			writeJSON(w, http.StatusMethodNotAllowed, errBody{"method_not_allowed"})
			return
		}
		if s.limiter.blocked(ip) {
			writeJSON(w, http.StatusTooManyRequests, errBody{"too_many_attempts"})
			return
		}
		id := r.Header.Get(headerDevice)
		auth := r.Header.Get("Authorization")
		token := strings.TrimPrefix(auth, "Bearer ")
		d, ok := s.store.Authenticate(id, token)
		if !strings.HasPrefix(auth, "Bearer ") || !ok {
			s.limiter.fail(ip)
			writeJSON(w, http.StatusUnauthorized, errBody{"unauthorized"})
			return
		}
		h(w, r, d)
	}
}

func (s *Server) handleStatus(w http.ResponseWriter, r *http.Request, d devices.Public) {
	writeJSON(w, http.StatusOK, map[string]any{
		"apiVersion":   APIVersion,
		"computerName": s.opts.ComputerName,
		"version":      s.opts.Version,
		"serverTime":   time.Now().UTC().Format(time.RFC3339),
		"deviceName":   d.Name,
	})
}

func (s *Server) handleDisks(w http.ResponseWriter, r *http.Request, _ devices.Public) {
	list, err := s.provider.List()
	if err != nil {
		s.log.Printf("disk listing failed: %v", err)
		writeJSON(w, http.StatusInternalServerError, errBody{"disk_listing_failed"})
		return
	}
	writeJSON(w, http.StatusOK, list)
}

func (s *Server) handleUnpair(w http.ResponseWriter, r *http.Request, d devices.Public) {
	s.store.Remove(d.ID)
	s.log.Printf("device unpaired by itself: %s", d.ID)
	_, _ = io.Copy(io.Discard, io.LimitReader(r.Body, maxBody))
	writeJSON(w, http.StatusOK, map[string]string{"status": "unpaired"})
}

// ---- network filtering -------------------------------------------------------------

var cgnat = &net.IPNet{IP: net.IPv4(100, 64, 0, 0), Mask: net.CIDRMask(10, 32)}

// AllowedIP reports whether a peer address is on a private network (home/office LAN,
// link-local, loopback, or a 100.64/10 VPN range). Public addresses are refused.
func AllowedIP(ip net.IP) bool {
	if ip == nil {
		return false
	}
	if v4 := ip.To4(); v4 != nil {
		ip = v4
	}
	return ip.IsLoopback() || ip.IsPrivate() || ip.IsLinkLocalUnicast() || cgnat.Contains(ip)
}

type filterListener struct {
	net.Listener
	log *log.Logger
}

func (l *filterListener) Accept() (net.Conn, error) {
	for {
		c, err := l.Listener.Accept()
		if err != nil {
			return nil, err
		}
		if ta, ok := c.RemoteAddr().(*net.TCPAddr); ok && AllowedIP(ta.IP) {
			return c, nil
		}
		l.log.Printf("refused connection from a non-private address")
		c.Close()
	}
}

// LocalAddresses lists this computer's private IPv4 addresses for display.
func LocalAddresses() []string {
	var out []string
	ifs, err := net.Interfaces()
	if err != nil {
		return out
	}
	for _, ifc := range ifs {
		if ifc.Flags&net.FlagUp == 0 || ifc.Flags&net.FlagLoopback != 0 {
			continue
		}
		addrs, _ := ifc.Addrs()
		for _, a := range addrs {
			ipn, ok := a.(*net.IPNet)
			if !ok {
				continue
			}
			v4 := ipn.IP.To4()
			if v4 == nil || v4.IsLinkLocalUnicast() || !AllowedIP(v4) {
				continue
			}
			out = append(out, v4.String())
		}
	}
	return out
}

// ---- failure rate limiter -----------------------------------------------------------

type entry struct {
	count int
	start time.Time
}

type limiter struct {
	mu     sync.Mutex
	m      map[string]*entry
	max    int
	window time.Duration
	now    func() time.Time
}

func newLimiter(max int, window time.Duration) *limiter {
	return &limiter{m: map[string]*entry{}, max: max, window: window, now: time.Now}
}

func (l *limiter) blocked(ip string) bool {
	l.mu.Lock()
	defer l.mu.Unlock()
	e := l.m[ip]
	return e != nil && l.now().Sub(e.start) < l.window && e.count >= l.max
}

func (l *limiter) fail(ip string) {
	l.mu.Lock()
	defer l.mu.Unlock()
	now := l.now()
	if len(l.m) > 1000 {
		for k, v := range l.m {
			if now.Sub(v.start) >= l.window {
				delete(l.m, k)
			}
		}
	}
	e := l.m[ip]
	if e == nil || now.Sub(e.start) >= l.window {
		l.m[ip] = &entry{count: 1, start: now}
		return
	}
	e.count++
}
