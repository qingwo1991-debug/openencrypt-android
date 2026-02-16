package server

import (
	"bytes"
	"context"
	"encoding/json"
	"io"
	"log"
	"net/http"
	"path"
	"strings"
	"time"

	"github.com/openlist/openencrypt-android/core-openlist-go/internal/backoff"
	"github.com/openlist/openencrypt-android/core-openlist-go/internal/config"
	decryptor "github.com/openlist/openencrypt-android/core-openlist-go/internal/crypto"
)

type Server struct {
	cfg       config.Config
	gate      *backoff.Gate
	decryptor *decryptor.Decryptor
	log       *log.Logger

	generalClient *http.Client
	streamClient  *http.Client
}

func New(cfg config.Config, logger *log.Logger) *Server {
	if logger == nil {
		logger = log.Default()
	}
	tr := &http.Transport{
		Proxy:                 http.ProxyFromEnvironment,
		ResponseHeaderTimeout: cfg.HeaderTimeout,
		IdleConnTimeout:       cfg.ReadIdleTimeout,
	}
	streamTr := &http.Transport{
		Proxy:                 http.ProxyFromEnvironment,
		ResponseHeaderTimeout: cfg.HeaderTimeout,
		IdleConnTimeout:       cfg.ReadIdleTimeout,
	}
	return &Server{
		cfg:       cfg,
		gate:      &backoff.Gate{},
		decryptor: decryptor.NewDecryptor(),
		log:       logger,
		generalClient: &http.Client{
			Transport: tr,
		},
		streamClient: &http.Client{
			Transport: streamTr,
		},
	}
}

func (s *Server) Handler() http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("/ping", s.handlePing)
	mux.HandleFunc("/healthz", s.handleHealthz)
	mux.HandleFunc("/v1/admin/backoff/activate", s.handleBackoffActivate)
	mux.HandleFunc("/v1/crypto/decrypt-names", s.handleDecryptNames)
	mux.HandleFunc("/", s.handleProxy)
	return mux
}

func (s *Server) handlePing(w http.ResponseWriter, _ *http.Request) {
	writeJSON(w, http.StatusOK, map[string]any{"status": "ok"})
}

func (s *Server) handleHealthz(w http.ResponseWriter, _ *http.Request) {
	writeJSON(w, http.StatusOK, map[string]any{
		"status":                    "ok",
		"gateway_base_url":          s.cfg.GatewayBaseURL,
		"parallel_decrypt_enabled":  s.cfg.EnableParallelDecrypt,
		"parallel_decrypt_threshold": s.cfg.ParallelDecryptThreshold,
	})
}

func (s *Server) handleBackoffActivate(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		writeJSON(w, http.StatusMethodNotAllowed, map[string]string{"error": "method not allowed"})
		return
	}
	d := s.cfg.UpstreamBackoff
	if q := r.URL.Query().Get("seconds"); q != "" {
		if sec, err := time.ParseDuration(q + "s"); err == nil {
			d = sec
		}
	}
	s.gate.Activate(d)
	writeJSON(w, http.StatusOK, map[string]any{"ok": true, "backoff_seconds": int(d.Seconds())})
}

type decryptReq struct {
	Names []string `json:"names"`
}

func (s *Server) handleDecryptNames(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		writeJSON(w, http.StatusMethodNotAllowed, map[string]string{"error": "method not allowed"})
		return
	}
	var req decryptReq
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		s.log.Printf("< ts=%s route=/v1/crypto/decrypt-names status=%d error=%q", time.Now().Format(time.RFC3339Nano), http.StatusBadRequest, "invalid payload")
		writeJSON(w, http.StatusBadRequest, map[string]string{"error": "invalid payload"})
		return
	}
	s.log.Printf("> ts=%s route=/v1/crypto/decrypt-names names=%d", time.Now().Format(time.RFC3339Nano), len(req.Names))
	out := s.decryptor.DecryptNames(r.Context(), req.Names, s.cfg.EnableParallelDecrypt, s.cfg.ParallelDecryptThreshold, s.cfg.ParallelDecryptConcurrency)
	s.log.Printf("< ts=%s route=/v1/crypto/decrypt-names status=%d names=%d", time.Now().Format(time.RFC3339Nano), http.StatusOK, len(out))
	writeJSON(w, http.StatusOK, map[string]any{"names": out})
}

