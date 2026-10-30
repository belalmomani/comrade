//go:build windows

package disks

import (
	"syscall"
	"unsafe"
)

const driveFixed = 3

var (
	kernel32                = syscall.NewLazyDLL("kernel32.dll")
	procGetLogicalDrives    = kernel32.NewProc("GetLogicalDrives")
	procGetDriveTypeW       = kernel32.NewProc("GetDriveTypeW")
	procGetDiskFreeSpaceExW = kernel32.NewProc("GetDiskFreeSpaceExW")
)

func listPlatform() ([]Disk, error) {
	mask, _, _ := procGetLogicalDrives.Call()
	var out []Disk
	for i := 0; i < 26; i++ {
		if mask&(1<<uint(i)) == 0 {
			continue
		}
		letter := string(rune('A'+i)) + ":"
		root, err := syscall.UTF16PtrFromString(letter + `\`)
		if err != nil {
			out = append(out, Unavailable(letter, err))
			continue
		}
		t, _, _ := procGetDriveTypeW.Call(uintptr(unsafe.Pointer(root)))
		if t != driveFixed {
			continue // الإصدار الأول: الأقراص الثابتة فقط
		}
		var freeToCaller, total, totalFree uint64
		r, _, callErr := procGetDiskFreeSpaceExW.Call(
			uintptr(unsafe.Pointer(root)),
			uintptr(unsafe.Pointer(&freeToCaller)),
			uintptr(unsafe.Pointer(&total)),
			uintptr(unsafe.Pointer(&totalFree)),
		)
		if r == 0 {
			out = append(out, Unavailable(letter, callErr))
			continue
		}
		out = append(out, Compute(letter, total, totalFree))
	}
	return out, nil
}
