package control

import (
	"context"
	"errors"
	"io"
	"log"
	"net/http"
	"os"
	"path/filepath"
	"testing"
	"time"

	"diskati/companion/internal/devices"
	"diskati/companion/internal/disks"
)

type fakeProvider struct {
	d   []disks.Disk
	err error
}

func (f fakeProvider) List() ([]disks.Disk, error) { return f.d, f.err }

func newServer(t *testing.T, p disks.Provider) (*Server, string) {
	t.Helper()
	dir := t.TempDir()
	s := New(dir, "test", p, nil, log.New(io.Discard, "", 0))
	if err := s.Start(); err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { _ = s.Shutdown(context.Background()) })
	return s, dir
}

func TestStatusAndDisks(t *testing.T) {
	want := []disks.Disk{disks.Compute("C:", 512, 77)}
	_, dir := newServer(t, fakeProvider{d: want})

	st, err := GetStatus(dir)
	if err != nil || st.Version != "test" || st.PID != os.Getpid() {
		t.Fatalf("status: %+v err=%v", st, err)
	}
	got, err := GetDisks(dir)
	if err != nil || len(got) != 1 || got[0].UsedBytes != 435 {
		t.Fatalf("disks: %+v err=%v", got, err)
	}
}

func TestRejectsMissingOrWrongToken(t *testing.T) {
	_, dir := newServer(t, fakeProvider{})
	info, _ := ReadInfo(dir)
	for _, tok := range []string{"", "wrong"} {
		req, _ := http.NewRequest(http.MethodGet, "http://127.0.0.1:"+itoa(info.Port)+"/status", nil)
		if tok != "" {
			req.Header.Set(tokenHeader, tok)
		}
		resp, err := http.DefaultClient.Do(req)
		if err != nil {
			t.Fatal(err)
		}
		resp.Body.Close()
		if resp.StatusCode != http.StatusUnauthorized {
			t.Fatalf("token %q: want 401 got %d", tok, resp.StatusCode)
		}
	}
}

func TestStopSignalsAndShutdownRemovesInfo(t *testing.T) {
	s, dir := newServer(t, fakeProvider{})
	if err := RequestStop(dir); err != nil {
		t.Fatal(err)
	}
	select {
	case <-s.Stopped():
	case <-time.After(2 * time.Second):
		t.Fatal("stop was not signalled")
	}
	if err := s.Shutdown(context.Background()); err != nil {
		t.Fatal(err)
	}
	if _, err := os.Stat(filepath.Join(dir, infoFile)); !os.IsNotExist(err) {
		t.Fatal("info file should be removed")
	}
	if _, err := GetStatus(dir); !errors.Is(err, ErrNotRunning) {
		t.Fatalf("want ErrNotRunning, got %v", err)
	}
}

func TestNotRunningWhenNoInfo(t *testing.T) {
	if _, err := GetStatus(t.TempDir()); !errors.Is(err, ErrNotRunning) {
		t.Fatalf("want ErrNotRunning, got %v", err)
	}
}

func TestStaleInfoMeansNotRunning(t *testing.T) {
	dir := t.TempDir()
	if err := writeInfo(dir, Info{Port: 1, Token: "x", PID: 999999}); err != nil {
		t.Fatal(err)
	}
	if _, err := GetStatus(dir); !errors.Is(err, ErrNotRunning) {
		t.Fatalf("want ErrNotRunning, got %v", err)
	}
}

func TestDiskErrorReturns500(t *testing.T) {
	_, dir := newServer(t, fakeProvider{err: errors.New("boom")})
	if _, err := GetDisks(dir); err == nil {
		t.Fatal("expected error")
	}
}

func TestTokensAreUnique(t *testing.T) {
	a, _ := NewToken()
	b, _ := NewToken()
	if a == b || len(a) != 64 {
		t.Fatalf("bad tokens: %q %q", a, b)
	}
}

func TestStopRejectsGET(t *testing.T) {
	_, dir := newServer(t, fakeProvider{})
	info, _ := ReadInfo(dir)
	req, _ := http.NewRequest(http.MethodGet, "http://127.0.0.1:"+itoa(info.Port)+"/stop", nil)
	req.Header.Set(tokenHeader, info.Token)
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		t.Fatal(err)
	}
	resp.Body.Close()
	if resp.StatusCode != http.StatusMethodNotAllowed {
		t.Fatalf("want 405 got %d", resp.StatusCode)
	}
}

func itoa(n int) string {
	if n == 0 {
		return "0"
	}
	var b []byte
	for n > 0 {
		b = append([]byte{byte('0' + n%10)}, b...)
		n /= 10
	}
	return string(b)
}

type fakeAdmin struct {
	cancelled bool
	removed   []string
}

func (f *fakeAdmin) StartPairing() (PairingInfo, error) {
	return PairingInfo{Code: "AAAA-BBBB-CCCC", Port: 47831, Addresses: []string{"192.168.1.5"}}, nil
}
func (f *fakeAdmin) CancelPairing()            { f.cancelled = true }
func (f *fakeAdmin) Devices() []devices.Public { return []devices.Public{{ID: "d1", Name: "Pixel"}} }
func (f *fakeAdmin) RemoveDevice(id string) bool {
	f.removed = append(f.removed, id)
	return id == "d1"
}
func (f *fakeAdmin) RemoveAllDevices() int { return 3 }

func newAdminServer(t *testing.T) (*fakeAdmin, string) {
	t.Helper()
	dir := t.TempDir()
	a := &fakeAdmin{}
	s := New(dir, "test", fakeProvider{}, a, log.New(io.Discard, "", 0))
	if err := s.Start(); err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { _ = s.Shutdown(context.Background()) })
	return a, dir
}

func TestAdminPairingAndDevices(t *testing.T) {
	a, dir := newAdminServer(t)
	p, err := StartPairing(dir)
	if err != nil || p.Code != "AAAA-BBBB-CCCC" || p.Port != 47831 {
		t.Fatalf("start pairing: %+v %v", p, err)
	}
	if err := CancelPairing(dir); err != nil || !a.cancelled {
		t.Fatalf("cancel: %v", err)
	}
	d, err := ListDevices(dir)
	if err != nil || len(d) != 1 || d[0].ID != "d1" {
		t.Fatalf("devices: %+v %v", d, err)
	}
	if err := RemoveDevice(dir, "d1"); err != nil {
		t.Fatalf("remove: %v", err)
	}
	if err := RemoveDevice(dir, "zzz"); err == nil {
		t.Fatal("removing an unknown device must fail")
	}
	if err := RemoveDevice(dir, "all"); err != nil {
		t.Fatalf("remove all: %v", err)
	}
}

func TestAdminRoutesUnavailableWithoutAdmin(t *testing.T) {
	_, dir := newServer(t, fakeProvider{})
	if _, err := StartPairing(dir); err == nil {
		t.Fatal("expected 501 without an admin implementation")
	}
}

func TestAdminRoutesNeedToken(t *testing.T) {
	_, dir := newAdminServer(t)
	info, _ := ReadInfo(dir)
	resp, err := http.Post("http://127.0.0.1:"+itoa(info.Port)+"/pair/start", "", nil)
	if err != nil {
		t.Fatal(err)
	}
	resp.Body.Close()
	if resp.StatusCode != http.StatusUnauthorized {
		t.Fatalf("want 401 got %d", resp.StatusCode)
	}
}
