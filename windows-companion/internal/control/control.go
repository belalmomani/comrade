// Package control exposes a loopback-only, token-protected control endpoint so that
// `diskati status` and `diskati stop` can talk to the running background process.
// It is NOT the phone connection; the secure phone link is built in phase 4.
package control

import (
	"context"
	"crypto/rand"
	"crypto/subtle"
	"encoding/hex"
	"encoding/json"
	"errors"
	"log"
	"net"
	"net/http"
	"os"
	"path/filepath"
	"sync"
	"time"

	"diskati/companion/internal/devices"
	"diskati/companion/internal/disks"
)

const (
	tokenHeader = "X-Diskati-Token"
	infoFile    = "control.json"
)

// Info is written to the data dir so the CLI can find the running process.
type Info struct {
	Port  int    `json:"port"`
	Token string `json:"token"`
	PID   int    `json:"pid"`
}

// StatusResponse is returned by GET /status.
type StatusResponse struct {
	Version       string    `json:"version"`
	PID           int       `json:"pid"`
	StartedAt     time.Time `json:"startedAt"`
	UptimeSeconds int64     `json:"uptimeSeconds"`
}

// PairingInfo is returned when a pairing code is started.
type PairingInfo struct {
	Code        string    `json:"code"`
	ExpiresAt   time.Time `json:"expiresAt"`
	Port        int       `json:"port"`
	Fingerprint string    `json:"fingerprint"`
	Addresses   []string  `json:"addresses"`
}

// Admin is implemented by the service; it is what `diskati pair/devices/unpair` talk to.
type Admin interface {
	StartPairing() (PairingInfo, error)
	CancelPairing()
	Devices() []devices.Public
	RemoveDevice(id string) bool
	RemoveAllDevices() int
}

// Server is the control endpoint.
type Server struct {
	version  string
	dir      string
	provider disks.Provider
	admin    Admin
	log      *log.Logger

	started time.Time
	token   string
	srv     *http.Server
	stop    chan struct{}
	once    sync.Once
}

// New creates a server. Call Start to begin listening.
func New(dir, version string, p disks.Provider, admin Admin, lg *log.Logger) *Server {
	return &Server{version: version, dir: dir, provider: p, admin: admin, log: lg, stop: make(chan struct{})}
}

// NewToken returns 256 bits of randomness, hex encoded.
func NewToken() (string, error) {
	b := make([]byte, 32)
	if _, err := rand.Read(b); err != nil {
		return "", err
	}
	return hex.EncodeToString(b), nil
}

// Start listens on 127.0.0.1 only, on a random port.
func (s *Server) Start() error {
	tok, err := NewToken()
	if err != nil {
		return err
	}
	s.token = tok
	s.started = time.Now().UTC()

	ln, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		return err
	}
	mux := http.NewServeMux()
	mux.HandleFunc("/status", s.auth(s.handleStatus))
	mux.HandleFunc("/disks", s.auth(s.handleDisks))
	mux.HandleFunc("/stop", s.auth(s.handleStop))
	mux.HandleFunc("/pair/start", s.auth(s.handlePairStart))
	mux.HandleFunc("/pair/cancel", s.auth(s.handlePairCancel))
	mux.HandleFunc("/devices", s.auth(s.handleDevices))
	mux.HandleFunc("/devices/remove", s.auth(s.handleDeviceRemove))
	s.srv = &http.Server{Handler: mux, ReadHeaderTimeout: 5 * time.Second}

	info := Info{Port: ln.Addr().(*net.TCPAddr).Port, Token: tok, PID: os.Getpid()}
	if err := writeInfo(s.dir, info); err != nil {
		ln.Close()
		return err
	}
	go func() {
		if err := s.srv.Serve(ln); err != nil && !errors.Is(err, http.ErrServerClosed) {
			s.log.Printf("control server error: %v", err)
		}
	}()
	s.log.Printf("control endpoint started on loopback port %d", info.Port)
	return nil
}

