// Package disks reads logical disk capacity. It never reads file names or contents.
package disks

import (
	"errors"
	"sort"
)

// ErrUnsupported is returned on platforms other than Windows.
var ErrUnsupported = errors.New("disk enumeration is only supported on Windows")

// Disk describes one fixed logical drive.
type Disk struct {
	Letter     string `json:"letter"`
	TotalBytes uint64 `json:"totalBytes"`
	FreeBytes  uint64 `json:"freeBytes"`
	UsedBytes  uint64 `json:"usedBytes"`
	Available  bool   `json:"available"`
	Error      string `json:"error,omitempty"`
}

// Provider lists disks. A fake implementation is used in tests.
type Provider interface {
	List() ([]Disk, error)
}

// SystemProvider reads the real disks of the machine.
type SystemProvider struct{}

// List returns fixed disks sorted by drive letter.
func (SystemProvider) List() ([]Disk, error) {
	d, err := listPlatform()
	if err != nil {
		return nil, err
	}
	sort.Slice(d, func(i, j int) bool { return d[i].Letter < d[j].Letter })
	return d, nil
}

// Compute builds a Disk from raw numbers. Free is clamped so used never underflows.
func Compute(letter string, total, free uint64) Disk {
	if free > total {
		free = total
	}
	return Disk{Letter: letter, TotalBytes: total, FreeBytes: free, UsedBytes: total - free, Available: true}
}

// Unavailable marks a drive that exists but could not be read (locked, no media, access denied).
func Unavailable(letter string, err error) Disk {
	return Disk{Letter: letter, Available: false, Error: err.Error()}
}
