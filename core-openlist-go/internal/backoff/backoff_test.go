package backoff

import (
	"testing"
	"time"
)

func TestBackoffGate(t *testing.T) {
	var g Gate
	g.Activate(500 * time.Millisecond)
	active, remain := g.IsActive(time.Now())
	if !active || remain <= 0 {
		t.Fatalf("expected active gate")
	}
	time.Sleep(600 * time.Millisecond)
	active, _ = g.IsActive(time.Now())
	if active {
		t.Fatalf("expected gate inactive")
	}
}
