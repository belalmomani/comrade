// Package pairing implements the one-time pairing code and its proofs.
//
// The code itself is never sent over the network. The phone proves it knows the code by
// sending HMAC-SHA256(code, "diskati-pair-v1|client|<certFingerprint>|<nonce>") where the
// fingerprint is the one the phone saw in the TLS handshake. A man in the middle presents a
// different certificate, so its fingerprint differs and the proof is rejected.
// The server answers with a second proof (role "server") so the phone can verify the same.
package pairing

import (
	"crypto/hmac"
	"crypto/rand"
	"crypto/sha256"
	"encoding/hex"
	"strings"
	"sync"
	"time"
)

const (
	alphabet    = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ" // 32 symbols, no 0/1/I/O
	CodeLength  = 12                                 // 60 bits of entropy
	MaxFailures = 3
	proofPrefix = "diskati-pair-v1"
)

// NormalizeCode uppercases and strips separators.
func NormalizeCode(s string) string {
	var b strings.Builder
	for _, r := range strings.ToUpper(s) {
		if r == '-' || r == ' ' {
			continue
		}
		b.WriteRune(r)
	}
	return b.String()
}

// FormatCode renders XXXX-XXXX-XXXX.
func FormatCode(c string) string {
	if len(c) != CodeLength {
		return c
	}
	return c[0:4] + "-" + c[4:8] + "-" + c[8:12]
}

// Proof computes the HMAC proof for a role ("client" or "server").
func Proof(code, role, fingerprint, nonce string) string {
	m := hmac.New(sha256.New, []byte(code))
	m.Write([]byte(proofPrefix + "|" + role + "|" + fingerprint + "|" + nonce))
	return hex.EncodeToString(m.Sum(nil))
}

// Manager holds at most one active pairing code.
type Manager struct {
	mu       sync.Mutex
	code     string
	expires  time.Time
	failures int
	now      func() time.Time
}

// NewManager creates a manager using the real clock.
func NewManager() *Manager { return &Manager{now: time.Now} }

func newCode() (string, error) {
	b := make([]byte, CodeLength)
	if _, err := rand.Read(b); err != nil {
		return "", err
	}
	out := make([]byte, CodeLength)
	for i, v := range b {
		out[i] = alphabet[int(v)&31] // 256 is a multiple of 32: no modulo bias
	}
	return string(out), nil
}

// Start creates a fresh code, invalidating any previous one.
func (m *Manager) Start(ttl time.Duration) (string, time.Time, error) {
	c, err := newCode()
	if err != nil {
		return "", time.Time{}, err
	}
	m.mu.Lock()
	defer m.mu.Unlock()
	m.code = c
	m.failures = 0
	m.expires = m.now().Add(ttl)
	return c, m.expires, nil
}

// Cancel invalidates the active code.
func (m *Manager) Cancel() {
	m.mu.Lock()
	m.code = ""
	m.mu.Unlock()
}

// Active reports whether a code can currently be used.
func (m *Manager) Active() bool {
	m.mu.Lock()
	defer m.mu.Unlock()
	return m.code != "" && m.now().Before(m.expires)
}

// Complete verifies the client's proof. On success the code is consumed and the server
// proof is returned. Every wrong proof counts; MaxFailures wrong proofs invalidate the code.
func (m *Manager) Complete(fingerprint, nonce, clientProof string) (string, bool) {
	m.mu.Lock()
	defer m.mu.Unlock()
	if m.code == "" || !m.now().Before(m.expires) {
		m.code = ""
		return "", false
	}
	want := Proof(m.code, "client", fingerprint, nonce)
	if !hmac.Equal([]byte(want), []byte(strings.ToLower(clientProof))) {
		m.failures++
		if m.failures >= MaxFailures {
			m.code = ""
		}
		return "", false
	}
	serverProof := Proof(m.code, "server", fingerprint, nonce)
	m.code = ""
	return serverProof, true
}
