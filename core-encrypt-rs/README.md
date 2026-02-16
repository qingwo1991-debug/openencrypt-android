# core-encrypt-rs

SQLite-first encryption gateway skeleton for Android deployment.

## Run
```bash
cd core-encrypt-rs
cargo run
```

## Env
- `LISTEN_ADDR` (default `0.0.0.0:5344`)
- `SQLITE_PATH` (default `./data/openencrypt.sqlite3`)
- `AUTO_MIGRATE` (default `true`)

## Endpoints
- `GET /healthz`
- `GET/PUT /v2/admin/runtime-kv/:key`
- `GET/PUT /v2/admin/timeout-profiles/:iface_name`
