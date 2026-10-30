package remote

import (
	"bytes"
	"context"
	"crypto/sha256"
	"crypto/tls"
	"crypto/x509"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net"
	"net/http"
	"strings"
	"testing"
	"time"

	"diskati/companion/internal/devices"
	"diskati/companion/internal/disks"
	"diskati/companion/internal/pairing"
	"diskati/companion/internal/pki"
)

type fakeDisks struct{}

func (fakeDisks) List() ([]disks.Disk, error) {
	return []disks.Disk{disks.Compute("C:", 512, 77)}, nil
}

type env struct {
	srv   *Server
	pm    *pairing.Manager
	store *devices.Store
	fp    string
}

func start(t *testing.T) *env {
	t.Helper()
	dir := t.TempDir()
	cert, fp, err := pki.LoadOrCreate(dir)
	if err != nil {
		t.Fatal(err)
	}
	st, err := devices.Open(dir)
	if err != nil {
		t.Fatal(err)
	}
	pm := pairing.NewManager()
	s := New(Options{Port: 0, ComputerName: "TEST-PC", Version: "t"}, cert, fp, fakeDisks{}, st, pm, log.New(io.Discard, "", 0))
	if err := s.Start(); err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() {
		ctx, c := context.WithTimeout(context.Background(), time.Second)
		defer c()
		_ = s.Shutdown(ctx)
	})
	return &env{s, pm, st, fp}
}

// phoneTLS behaves like the Android client: it does not verify the certificate by itself,
// it records the fingerprint it saw (what the real app will bind into its proof).
func phoneClient(pin string, seen *string) *http.Client {
	return &http.Client{
		Timeout: 3 * time.Second,
		Transport: &http.Transport{TLSClientConfig: &tls.Config{
			InsecureSkipVerify: true,
			MinVersion:         tls.VersionTLS12,
			VerifyPeerCertificate: func(raw [][]byte, _ [][]*x509.Certificate) error {
				sum := sha256.Sum256(raw[0])
				fp := hex.EncodeToString(sum[:])
				if seen != nil {
					*seen = fp
				}
				if pin != "" && fp != pin {
					return fmt.Errorf("fingerprint mismatch")
				}
				return nil
			},
		}},
	}
}

func url(e *env, path string) string {
	return fmt.Sprintf("https://127.0.0.1:%d%s", e.srv.Port(), path)
}

func doPair(t *testing.T, e *env, code, name string) (int, pairResp) {
	t.Helper()
	var seen string
	c := phoneClient("", &seen)
	nonceRaw := make([]byte, 16)
	nonce := base64.StdEncoding.EncodeToString(nonceRaw)
	// first request only to learn the fingerprint, exactly like capturing during the handshake
	resp, err := c.Get(url(e, "/v1/status"))
	if err != nil {
		t.Fatal(err)
	}
	resp.Body.Close()
	body, _ := json.Marshal(pairReq{DeviceName: name, Nonce: nonce, Proof: pairing.Proof(pairing.NormalizeCode(code), "client", seen, nonce)})
	resp, err = c.Post(url(e, "/v1/pair"), "application/json", bytes.NewReader(body))
	if err != nil {
		t.Fatal(err)
	}
	defer resp.Body.Close()
	var pr pairResp
	_ = json.NewDecoder(resp.Body).Decode(&pr)
	if resp.StatusCode == 200 {
		want := pairing.Proof(pairing.NormalizeCode(code), "server", seen, nonce)
		if pr.ServerProof != want {
			t.Fatal("server proof does not verify: the phone could not authenticate the computer")
		}
	}
	return resp.StatusCode, pr
}

func authed(e *env, method, path, id, token string) (*http.Response, error) {
	c := phoneClient(e.fp, nil)
	req, _ := http.NewRequest(method, url(e, path), nil)
	if id != "" {
		req.Header.Set(headerDevice, id)
	}
	if token != "" {
		req.Header.Set("Authorization", "Bearer "+token)
	}
	return c.Do(req)
}

