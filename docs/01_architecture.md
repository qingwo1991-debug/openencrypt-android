# 01 Architecture

## Runtime Topology
- OpenList (Go): `127.0.0.1:5244`
- Encrypt Gateway (Rust): `127.0.0.1:5344`
- Android app serves as control plane and lifecycle orchestrator.

## Data Plane Rule
All user-facing playback/download/WebDAV traffic uses gateway entry (`5344`) to avoid split-path regressions.

## Control Plane Responsibilities
- Start sequence: Go -> health check -> Rust
- Stop sequence: Rust -> Go
- Health probes and auto-recovery with exponential backoff
- Runtime config apply with validation and rollback

## Storage
- SQLite only on Android internal storage
- WAL enabled
- periodic checkpoint and graceful shutdown flush

## Security
- sensitive values are never logged raw
- keystore-backed secrets for tokens/password wrappers
