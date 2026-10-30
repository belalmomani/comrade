// Package devices stores paired phones. Only a SHA-256 hash of each device token is stored,
// so a copy of devices.json cannot be used to impersonate a phone.
package devices

import (
	"crypto/rand"
	"crypto/sha256"
	"crypto/subtle"
	"encoding/hex"
	"encoding/json"
	"errors"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"time"
	"unicode"
)

// MaxDevices limits how many phones can be paired at once.
const MaxDevices = 10

// ErrTooMany is returned by Add when the limit is reached.
var ErrTooMany = errors.New("too many paired devices")

// Device is the stored record.
type Device struct {
	ID        string    `json:"id"`
	Name      string    `json:"name"`
	TokenHash string    `json:"tokenHash"`
	CreatedAt time.Time `json:"createdAt"`
	LastSeen  time.Time `json:"lastSeen"`
}

// Public is the view without secrets.
type Public struct {
	ID        string    `json:"id"`
	Name      string    `json:"name"`
	CreatedAt time.Time `json:"createdAt"`
	LastSeen  time.Time `json:"lastSeen"`
}

type fileData struct {
	Devices []Device `json:"devices"`
}

// Store is safe for concurrent use.
type Store struct {
	mu      sync.Mutex
	path    string
	devices []Device
	now     func() time.Time
}

// Open loads devices.json from dir (empty store if absent).
func Open(dir string) (*Store, error) {
	if err := os.MkdirAll(dir, 0o700); err != nil {
		return nil, err
	}
	s := &Store{path: filepath.Join(dir, "devices.json"), now: time.Now}
	data, err := os.ReadFile(s.path)
	if errors.Is(err, os.ErrNotExist) {
		return s, nil
	}
	if err != nil {
		return nil, err
	}
	var fd fileData
	if err := json.Unmarshal(data, &fd); err != nil {
		return nil, err
	}
	s.devices = fd.Devices
	return s, nil
}

func hashToken(t string) string {
	h := sha256.Sum256([]byte(t))
	return hex.EncodeToString(h[:])
}

func randomHex(n int) (string, error) {
	b := make([]byte, n)
	if _, err := rand.Read(b); err != nil {
		return "", err
	}
	return hex.EncodeToString(b), nil
}

// SanitizeName removes control characters and limits the length.
func SanitizeName(s string) string {
	s = strings.Map(func(r rune) rune {
		if unicode.IsControl(r) {
			return -1
		}
		return r
	}, s)
	r := []rune(strings.TrimSpace(s))
	if len(r) > 40 {
		r = r[:40]
	}
	out := strings.TrimSpace(string(r))
	if out == "" {
		return "جهاز"
	}
	return out
}

// Add registers a device and returns its id and the plaintext token (shown once).
func (s *Store) Add(name string) (string, string, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if len(s.devices) >= MaxDevices {
		return "", "", ErrTooMany
	}
	id, err := randomHex(8)
	if err != nil {
		return "", "", err
	}
	token, err := randomHex(32)
	if err != nil {
		return "", "", err
	}
	now := s.now().UTC()
	s.devices = append(s.devices, Device{ID: id, Name: SanitizeName(name), TokenHash: hashToken(token), CreatedAt: now, LastSeen: now})
	if err := s.saveLocked(); err != nil {
		s.devices = s.devices[:len(s.devices)-1]
		return "", "", err
	}
	return id, token, nil
}

// Authenticate checks a device id and token in constant time.
func (s *Store) Authenticate(id, token string) (Public, bool) {
	s.mu.Lock()
	defer s.mu.Unlock()
	h := hashToken(token)
	idx := -1
	for i := range s.devices {
		if s.devices[i].ID == id {
			idx = i
			break
		}
	}
	want := hashToken("no-such-device") // keeps timing similar when the id is unknown
	if idx >= 0 {
		want = s.devices[idx].TokenHash
	}
	if subtle.ConstantTimeCompare([]byte(h), []byte(want)) != 1 || idx < 0 {
		return Public{}, false
	}
	d := &s.devices[idx]
	if s.now().Sub(d.LastSeen) > time.Minute {
		d.LastSeen = s.now().UTC()
		_ = s.saveLocked() // best effort
	}
	return Public{ID: d.ID, Name: d.Name, CreatedAt: d.CreatedAt, LastSeen: d.LastSeen}, true
}

// Remove unpairs one device.
func (s *Store) Remove(id string) bool {
	s.mu.Lock()
	defer s.mu.Unlock()
	for i := range s.devices {
		if s.devices[i].ID == id {
			s.devices = append(s.devices[:i], s.devices[i+1:]...)
			_ = s.saveLocked()
			return true
		}
	}
	return false
}

// RemoveAll unpairs every device and returns how many were removed.
func (s *Store) RemoveAll() int {
	s.mu.Lock()
	defer s.mu.Unlock()
	n := len(s.devices)
	s.devices = nil
	_ = s.saveLocked()
	return n
}

// List returns devices without secrets.
func (s *Store) List() []Public {
	s.mu.Lock()
	defer s.mu.Unlock()
	out := make([]Public, 0, len(s.devices))
	for _, d := range s.devices {
		out = append(out, Public{ID: d.ID, Name: d.Name, CreatedAt: d.CreatedAt, LastSeen: d.LastSeen})
	}
	return out
}

func (s *Store) saveLocked() error {
	data, err := json.MarshalIndent(fileData{Devices: s.devices}, "", "  ")
	if err != nil {
		return err
	}
	tmp := s.path + ".tmp"
	if err := os.WriteFile(tmp, data, 0o600); err != nil {
		return err
	}
	return os.Rename(tmp, s.path)
}