func TestFullPairingAndUse(t *testing.T) {
	e := start(t)
	code, _, _ := e.pm.Start(time.Minute)
	status, pr := doPair(t, e, pairing.FormatCode(code), "Pixel 7")
	if status != 200 || pr.DeviceID == "" || len(pr.Token) != 64 || pr.ComputerName != "TEST-PC" {
		t.Fatalf("pairing failed: %d %+v", status, pr)
	}

	resp, err := authed(e, http.MethodGet, "/v1/status", pr.DeviceID, pr.Token)
	if err != nil || resp.StatusCode != 200 {
		t.Fatalf("status: %v %v", resp, err)
	}
	resp.Body.Close()

	resp, err = authed(e, http.MethodGet, "/v1/disks", pr.DeviceID, pr.Token)
	if err != nil || resp.StatusCode != 200 {
		t.Fatalf("disks: %v %v", resp, err)
	}
	var d []disks.Disk
	_ = json.NewDecoder(resp.Body).Decode(&d)
	resp.Body.Close()
	if len(d) != 1 || d[0].UsedBytes != 435 {
		t.Fatalf("disks payload: %+v", d)
	}
}

func TestCodeIsSingleUse(t *testing.T) {
	e := start(t)
	code, _, _ := e.pm.Start(time.Minute)
	if s, _ := doPair(t, e, code, "a"); s != 200 {
		t.Fatalf("first pairing: %d", s)
	}
	if s, _ := doPair(t, e, code, "b"); s != 403 {
		t.Fatalf("reused code must be refused, got %d", s)
	}
	if n := len(e.store.List()); n != 1 {
		t.Fatalf("exactly one device expected, got %d", n)
	}
}

func TestWrongCodeAndNoActiveCode(t *testing.T) {
	e := start(t)
	if s, _ := doPair(t, e, "AAAA-AAAA-AAAA", "x"); s != 403 {
		t.Fatalf("no active code: want 403 got %d", s)
	}
	e.pm.Start(time.Minute)
	if s, _ := doPair(t, e, "AAAA-AAAA-AAAA", "x"); s != 403 {
		t.Fatalf("wrong code: want 403 got %d", s)
	}
	if len(e.store.List()) != 0 {
		t.Fatal("no device may be created")
	}
}

func TestCodeDiesAfterThreeWrongAttempts(t *testing.T) {
	e := start(t)
	code, _, _ := e.pm.Start(time.Minute)
	for i := 0; i < pairing.MaxFailures; i++ {
		doPair(t, e, "AAAA-AAAA-AAAA", "x")
	}
	if s, _ := doPair(t, e, code, "real"); s != 403 {
		t.Fatalf("right code after burn-out must fail, got %d", s)
	}
}

func TestUnauthenticatedRequestsRejected(t *testing.T) {
	e := start(t)
	for _, p := range []string{"/v1/status", "/v1/disks"} {
		resp, err := authed(e, http.MethodGet, p, "", "")
		if err != nil || resp.StatusCode != 401 {
			t.Fatalf("%s without credentials: %v %v", p, resp, err)
		}
		resp.Body.Close()
	}
	resp, _ := authed(e, http.MethodGet, "/v1/status", "deadbeef", "bad")
	if resp.StatusCode != 401 {
		t.Fatalf("bad credentials: %d", resp.StatusCode)
	}
	resp.Body.Close()
	resp, _ = authed(e, http.MethodPost, "/v1/unpair", "", "")
	if resp.StatusCode != 401 {
		t.Fatalf("unpair without credentials: %d", resp.StatusCode)
	}
	resp.Body.Close()
}

func TestUnknownCommandsDoNotExist(t *testing.T) {
	e := start(t)
	code, _, _ := e.pm.Start(time.Minute)
	_, pr := doPair(t, e, code, "p")
	for _, p := range []string{"/v1/exec", "/v1/delete", "/v1/files", "/", "/v2/status"} {
		resp, err := authed(e, http.MethodGet, p, pr.DeviceID, pr.Token)
		if err != nil || resp.StatusCode != 404 {
			t.Fatalf("%s must be 404, got %v %v", p, resp, err)
		}
		resp.Body.Close()
	}
}

func TestMethodsAreEnforced(t *testing.T) {
	e := start(t)
	code, _, _ := e.pm.Start(time.Minute)
	_, pr := doPair(t, e, code, "p")
	resp, _ := authed(e, http.MethodPost, "/v1/status", pr.DeviceID, pr.Token)
	if resp.StatusCode != 405 {
		t.Fatalf("POST /status: %d", resp.StatusCode)
	}
	resp.Body.Close()
	resp, _ = authed(e, http.MethodGet, "/v1/unpair", pr.DeviceID, pr.Token)
	if resp.StatusCode != 405 {
		t.Fatalf("GET /unpair: %d", resp.StatusCode)
	}
	resp.Body.Close()
}

