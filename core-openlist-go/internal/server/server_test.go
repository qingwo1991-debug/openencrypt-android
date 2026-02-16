package server

import (
	"bytes"
	"encoding/json"
	"io"
	"log"
	"net/http"
	"net/http/httptest"
	"strings"
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

func TestProxyPlaybackRangeRequest(t *testing.T) {
	var gotRange string
	upstream := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		gotRange = r.Header.Get("Range")
		w.Header().Set("Content-Type", "video/mp4")
		w.WriteHeader(http.StatusPartialContent)
		_, _ = w.Write([]byte("chunk"))
	}))
	defer upstream.Close()

	cfg := config.Config{
		ListenAddr:                 "127.0.0.1:0",
		GatewayBaseURL:             upstream.URL,
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

	req, err := http.NewRequest(http.MethodGet, ts.URL+"/api/stream/video.mkv", nil)
	if err != nil {
		t.Fatalf("new request: %v", err)
	}
	req.Header.Set("Range", "bytes=0-1023")
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		t.Fatalf("do request: %v", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusPartialContent {
		t.Fatalf("unexpected status: %d", resp.StatusCode)
	}
	if gotRange != "bytes=0-1023" {
		t.Fatalf("range not forwarded: %q", gotRange)
	}
}

func TestProxyWebDavMethodMatrix(t *testing.T) {
	methods := []string{
		http.MethodGet,
		http.MethodPut,
		http.MethodDelete,
		http.MethodHead,
		"PROPFIND",
		"MKCOL",
		"MOVE",
	}

	for _, method := range methods {
		method := method
		t.Run(strings.ToLower(method), func(t *testing.T) {
			var gotMethod string
			var gotDest string
			upstream := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				gotMethod = r.Method
				gotDest = r.Header.Get("Destination")
				w.WriteHeader(http.StatusCreated)
				_, _ = w.Write([]byte("ok"))
			}))
			defer upstream.Close()

			cfg := config.Config{
				ListenAddr:                 "127.0.0.1:0",
				GatewayBaseURL:             upstream.URL,
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

			req, err := http.NewRequest(method, ts.URL+"/dav/dir/file.txt", bytes.NewReader([]byte("x")))
			if err != nil {
				t.Fatalf("new request: %v", err)
			}
			req.Header.Set("Destination", "http://example/target")
			if method == "PROPFIND" {
				req.Header.Set("Depth", "1")
			}
			resp, err := http.DefaultClient.Do(req)
			if err != nil {
				t.Fatalf("do request: %v", err)
			}
			defer resp.Body.Close()

			if resp.StatusCode != http.StatusCreated {
				t.Fatalf("unexpected status: %d", resp.StatusCode)
			}
			if gotMethod != method {
				t.Fatalf("method not forwarded: got=%s want=%s", gotMethod, method)
			}
			if gotDest != "http://example/target" {
				t.Fatalf("destination not forwarded: %q", gotDest)
			}
		})
	}
}
