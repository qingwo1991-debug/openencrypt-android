package backoff

import (
	"sync"
	"time"
)

type Gate struct {
	mu    sync.RWMutex
	until time.Time
}

func (g *Gate) Activate(d time.Duration) {
	if d <= 0 {
		return
	}
	g.mu.Lock()
	defer g.mu.Unlock()
	until := time.Now().Add(d)
	if until.After(g.until) {
		g.until = until
	}
}

func (g *Gate) IsActive(now time.Time) (bool, time.Duration) {
	g.mu.RLock()
	defer g.mu.RUnlock()
	if g.until.IsZero() || !now.Before(g.until) {
		return false, 0
	}
	return true, g.until.Sub(now)
}

func (g *Gate) Clear() {
	g.mu.Lock()
	g.until = time.Time{}
	g.mu.Unlock()
}