// Stopped is closed when a stop was requested through the endpoint.
func (s *Server) Stopped() <-chan struct{} { return s.stop }

// Shutdown stops listening and removes the info file.
func (s *Server) Shutdown(ctx context.Context) error {
	removeInfo(s.dir, os.Getpid())
	if s.srv == nil {
		return nil
	}
	return s.srv.Shutdown(ctx)
}

func (s *Server) auth(h http.HandlerFunc) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		got := r.Header.Get(tokenHeader)
		if subtle.ConstantTimeCompare([]byte(got), []byte(s.token)) != 1 {
			http.Error(w, "unauthorized", http.StatusUnauthorized)
			return
		}
		h(w, r)
	}
}

func (s *Server) handleStatus(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
		return
	}
	writeJSON(w, StatusResponse{
		Version:       s.version,
		PID:           os.Getpid(),
		StartedAt:     s.started,
		UptimeSeconds: int64(time.Since(s.started).Seconds()),
	})
}

func (s *Server) handleDisks(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
		return
	}
	d, err := s.provider.List()
	if err != nil {
		s.log.Printf("disk listing failed: %v", err)
		http.Error(w, "disk listing failed", http.StatusInternalServerError)
		return
	}
	writeJSON(w, d)
}

func (s *Server) handleStop(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
		return
	}
	writeJSON(w, map[string]string{"status": "stopping"})
	s.once.Do(func() { close(s.stop) })
}

func (s *Server) needAdmin(w http.ResponseWriter, r *http.Request, method string) bool {
	if r.Method != method {
		http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
		return false
	}
	if s.admin == nil {
		http.Error(w, "not available", http.StatusNotImplemented)
		return false
	}
	return true
}

func (s *Server) handlePairStart(w http.ResponseWriter, r *http.Request) {
	if !s.needAdmin(w, r, http.MethodPost) {
		return
	}
	info, err := s.admin.StartPairing()
	if err != nil {
		s.log.Printf("could not start pairing: %v", err)
		http.Error(w, "could not start pairing", http.StatusInternalServerError)
		return
	}
	s.log.Printf("pairing code issued")
	writeJSON(w, info)
}

func (s *Server) handlePairCancel(w http.ResponseWriter, r *http.Request) {
	if !s.needAdmin(w, r, http.MethodPost) {
		return
	}
	s.admin.CancelPairing()
	writeJSON(w, map[string]string{"status": "cancelled"})
}

func (s *Server) handleDevices(w http.ResponseWriter, r *http.Request) {
	if !s.needAdmin(w, r, http.MethodGet) {
		return
	}
	writeJSON(w, s.admin.Devices())
}

func (s *Server) handleDeviceRemove(w http.ResponseWriter, r *http.Request) {
	if !s.needAdmin(w, r, http.MethodPost) {
		return
	}
	id := r.URL.Query().Get("id")
	if id == "all" {
		writeJSON(w, map[string]int{"removed": s.admin.RemoveAllDevices()})
		return
	}
	if !s.admin.RemoveDevice(id) {
		http.Error(w, "device not found", http.StatusNotFound)
		return
	}
	writeJSON(w, map[string]int{"removed": 1})
}

func writeJSON(w http.ResponseWriter, v any) {
	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(v)
}

func writeInfo(dir string, info Info) error {
	if err := os.MkdirAll(dir, 0o700); err != nil {
		return err
	}
	data, err := json.Marshal(info)
	if err != nil {
		return err
	}
	tmp := filepath.Join(dir, infoFile+".tmp")
	if err := os.WriteFile(tmp, data, 0o600); err != nil {
		return err
	}
	return os.Rename(tmp, filepath.Join(dir, infoFile))
}

func removeInfo(dir string, pid int) {
	p := filepath.Join(dir, infoFile)
	data, err := os.ReadFile(p)
	if err != nil {
		return
	}
	var info Info
	if json.Unmarshal(data, &info) == nil && info.PID == pid {
		_ = os.Remove(p)
	}
}
