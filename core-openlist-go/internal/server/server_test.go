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
	decryptor "github.com/openlist/openencrypt-android/core-openlist-go/internal/crypto"
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

func TestProxyInjectsEncryptRuleHeaders(t *testing.T) {
	var gotOp string
	var gotRulePath string
	var gotEncType string
	var gotEncName string
	var gotPassword string
	upstream := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		gotOp = r.Header.Get("X-OpenEncrypt-Operation")
		gotRulePath = r.Header.Get("X-OpenEncrypt-Rule-Path")
		gotEncType = r.Header.Get("X-OpenEncrypt-Rule-Enc-Type")
		gotEncName = r.Header.Get("X-OpenEncrypt-Rule-Enc-Name")
		gotPassword = r.Header.Get("X-OpenEncrypt-Rule-Password")
		w.WriteHeader(http.StatusOK)
	}))
	defer upstream.Close()

	cfg := config.Config{
		ListenAddr:                 "127.0.0.1:0",
		GatewayBaseURL:             upstream.URL,
		EncryptRulesJSON:           `[{"path":"/123/encrypt/*","password":"secret","enc_type":"aes-ctr","enc_name":true,"enable":true}]`,
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

	req, err := http.NewRequest(http.MethodPut, ts.URL+"/123/encrypt/a.txt", strings.NewReader("x"))
	if err != nil {
		t.Fatalf("new request: %v", err)
	}
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		t.Fatalf("do request: %v", err)
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		t.Fatalf("unexpected status: %d", resp.StatusCode)
	}
	if gotOp != "encrypt" {
		t.Fatalf("unexpected op: %q", gotOp)
	}
	if gotRulePath != "/123/encrypt/*" || gotEncType != "aes-ctr" || gotEncName != "true" || gotPassword != "secret" {
		t.Fatalf("unexpected rule headers path=%q encType=%q encName=%q password=%q", gotRulePath, gotEncType, gotEncName, gotPassword)
	}
}

func TestProxyDecryptsResponseBody(t *testing.T) {
	original := []byte("hello from encrypted storage")
	password := "decrypt-test-key"
	encType := "aes-ctr"

	ce := decryptor.NewContentEncryptor()
	encryptedReader, err := ce.EncryptStream(bytes.NewReader(original), encType, password, int64(len(original)))
	if err != nil {
		t.Fatalf("pre-encrypt: %v", err)
	}
	encryptedData, _ := io.ReadAll(encryptedReader)

	upstream := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/octet-stream")
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write(encryptedData)
	}))
	defer upstream.Close()

	cfg := config.Config{
		ListenAddr:               "127.0.0.1:0",
		GatewayBaseURL:           upstream.URL,
		EncryptRulesJSON:         `[{"path":"/files/*","password":"` + password + `","enc_type":"` + encType + `","enc_name":false,"enable":true}]`,
		HeaderTimeout:            1 * time.Second,
		ReadIdleTimeout:          1 * time.Second,
		ProbeBudgetList:          1 * time.Second,
		ProbeBudgetStream:        1 * time.Second,
		UpstreamBackoff:          1 * time.Second,
		EnableUpstreamFastFail:   true,
		EnableParallelDecrypt:    true,
		ParallelDecryptThreshold: 1,
		ParallelDecryptConcurrency: 2,
	}
	srv := New(cfg, log.New(io.Discard, "", 0))
	ts := httptest.NewServer(srv.Handler())
	defer ts.Close()

	resp, err := http.Get(ts.URL + "/files/document.bin")
	if err != nil {
		t.Fatalf("get: %v", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		t.Fatalf("expected 200, got %d", resp.StatusCode)
	}

	got, err := io.ReadAll(resp.Body)
	if err != nil {
		t.Fatalf("read body: %v", err)
	}

	if !bytes.Equal(got, original) {
		t.Fatalf("decrypted body mismatch: got %q, want %q", string(got), string(original))
	}
}

func TestProxyEncryptsRequestBody(t *testing.T) {
	original := []byte("this will be encrypted on the wire")
	password := "encrypt-test-key"
	encType := "aes-ctr"

	var upstreamGotBody []byte
	upstream := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		upstreamGotBody, _ = io.ReadAll(r.Body)
		w.WriteHeader(http.StatusCreated)
	}))
	defer upstream.Close()

	cfg := config.Config{
		ListenAddr:               "127.0.0.1:0",
		GatewayBaseURL:           upstream.URL,
		EncryptRulesJSON:         `[{"path":"/upload/*","password":"` + password + `","enc_type":"` + encType + `","enc_name":false,"enable":true}]`,
		HeaderTimeout:            1 * time.Second,
		ReadIdleTimeout:          1 * time.Second,
		ProbeBudgetList:          1 * time.Second,
		ProbeBudgetStream:        1 * time.Second,
		UpstreamBackoff:          1 * time.Second,
		EnableUpstreamFastFail:   true,
		EnableParallelDecrypt:    true,
		ParallelDecryptThreshold: 1,
		ParallelDecryptConcurrency: 2,
	}
	srv := New(cfg, log.New(io.Discard, "", 0))
	ts := httptest.NewServer(srv.Handler())
	defer ts.Close()

	resp, err := http.Post(ts.URL+"/upload/file.bin", "application/octet-stream", bytes.NewReader(original))
	if err != nil {
		t.Fatalf("post: %v", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusCreated {
		t.Fatalf("expected 201, got %d", resp.StatusCode)
	}

	if bytes.Equal(upstreamGotBody, original) {
		t.Fatal("upstream received raw plaintext — encryption did not happen")
	}

	ce := decryptor.NewContentEncryptor()
	decryptedReader, err := ce.DecryptStream(bytes.NewReader(upstreamGotBody), encType, password, int64(len(upstreamGotBody)))
	if err != nil {
		t.Fatalf("decrypt: %v", err)
	}
	decrypted, _ := io.ReadAll(decryptedReader)
	if !bytes.Equal(decrypted, original) {
		t.Fatalf("encrypt→decrypt mismatch")
	}
}
