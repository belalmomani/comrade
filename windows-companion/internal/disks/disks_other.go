//go:build !windows

package disks

func listPlatform() ([]Disk, error) { return nil, ErrUnsupported }
