package control

import (
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"os"
	"path/filepath"
	"time"

	"diskati/companion/internal/devices"
	"diskati/companion/internal/disks"
)

// ErrNotRunning means no responsive background process was found.
var ErrNotRunning = errors.New("diskati is not running")

// ReadInfo loads the info file written by the running process.
func ReadInfo(dir string) (Info, error) {
	var info Info
	data, err := os.ReadFile(filepath.Join(dir, infoFile))
	if err != nil {
		if os.IsNotExist(err) {
			return info, ErrNotRunning
		}
		return info, err
	}
	if err := json.Unmarshal(data, &info); err != nil {
		return info, fmt.Errorf("corrupt control file: %w", err)
	}
	return info, nil
}

func call(dir, method, path string, out any) error {
	info, err := ReadInfo(dir)
	if err != nil {
		return err
	}
	req, err := http.NewRequest(method, fmt.Sprintf("http://127.0.0.1:%d%s", info.Port, path), nil)
	if err != nil {
		return err
	}
	req.Header.Set(tokenHeader, info.Token)
	resp, err := (&http.Client{Timeout: 3 * time.Second}).Do(req)
	if err != nil {
		return ErrNotRunning // stale info file or process gone
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		b, _ := io.ReadAll(io.LimitReader(resp.Body, 256))
		return fmt.Errorf("control request failed: %s %s", resp.Status, string(b))
	}
	if out == nil {
		return nil
	}
	return json.NewDecoder(resp.Body).Decode(out)
}

// GetStatus asks the running process for its status.
func GetStatus(dir string) (StatusResponse, error) {
	var s StatusResponse
	err := call(dir, http.MethodGet, "/status", &s)
	return s, err
}

// GetDisks asks the running process for the disk list.
func GetDisks(dir string) ([]disks.Disk, error) {
	var d []disks.Disk
	err := call(dir, http.MethodGet, "/disks", &d)
	return d, err
}

// RequestStop asks the running process to exit.
func RequestStop(dir string) error {
	return call(dir, http.MethodPost, "/stop", nil)
}

// StartPairing asks the service for a new one-time pairing code.
func StartPairing(dir string) (PairingInfo, error) {
	var p PairingInfo
	err := call(dir, http.MethodPost, "/pair/start", &p)
	return p, err
}

// CancelPairing invalidates the active pairing code.
func CancelPairing(dir string) error {
	return call(dir, http.MethodPost, "/pair/cancel", nil)
}

// ListDevices returns the paired phones.
func ListDevices(dir string) ([]devices.Public, error) {
	var d []devices.Public
	err := call(dir, http.MethodGet, "/devices", &d)
	return d, err
}

// RemoveDevice unpairs one phone ("all" unpairs every phone).
func RemoveDevice(dir, id string) error {
	return call(dir, http.MethodPost, "/devices/remove?id="+url.QueryEscape(id), nil)
}
