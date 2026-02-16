package server

import (
	"bytes"
	"encoding/json"
	"io"
	"log"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/openlist/openencrypt-android/core-openlist-go/internal/config"
)

func TestDecryptEndpoint(t *testing.T) {
	cfg := config.Config{
		ListenAddr:                 "127.0.0.1:0",
		GatewayBaseURL:             "http://127.0.0.1:1",
		HeaderTimeout:              1 * time.Second,
		ReadIdleTimeout:            1 * time.Second,
		ProbeBudgetList:            1 * time.Second,
		ProbeBudgetStream:          1 * time.Second,
		UpstreamBackoff:            1 * time.Second,
		EnableUpstreamFastFail:     true,
		EnableParallelDecrypt:      true,
		ParallelDecryptThreshold:   1,
		ParallelDecryptConcurrency: 2,
	}
	srv := New(cfg, log.New(io.Discard, "", 0))
	ts := httptest.NewServer(srv.Handler())
	defer ts.Close()

	payload := map[string]any{"names": []string{"a.enc", "b.enc"}}
	buf, _ := json.Marshal(payload)
	resp, err := http.Post(ts.URL+"/v1/crypto/decrypt-names", "application/json", bytes.NewReader(buf))
	if err != nil {
		t.Fatalf("post: %v", err)
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		t.Fatalf("unexpected code: %d", resp.StatusCode)
	}
}
