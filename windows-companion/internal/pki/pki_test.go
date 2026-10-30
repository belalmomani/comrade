package pki

import (
	"os"
	"path/filepath"
	"runtime"
	"testing"
)

func TestCreateThenLoadGivesSameFingerprint(t *testing.T) {
	dir := t.TempDir()
	_, fp1, err := LoadOrCreate(dir)
	if err != nil {
		t.Fatal(err)
	}
	_, fp2, err := LoadOrCreate(dir)
	if err != nil {
		t.Fatal(err)
	}
	if fp1 != fp2 || len(fp1) != 64 {
		t.Fatalf("fingerprints differ or malformed: %q %q", fp1, fp2)
	}
}

func TestPrivateKeyNotWorldReadable(t *testing.T) {
	if runtime.GOOS == "windows" {
		t.Skip("POSIX permissions do not apply")
	}
	dir := t.TempDir()
	if _, _, err := LoadOrCreate(dir); err != nil {
		t.Fatal(err)
	}
	st, err := os.Stat(filepath.Join(dir, keyFile))
	if err != nil {
		t.Fatal(err)
	}
	if st.Mode().Perm()&0o077 != 0 {
		t.Fatalf("key permissions too open: %v", st.Mode().Perm())
	}
}

func TestResetChangesFingerprint(t *testing.T) {
	dir := t.TempDir()
	_, fp1, _ := LoadOrCreate(dir)
	if err := Reset(dir); err != nil {
		t.Fatal(err)
	}
	_, fp2, err := LoadOrCreate(dir)
	if err != nil {
		t.Fatal(err)
	}
	if fp1 == fp2 {
		t.Fatal("a new certificate must have a new fingerprint")
	}
}
