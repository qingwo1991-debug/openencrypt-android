# 08 Rust SQLite Migration

## Objective
Replace MySQL-only runtime with SQLite backend for Android deployment.

## Changes
- `MySqlPool` abstraction replaced with backend interface
- SQLite backend implemented first
- SQL migrated to SQLite syntax (`ON CONFLICT DO UPDATE`)
- JSON columns stored as TEXT + serde

## Data Files
- db path in app internal storage
- WAL mode enabled
- startup migration + integrity check

## Compatibility
- preserve API behavior for timeout profiles, runtime kv, and metadata cache
