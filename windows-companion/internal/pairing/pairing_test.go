package pairing

import (
	"strings"
	"testing"
	"time"
)

const (
	fp    = "abababababababababababababababababababababababababababababababab"
	nonce = "AAAAAAAAAAAAAAAAAAAAAA=="
)

// Same vector is reproduced independently in Python and must match the Kotlin client.
func TestProofVectorMatchesOtherImplementations(t *testing.T) {
	if got := Proof("K7QM4TXP9WDA", "client", fp, nonce); got != "44406f480ccd67e887c77e63946dacbd6870f750f32d9b89ddbfd2a5c3867cc6" {
		t.Fatalf("client proof: %s", got)
	}
	if got := Proof("K7QM4TXP9WDA", "server", fp, nonce); got != "86dd455942d7fefa2cf30605f024cbd0016a341cb4bd116fdfa73f3a1562e5c6" {
		t.Fatalf("server proof: %s", got)
	}
}

func TestNormalizeAndFormat(t *testing.T) {
	if NormalizeCode(" k7qm-4txp 9wda ") != "K7QM4TXP9WDA" {
		t.Fatal("normalize")
	}
	if FormatCode("K7QM4TXP9WDA") != "K7QM-4TXP-9WDA" {
		t.Fatal("format")
	}
}

func TestCodeShapeAndUniqueness(t *testing.T) {
	m := NewManager()
	seen := map[string]bool{}
	for i := 0; i < 200; i++ {
		c, _, err := m.Start(time.Minute)
		if err != nil || len(c) != CodeLength {
			t.Fatalf("bad code %q %v", c, err)
		}
		for _, r := range c {
			if !strings.ContainsRune(alphabet, r) {
				t.Fatalf("symbol %q outside alphabet", r)
			}
		}
		if seen[c] {
			t.Fatalf("duplicate code %s", c)
		}
		seen[c] = true
	}
}

func TestSuccessConsumesCode(t *testing.T) {
	m := NewManager()
	code, _, _ := m.Start(time.Minute)
	sp, ok := m.Complete(fp, nonce, Proof(code, "client", fp, nonce))
	if !ok || sp != Proof(code, "server", fp, nonce) {
		t.Fatal("valid proof must succeed")
	}
	if _, ok := m.Complete(fp, nonce, Proof(code, "client", fp, nonce)); ok {
		t.Fatal("code must be single-use")
	}
	if m.Active() {
		t.Fatal("no active code after use")
	}
}

func TestWrongFingerprintRejected_MITM(t *testing.T) {
	m := NewManager()
	code, _, _ := m.Start(time.Minute)
	otherFp := strings.Repeat("cd", 32) // what a phone behind a MITM would have seen
	if _, ok := m.Complete(fp, nonce, Proof(code, "client", otherFp, nonce)); ok {
		t.Fatal("proof bound to a different certificate must fail")
	}
}

func TestInvalidatedAfterMaxFailures(t *testing.T) {
	m := NewManager()
	code, _, _ := m.Start(time.Minute)
	for i := 0; i < MaxFailures; i++ {
		if _, ok := m.Complete(fp, nonce, "00"); ok {
			t.Fatal("garbage proof accepted")
		}
	}
	if _, ok := m.Complete(fp, nonce, Proof(code, "client", fp, nonce)); ok {
		t.Fatal("code must be dead after max failures, even with the right proof")
	}
}

func TestExpiry(t *testing.T) {
	m := NewManager()
	now := time.Now()
	m.now = func() time.Time { return now }
	code, _, _ := m.Start(5 * time.Minute)
	now = now.Add(5*time.Minute + time.Second)
	if m.Active() {
		t.Fatal("must be inactive after ttl")
	}
	if _, ok := m.Complete(fp, nonce, Proof(code, "client", fp, nonce)); ok {
		t.Fatal("expired code accepted")
	}
}

func TestNewStartReplacesOldCode(t *testing.T) {
	m := NewManager()
	old, _, _ := m.Start(time.Minute)
	m.Start(time.Minute)
	if _, ok := m.Complete(fp, nonce, Proof(old, "client", fp, nonce)); ok {
		t.Fatal("old code must not work after a new start")
	}
}

func TestNoCodeNoPairing(t *testing.T) {
	m := NewManager()
	if _, ok := m.Complete(fp, nonce, "x"); ok {
		t.Fatal("pairing without an active code")
	}
}
