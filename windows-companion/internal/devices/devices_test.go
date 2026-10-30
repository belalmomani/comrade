package devices

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"
)

func TestAddAuthenticateRemove(t *testing.T) {
	s, err := Open(t.TempDir())
	if err != nil {
		t.Fatal(err)
	}
	id, tok, err := s.Add("Pixel 7")
	if err != nil || len(id) != 16 || len(tok) != 64 {
		t.Fatalf("add: %q %q %v", id, tok, err)
	}
	if d, ok := s.Authenticate(id, tok); !ok || d.Name != "Pixel 7" {
		t.Fatal("valid token rejected")
	}
	if _, ok := s.Authenticate(id, "wrong"); ok {
		t.Fatal("wrong token accepted")
	}
	if _, ok := s.Authenticate("nope", tok); ok {
		t.Fatal("unknown id accepted")
	}
	if !s.Remove(id) {
		t.Fatal("remove failed")
	}
	if _, ok := s.Authenticate(id, tok); ok {
		t.Fatal("removed device still authenticates")
	}
	if s.Remove(id) {
		t.Fatal("second remove must report false")
	}
}

func TestPersistsAndStoresOnlyHash(t *testing.T) {
	dir := t.TempDir()
	s, _ := Open(dir)
	id, tok, _ := s.Add("Phone")
	raw, err := os.ReadFile(filepath.Join(dir, "devices.json"))
	if err != nil {
		t.Fatal(err)
	}
	if strings.Contains(string(raw), tok) {
		t.Fatal("plaintext token must never be written to disk")
	}
	s2, err := Open(dir)
	if err != nil {
		t.Fatal(err)
	}
	if _, ok := s2.Authenticate(id, tok); !ok {
		t.Fatal("device lost after reopen")
	}
}

func TestLimitAndRemoveAll(t *testing.T) {
	s, _ := Open(t.TempDir())
	for i := 0; i < MaxDevices; i++ {
		if _, _, err := s.Add("d"); err != nil {
			t.Fatal(err)
		}
	}
	if _, _, err := s.Add("extra"); err != ErrTooMany {
		t.Fatalf("want ErrTooMany, got %v", err)
	}
	if n := s.RemoveAll(); n != MaxDevices || len(s.List()) != 0 {
		t.Fatalf("remove all: %d", n)
	}
}

func TestSanitizeName(t *testing.T) {
	if SanitizeName("  \x00\x07  ") != "جهاز" {
		t.Fatal("empty fallback")
	}
	if got := SanitizeName("a\nb\tc"); got != "abc" {
		t.Fatalf("control chars: %q", got)
	}
	if n := len([]rune(SanitizeName(strings.Repeat("x", 200)))); n != 40 {
		t.Fatalf("length: %d", n)
	}
}

func TestLastSeenUpdates(t *testing.T) {
	s, _ := Open(t.TempDir())
	base := time.Now()
	s.now = func() time.Time { return base }
	id, tok, _ := s.Add("p")
	s.now = func() time.Time { return base.Add(2 * time.Minute) }
	d, ok := s.Authenticate(id, tok)
	if !ok || !d.LastSeen.After(base) {
		t.Fatal("lastSeen not updated")
	}
}
