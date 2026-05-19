package db

import "database/sql"

type TimeoutProfile struct {
	ConnectMs  int64 `json:"connect_ms"`
	TtfbMs     int64 `json:"ttfb_ms"`
	ReadIdleMs int64 `json:"read_idle_ms"`
	TotalMs    int64 `json:"total_ms"`
}

func (db *DB) LoadTimeoutProfile(tenantID, ifaceName string) (*TimeoutProfile, bool, error) {
	var p TimeoutProfile
	err := db.pool.QueryRow(
		`SELECT connect_ms, ttfb_ms, read_idle_ms, total_ms
		 FROM timeout_profiles
		 WHERE tenant_id = ? AND iface_name = ? AND enabled = 1
		 LIMIT 1`,
		tenantID, ifaceName,
	).Scan(&p.ConnectMs, &p.TtfbMs, &p.ReadIdleMs, &p.TotalMs)
	if err == sql.ErrNoRows {
		return nil, false, nil
	}
	if err != nil {
		return nil, false, err
	}
	return &p, true, nil
}

func (db *DB) UpsertTimeoutProfile(tenantID, ifaceName string, profile TimeoutProfile) error {
	_, err := db.pool.Exec(
		`INSERT INTO timeout_profiles
		 (tenant_id, iface_name, connect_ms, ttfb_ms, read_idle_ms, total_ms, enabled, updated_at)
		 VALUES (?, ?, ?, ?, ?, ?, 1, datetime('now'))
		 ON CONFLICT(tenant_id, iface_name) DO UPDATE SET
		 connect_ms = excluded.connect_ms,
		 ttfb_ms = excluded.ttfb_ms,
		 read_idle_ms = excluded.read_idle_ms,
		 total_ms = excluded.total_ms,
		 enabled = 1,
		 updated_at = datetime('now')`,
		tenantID, ifaceName, profile.ConnectMs, profile.TtfbMs, profile.ReadIdleMs, profile.TotalMs,
	)
	return err
}
