package config

import (
	"fmt"
	"os"
	"strconv"
	"strings"
	"time"
)

type Config struct {
	ListenAddr                 string
	GatewayBaseURL             string
	HeaderTimeout              time.Duration
	ReadIdleTimeout            time.Duration
	ProbeBudgetList            time.Duration
	ProbeBudgetStream          time.Duration
	UpstreamBackoff            time.Duration
	EnableUpstreamFastFail     bool
	EnableParallelDecrypt      bool
	ParallelDecryptThreshold   int
	ParallelDecryptConcurrency int
}

func FromEnv() (Config, error) {
	cfg := Config{
		ListenAddr:                 getenv("LISTEN_ADDR", "127.0.0.1:5244"),
		GatewayBaseURL:             strings.TrimRight(getenv("GATEWAY_BASE_URL", "http://127.0.0.1:5344"), "/"),
		HeaderTimeout:              ms(getenvInt("HEADER_TIMEOUT_MS", 5000)),
		ReadIdleTimeout:            ms(getenvInt("READ_IDLE_TIMEOUT_MS", 12000)),
		ProbeBudgetList:            ms(getenvInt("PROBE_BUDGET_LIST_MS", 1200)),
		ProbeBudgetStream:          ms(getenvInt("PROBE_BUDGET_STREAM_MS", 2500)),
		UpstreamBackoff:            time.Duration(getenvInt("UPSTREAM_BACKOFF_SECONDS", 20)) * time.Second,
		EnableUpstreamFastFail:     getenvBool("ENABLE_UPSTREAM_FAST_FAIL", true),
		EnableParallelDecrypt:      getenvBool("ENABLE_PARALLEL_DECRYPT", true),
		ParallelDecryptThreshold:   getenvInt("PARALLEL_DECRYPT_THRESHOLD", 24),
		ParallelDecryptConcurrency: getenvInt("PARALLEL_DECRYPT_CONCURRENCY", 4),
	}

	if cfg.ParallelDecryptConcurrency < 1 {
		return Config{}, fmt.Errorf("PARALLEL_DECRYPT_CONCURRENCY must be >= 1")
	}
	if cfg.ParallelDecryptThreshold < 0 {
		return Config{}, fmt.Errorf("PARALLEL_DECRYPT_THRESHOLD must be >= 0")
	}
	if cfg.GatewayBaseURL == "" {
		return Config{}, fmt.Errorf("GATEWAY_BASE_URL cannot be empty")
	}
	return cfg, nil
}

func getenv(key, fallback string) string {
	if v := strings.TrimSpace(os.Getenv(key)); v != "" {
		return v
	}
	return fallback
}

func getenvInt(key string, fallback int) int {
	v := strings.TrimSpace(os.Getenv(key))
	if v == "" {
		return fallback
	}
	n, err := strconv.Atoi(v)
	if err != nil {
		return fallback
	}
	return n
}

func getenvBool(key string, fallback bool) bool {
	v := strings.TrimSpace(strings.ToLower(os.Getenv(key)))
	if v == "" {
		return fallback
	}
	switch v {
	case "1", "true", "yes", "on":
		return true
	case "0", "false", "no", "off":
		return false
	default:
		return fallback
	}
}

func ms(v int) time.Duration { return time.Duration(v) * time.Millisecond }
