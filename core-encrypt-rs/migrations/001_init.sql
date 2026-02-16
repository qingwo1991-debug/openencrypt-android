CREATE TABLE IF NOT EXISTS runtime_kv (
  k TEXT PRIMARY KEY,
  v TEXT NOT NULL,
  updated_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS timeout_profiles (
  tenant_id TEXT NOT NULL,
  iface_name TEXT NOT NULL,
  connect_ms INTEGER NOT NULL,
  ttfb_ms INTEGER NOT NULL,
  read_idle_ms INTEGER NOT NULL,
  total_ms INTEGER NOT NULL,
  enabled INTEGER NOT NULL DEFAULT 1,
  updated_at TEXT NOT NULL DEFAULT (datetime('now')),
  PRIMARY KEY (tenant_id, iface_name)
);
