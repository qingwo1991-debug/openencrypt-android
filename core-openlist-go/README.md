# core-openlist-go

OpenList runtime integration layer for Android embedding.

## What it provides
- Local control-plane runtime service (`127.0.0.1:5244` by default)
- `/ping` and `/healthz` probes
- Gateway reverse-proxy path with timeout budgets and upstream fast-fail backoff
- Bounded parallel filename decrypt endpoint with stable output ordering
- Concurrent-safe decrypt cache implementation

## Run
```bash
go run ./cmd/openlist-runtime
```

## Env
- `LISTEN_ADDR` (default `127.0.0.1:5244`)
- `GATEWAY_BASE_URL` (default `http://127.0.0.1:5344`)
- `HEADER_TIMEOUT_MS`
- `READ_IDLE_TIMEOUT_MS`
- `PROBE_BUDGET_LIST_MS`
- `PROBE_BUDGET_STREAM_MS`
- `UPSTREAM_BACKOFF_SECONDS`
- `ENABLE_UPSTREAM_FAST_FAIL`
- `ENABLE_PARALLEL_DECRYPT`
- `PARALLEL_DECRYPT_THRESHOLD`
- `PARALLEL_DECRYPT_CONCURRENCY`
