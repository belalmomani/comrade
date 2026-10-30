package disks

import (
	"errors"
	"testing"
)

func TestComputeNormal(t *testing.T) {
	d := Compute("C:", 512, 77)
	if d.UsedBytes != 435 || d.FreeBytes != 77 || d.TotalBytes != 512 || !d.Available {
		t.Fatalf("unexpected: %+v", d)
	}
}

func TestComputeClampsFreeAboveTotal(t *testing.T) {
	d := Compute("D:", 100, 150)
	if d.FreeBytes != 100 || d.UsedBytes != 0 {
		t.Fatalf("free must be clamped: %+v", d)
	}
}

func TestUnavailable(t *testing.T) {
	d := Unavailable("E:", errors.New("not ready"))
	if d.Available || d.Error != "not ready" || d.TotalBytes != 0 {
		t.Fatalf("unexpected: %+v", d)
	}
}
