package main

import (
	"time"

	"diskati/companion/internal/control"
	"diskati/companion/internal/devices"
	"diskati/companion/internal/pairing"
	"diskati/companion/internal/remote"
)

const pairingTTL = 5 * time.Minute

// app implements control.Admin on top of the real components.
type app struct {
	pair        *pairing.Manager
	store       *devices.Store
	port        func() int
	fingerprint string
}

func (a *app) StartPairing() (control.PairingInfo, error) {
	code, exp, err := a.pair.Start(pairingTTL)
	if err != nil {
		return control.PairingInfo{}, err
	}
	return control.PairingInfo{
		Code:        pairing.FormatCode(code),
		ExpiresAt:   exp,
		Port:        a.port(),
		Fingerprint: a.fingerprint,
		Addresses:   remote.LocalAddresses(),
	}, nil
}

func (a *app) CancelPairing()              { a.pair.Cancel() }
func (a *app) Devices() []devices.Public   { return a.store.List() }
func (a *app) RemoveDevice(id string) bool { return a.store.Remove(id) }
func (a *app) RemoveAllDevices() int       { return a.store.RemoveAll() }
