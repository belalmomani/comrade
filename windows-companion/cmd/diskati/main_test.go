package main

import (
	"strings"
	"testing"
)

func TestInstallArgsQuotesPathAndIsFixed(t *testing.T) {
	a := installArgs(`C:\Program Files\Diskati\diskati.exe`)
	joined := strings.Join(a, " ")
	if a[0] != "add" || !strings.Contains(joined, `"C:\Program Files\Diskati\diskati.exe" start`) {
		t.Fatalf("unexpected args: %v", a)
	}
	if !strings.Contains(joined, `HKCU\Software\Microsoft\Windows\CurrentVersion\Run`) {
		t.Fatal("must target the per-user Run key (no admin rights)")
	}
}

func TestUninstallArgs(t *testing.T) {
	a := uninstallArgs()
	if a[0] != "delete" || a[len(a)-1] != "/f" {
		t.Fatalf("unexpected: %v", a)
	}
}
