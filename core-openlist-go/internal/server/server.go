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
	"github.com/openlist/openencrypt-android/core-openlist-go/internal/db"
	"github.com/openlist/openencrypt-android/core-openlist-go/internal/rules"
)

type Server struct {
	cfg       config.Config
	gate      *backoff.Gate
	decryptor *decryptor.Decryptor
	encryptor *decryptor.ContentEncryptor
	matcher   *rules.Matcher
	db        *db.DB
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
	matcher, err := rules.FromJSON(cfg.EncryptRulesJSON)
	if err != nil {
		logger.Printf("invalid ENCRYPT_RULES_JSON, ignored: %v", err)
		matcher = &rules.Matcher{}
	}

	database, dbErr := db.Open(cfg.SQLitePath, cfg.AutoMigrate)
	if dbErr != nil {
		logger.Printf("sqlite open failed, admin endpoints unavailable: %v", dbErr)
	}

	return &Server{
		cfg:       cfg,
		gate:      &backoff.Gate{},
		decryptor: decryptor.NewDecryptor(),
		encryptor: decryptor.NewContentEncryptor(),
		matcher:   matcher,
		db:        database,
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
	mux.HandleFunc("/v2/admin/runtime-kv/", s.handleRuntimeKV)
	mux.HandleFunc("/v2/admin/timeout-profiles/", s.handleTimeoutProfile)
	mux.HandleFunc("/v2/admin/db/integrity", s.handleDBIntegrity)
	mux.HandleFunc("/v2/admin/db/checkpoint", s.handleDBCheckpoint)
	mux.HandleFunc("/", s.handleProxy)
	return mux
}

func (s *Server) handlePing(w http.ResponseWriter, _ *http.Request) {
	writeJSON(w, http.StatusOK, map[string]any{"status": "ok"})
}

func (s *Server) handleHealthz(w http.ResponseWriter, _ *http.Request) {
	dbOK := s.db != nil && s.db.Ping() == nil
	writeJSON(w, http.StatusOK, map[string]any{
		"status":                     "ok",
		"db":                         dbOK,
		"gateway_base_url":           s.cfg.GatewayBaseURL,
		"parallel_decrypt_enabled":   s.cfg.EnableParallelDecrypt,
		"parallel_decrypt_threshold": s.cfg.ParallelDecryptThreshold,
		"encrypt_rules_count":        s.matcher.Count(),
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

	// ── encrypt rule matching ──
	rule, matched := s.matcher.Match(r.URL.Path)
	isWrite := isWriteMethod(r.Method)
	cryptoOp := ""
	if matched {
		if isWrite {
			cryptoOp = "encrypt"
		} else {
			cryptoOp = "decrypt"
		}
	}

	// ── build upstream URL ──
	targetPath := r.URL.RequestURI()

	// For uploads with enc_name: encrypt the filename in the path
	// (downloads keep encrypted path as-is → sign stays valid)
	if matched && rule.EncName && isWrite && cryptoOp == "encrypt" {
		dir := path.Dir(r.URL.Path)
		filename := path.Base(r.URL.Path)
		if encryptedName, err := s.encryptor.EncryptName(filename, rule.Password, rule.EncType); err == nil {
			newPath := dir
			if newPath == "." {
				newPath = ""
			}
			if !strings.HasPrefix(newPath, "/") {
				newPath = "/" + newPath
			}
			newPath = strings.TrimSuffix(newPath, "/") + "/" + encryptedName
			targetPath = newPath
			if r.URL.RawQuery != "" {
				targetPath += "?" + r.URL.RawQuery
			}
		}
	}

	target := s.cfg.GatewayBaseURL + targetPath

	// ── request body ──
	var bodyReader io.Reader
	if cryptoOp == "encrypt" && rule.EncType != "" {
		encryptedBody, err := s.encryptor.EncryptStream(r.Body, rule.EncType, rule.Password, r.ContentLength)
		if err != nil {
			s.log.Printf("encrypt stream error: %v", err)
			writeJSON(w, http.StatusInternalServerError, map[string]string{"error": "encrypt body failed"})
			return
		}
		bodyReader = encryptedBody
	} else if isWrite {
		bodyReader = io.LimitReader(r.Body, 1<<30)
	} else {
		body, err := io.ReadAll(r.Body)
		if err != nil {
			writeJSON(w, http.StatusBadRequest, map[string]string{"error": "read body failed"})
			return
		}
		bodyReader = bytes.NewReader(body)
	}

	upReq, err := http.NewRequestWithContext(ctx, r.Method, target, bodyReader)
	if err != nil {
		writeJSON(w, http.StatusInternalServerError, map[string]string{"error": "build upstream request failed"})
		return
	}
	copyHeaders(upReq.Header, r.Header)
	if cryptoOp != "" {
		upReq.Header.Del("Content-Length") // size changes with encryption
	}
	s.addEncryptRuleHeaders(upReq, rule, matched, cryptoOp)

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

	// ── response body ──
	copyHeaders(w.Header(), resp.Header)
	if cryptoOp == "decrypt" && rule.EncType != "" && resp.StatusCode < 400 {
		decryptedBody, err := s.encryptor.DecryptStream(resp.Body, rule.EncType, rule.Password, resp.ContentLength)
		if err != nil {
			s.log.Printf("decrypt stream error: %v", err)
			writeJSON(w, http.StatusInternalServerError, map[string]string{"error": "decrypt body failed"})
			return
		}
		w.Header().Del("Content-Length") // size changes with decryption
		w.WriteHeader(resp.StatusCode)
		n, err := io.Copy(w, decryptedBody)
		if err != nil {
			s.log.Printf("copy decrypted body failed: %v", err)
		}
		s.log.Printf("< ts=%s method=%s route=%s status=%d dur_ms=%d bytes=%d crypto=%s", time.Now().Format(time.RFC3339Nano), r.Method, r.URL.RequestURI(), resp.StatusCode, time.Since(start).Milliseconds(), n, cryptoOp)
		return
	}

	w.WriteHeader(resp.StatusCode)
	n, err := io.Copy(w, resp.Body)
	if err != nil {
		s.log.Printf("copy body failed: %v", err)
	}
	s.log.Printf("< ts=%s method=%s route=%s status=%d dur_ms=%d bytes=%d crypto=%s", time.Now().Format(time.RFC3339Nano), r.Method, r.URL.RequestURI(), resp.StatusCode, time.Since(start).Milliseconds(), n, cryptoOp)
}

func (s *Server) addEncryptRuleHeaders(upReq *http.Request, rule rules.CompiledRule, matched bool, op string) {
	if !matched {
		upReq.Header.Del("X-OpenEncrypt-Rule-Matched")
		upReq.Header.Del("X-OpenEncrypt-Rule-Path")
		upReq.Header.Del("X-OpenEncrypt-Rule-Enc-Type")
		upReq.Header.Del("X-OpenEncrypt-Rule-Enc-Name")
		upReq.Header.Del("X-OpenEncrypt-Rule-Password")
		upReq.Header.Del("X-OpenEncrypt-Operation")
		return
	}
	upReq.Header.Set("X-OpenEncrypt-Rule-Matched", "true")
	upReq.Header.Set("X-OpenEncrypt-Rule-Path", rule.Path)
	upReq.Header.Set("X-OpenEncrypt-Rule-Enc-Type", rule.EncType)
	upReq.Header.Set("X-OpenEncrypt-Rule-Enc-Name", strconvBool(rule.EncName))
	upReq.Header.Set("X-OpenEncrypt-Rule-Password", rule.Password)
	upReq.Header.Set("X-OpenEncrypt-Operation", op)
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

func isWriteMethod(method string) bool {
	switch strings.ToUpper(method) {
	case http.MethodPost, http.MethodPut, http.MethodPatch, http.MethodDelete, "MKCOL", "MOVE", "COPY", "PROPPATCH":
		return true
	default:
		return false
	}
}

// ── v2 admin handlers (ported from Rust gateway) ──

func (s *Server) handleRuntimeKV(w http.ResponseWriter, r *http.Request) {
	if s.db == nil {
		writeJSON(w, http.StatusServiceUnavailable, map[string]string{"error": "database unavailable"})
		return
	}
	key := strings.TrimPrefix(r.URL.Path, "/v2/admin/runtime-kv/")
	if key == "" || strings.Contains(key, "/") {
		writeJSON(w, http.StatusBadRequest, map[string]string{"error": "invalid key"})
		return
	}
	switch r.Method {
	case http.MethodGet:
		v, ok, err := s.db.GetRuntimeKV(key)
		if err != nil {
			s.log.Printf("runtime-kv get error: %v", err)
			writeJSON(w, http.StatusInternalServerError, map[string]string{"error": "db error"})
			return
		}
		writeJSON(w, http.StatusOK, map[string]any{"key": key, "value": v, "found": ok})
	case http.MethodPut:
		var payload struct {
			Value string `json:"value"`
		}
		if err := json.NewDecoder(r.Body).Decode(&payload); err != nil || payload.Value == "" {
			writeJSON(w, http.StatusBadRequest, map[string]string{"error": "missing 'value' string"})
			return
		}
		if err := s.db.SetRuntimeKV(key, payload.Value); err != nil {
			s.log.Printf("runtime-kv set error: %v", err)
			writeJSON(w, http.StatusInternalServerError, map[string]string{"error": "db error"})
			return
		}
		writeJSON(w, http.StatusOK, map[string]any{"ok": true, "key": key})
	default:
		writeJSON(w, http.StatusMethodNotAllowed, map[string]string{"error": "method not allowed"})
	}
}

func (s *Server) handleTimeoutProfile(w http.ResponseWriter, r *http.Request) {
	if s.db == nil {
		writeJSON(w, http.StatusServiceUnavailable, map[string]string{"error": "database unavailable"})
		return
	}
	ifaceName := strings.TrimPrefix(r.URL.Path, "/v2/admin/timeout-profiles/")
	if ifaceName == "" || strings.Contains(ifaceName, "/") {
		writeJSON(w, http.StatusBadRequest, map[string]string{"error": "invalid iface_name"})
		return
	}
	tenantID := "default"
	switch r.Method {
	case http.MethodGet:
		profile, found, err := s.db.LoadTimeoutProfile(tenantID, ifaceName)
		if err != nil {
			s.log.Printf("timeout-profile get error: %v", err)
			writeJSON(w, http.StatusInternalServerError, map[string]string{"error": "db error"})
			return
		}
		writeJSON(w, http.StatusOK, map[string]any{
			"tenant_id":  tenantID,
			"iface_name": ifaceName,
			"profile":    profile,
			"found":      found,
		})
	case http.MethodPut:
		var profile db.TimeoutProfile
		if err := json.NewDecoder(r.Body).Decode(&profile); err != nil {
			writeJSON(w, http.StatusBadRequest, map[string]string{"error": "invalid profile payload"})
			return
		}
		if err := s.db.UpsertTimeoutProfile(tenantID, ifaceName, profile); err != nil {
			s.log.Printf("timeout-profile upsert error: %v", err)
			writeJSON(w, http.StatusInternalServerError, map[string]string{"error": "db error"})
			return
		}
		writeJSON(w, http.StatusOK, map[string]any{"ok": true, "tenant_id": tenantID, "iface_name": ifaceName})
	default:
		writeJSON(w, http.StatusMethodNotAllowed, map[string]string{"error": "method not allowed"})
	}
}

func (s *Server) handleDBIntegrity(w http.ResponseWriter, _ *http.Request) {
	if s.db == nil {
		writeJSON(w, http.StatusServiceUnavailable, map[string]string{"error": "database unavailable"})
		return
	}
	if err := s.db.IntegrityCheck(); err != nil {
		s.log.Printf("db integrity check failed: %v", err)
		writeJSON(w, http.StatusInternalServerError, map[string]string{"error": err.Error()})
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"ok": true})
}

func (s *Server) handleDBCheckpoint(w http.ResponseWriter, _ *http.Request) {
	if s.db == nil {
		writeJSON(w, http.StatusServiceUnavailable, map[string]string{"error": "database unavailable"})
		return
	}
	if err := s.db.WALCheckpointTruncate(); err != nil {
		s.log.Printf("db checkpoint error: %v", err)
		writeJSON(w, http.StatusInternalServerError, map[string]string{"error": err.Error()})
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"ok": true, "checkpoint": "TRUNCATE"})
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

func strconvBool(v bool) string {
	if v {
		return "true"
	}
	return "false"
}