func (s *Server) handleProxy(w http.ResponseWriter, r *http.Request) {
	start := time.Now()
	fileHint := path.Base(r.URL.Path)
	if fileHint == "." || fileHint == "/" {
		fileHint = "-"
	}
	s.log.Printf("> ts=%s method=%s route=%s file=%s", start.Format(time.RFC3339Nano), r.Method, r.URL.RequestURI(), fileHint)

	if s.cfg.EnableUpstreamFastFail {
		if active, remaining := s.gate.IsActive(time.Now()); active {
			w.Header().Set("Retry-After", itoa(int(remaining.Seconds())+1))
			s.log.Printf("< ts=%s method=%s route=%s status=%d dur_ms=%d error=%q", time.Now().Format(time.RFC3339Nano), r.Method, r.URL.RequestURI(), http.StatusServiceUnavailable, time.Since(start).Milliseconds(), "upstream backing off")
			writeJSON(w, http.StatusServiceUnavailable, map[string]string{"error": "upstream backing off"})
			return
		}
	}

	client := s.generalClient
	deadline := s.cfg.ProbeBudgetList
	if isStreamReq(r) {
		client = s.streamClient
		deadline = s.cfg.ProbeBudgetStream
	}
	ctx, cancel := context.WithTimeout(r.Context(), deadline)
	defer cancel()

	target := s.cfg.GatewayBaseURL + r.URL.RequestURI()
	body, err := io.ReadAll(r.Body)
	if err != nil {
		writeJSON(w, http.StatusBadRequest, map[string]string{"error": "read body failed"})
		return
	}

	upReq, err := http.NewRequestWithContext(ctx, r.Method, target, bytes.NewReader(body))
	if err != nil {
		writeJSON(w, http.StatusInternalServerError, map[string]string{"error": "build upstream request failed"})
		return
	}
	copyHeaders(upReq.Header, r.Header)

	resp, err := client.Do(upReq)
	if err != nil {
		s.log.Printf("proxy error: %v", err)
		s.gate.Activate(s.cfg.UpstreamBackoff)
		s.log.Printf("< ts=%s method=%s route=%s status=%d dur_ms=%d error=%q", time.Now().Format(time.RFC3339Nano), r.Method, r.URL.RequestURI(), http.StatusBadGateway, time.Since(start).Milliseconds(), "upstream unavailable")
		writeJSON(w, http.StatusBadGateway, map[string]string{"error": "upstream unavailable"})
		return
	}
	defer resp.Body.Close()

	if resp.StatusCode >= 500 {
		s.gate.Activate(s.cfg.UpstreamBackoff)
	}

	copyHeaders(w.Header(), resp.Header)
	w.WriteHeader(resp.StatusCode)
	n, err := io.Copy(w, resp.Body)
	if err != nil {
		s.log.Printf("copy body failed: %v", err)
	}
	s.log.Printf("< ts=%s method=%s route=%s status=%d dur_ms=%d bytes=%d", time.Now().Format(time.RFC3339Nano), r.Method, r.URL.RequestURI(), resp.StatusCode, time.Since(start).Milliseconds(), n)
}

func copyHeaders(dst, src http.Header) {
	for k, vals := range src {
		dst.Del(k)
		for _, v := range vals {
			dst.Add(k, v)
		}
	}
}

func isStreamReq(r *http.Request) bool {
	if r.Header.Get("Range") != "" {
		return true
	}
	p := strings.ToLower(r.URL.Path)
	return strings.Contains(p, "/stream") || strings.Contains(p, "/download")
}

func writeJSON(w http.ResponseWriter, code int, payload any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(code)
	_ = json.NewEncoder(w).Encode(payload)
}

func itoa(v int) string { return strconv(v) }

func strconv(v int) string {
	if v == 0 {
		return "0"
	}
	neg := v < 0
	if neg {
		v = -v
	}
	buf := [20]byte{}
	i := len(buf)
	for v > 0 {
		i--
		buf[i] = byte('0' + (v % 10))
		v /= 10
	}
	if neg {
		i--
		buf[i] = '-'
	}
	return string(buf[i:])
}
