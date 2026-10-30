// Package logx writes a technical log. It must never receive file names, paths or contents.
package logx

import (
	"log"
	"os"
	"path/filepath"
)

const maxBytes = 1 << 20 // 1 MiB: truncated at startup when exceeded

// New opens dir/diskati.log. Callers log events and error codes only.
func New(dir string) (*log.Logger, *os.File, error) {
	if err := os.MkdirAll(dir, 0o700); err != nil {
		return nil, nil, err
	}
	p := filepath.Join(dir, "diskati.log")
	flags := os.O_CREATE | os.O_WRONLY | os.O_APPEND
	if st, err := os.Stat(p); err == nil && st.Size() > maxBytes {
		flags = os.O_CREATE | os.O_WRONLY | os.O_TRUNC
	}
	f, err := os.OpenFile(p, flags, 0o600)
	if err != nil {
		return nil, nil, err
	}
	return log.New(f, "", log.LstdFlags|log.LUTC), f, nil
}