func TestUnpairFromPhoneRevokesAccess(t *testing.T) {
	e := start(t)
	code, _, _ := e.pm.Start(time.Minute)
	_, pr := doPair(t, e, code, "p")
	resp, err := authed(e, http.MethodPost, "/v1/unpair", pr.DeviceID, pr.Token)
	if err != nil || resp.StatusCode != 200 {
		t.Fatalf("unpair: %v %v", resp, err)
	}
	resp.Body.Close()
	resp, _ = authed(e, http.MethodGet, "/v1/status", pr.DeviceID, pr.Token)
	if resp.StatusCode != 401 {
		t.Fatalf("token must stop working after unpair, got %d", resp.StatusCode)
	}
	resp.Body.Close()
}

func TestRemovalOnComputerRevokesAccess(t *testing.T) {
	e := start(t)
	code, _, _ := e.pm.Start(time.Minute)
	_, pr := doPair(t, e, code, "p")
	e.store.Remove(pr.DeviceID)
	resp, _ := authed(e, http.MethodGet, "/v1/disks", pr.DeviceID, pr.Token)
	if resp.StatusCode != 401 {
		t.Fatalf("got %d", resp.StatusCode)
	}
	resp.Body.Close()
}

func TestPinnedFingerprintMismatchIsRefused(t *testing.T) {
	e := start(t)
	c := phoneClient(strings.Repeat("00", 32), nil)
	if _, err := c.Get(url(e, "/v1/status")); err == nil {
		t.Fatal("a client pinning another fingerprint must refuse the connection")
	}
}

func TestPlainHTTPAndOldTLSRefused(t *testing.T) {
	e := start(t)
	c := &http.Client{Timeout: 2 * time.Second}
	if resp, err := c.Get(fmt.Sprintf("http://127.0.0.1:%d/v1/status", e.srv.Port())); err == nil && resp.StatusCode == 200 {
		t.Fatal("plain HTTP must not be served")
	}
	old := &http.Client{Timeout: 2 * time.Second, Transport: &http.Transport{TLSClientConfig: &tls.Config{
		InsecureSkipVerify: true, MinVersion: tls.VersionTLS10, MaxVersion: tls.VersionTLS11}}}
	if _, err := old.Get(url(e, "/v1/status")); err == nil {
		t.Fatal("TLS 1.1 must be refused")
	}
}

func TestRateLimitAfterRepeatedFailures(t *testing.T) {
	e := start(t)
	var last int
	for i := 0; i < 12; i++ {
		resp, err := authed(e, http.MethodGet, "/v1/status", "x", "y")
		if err != nil {
			t.Fatal(err)
		}
		last = resp.StatusCode
		resp.Body.Close()
	}
	if last != 429 {
		t.Fatalf("expected 429 after repeated failures, got %d", last)
	}
}

func TestOversizedPairBodyRejected(t *testing.T) {
	e := start(t)
	c := phoneClient("", nil)
	resp, err := c.Post(url(e, "/v1/pair"), "application/json", strings.NewReader(`{"deviceName":"`+strings.Repeat("a", 10000)+`"}`))
	if err != nil {
		t.Fatal(err)
	}
	defer resp.Body.Close()
	if resp.StatusCode != 400 {
		t.Fatalf("got %d", resp.StatusCode)
	}
}

func TestAllowedIP(t *testing.T) {
	yes := []string{"127.0.0.1", "10.1.2.3", "172.16.5.5", "192.168.1.20", "169.254.3.4", "100.64.1.1", "100.127.255.255", "fd00::1", "::1", "::ffff:192.168.0.9"}
	no := []string{"8.8.8.8", "1.1.1.1", "100.128.0.1", "172.32.0.1", "203.0.113.5", "2001:4860:4860::8888"}
	for _, s := range yes {
		if !AllowedIP(net.ParseIP(s)) {
			t.Errorf("%s should be allowed", s)
		}
	}
	for _, s := range no {
		if AllowedIP(net.ParseIP(s)) {
			t.Errorf("%s must be refused (public address)", s)
		}
	}
	if AllowedIP(nil) {
		t.Error("nil must be refused")
	}
}

func TestLimiterWindowResets(t *testing.T) {
	now := time.Now()
	l := newLimiter(2, time.Minute)
	l.now = func() time.Time { return now }
	l.fail("1.1.1.1")
	l.fail("1.1.1.1")
	if !l.blocked("1.1.1.1") {
		t.Fatal("should be blocked")
	}
	now = now.Add(61 * time.Second)
	if l.blocked("1.1.1.1") {
		t.Fatal("window must reset")
	}
}
