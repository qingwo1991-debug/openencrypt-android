package db

import (
	"os"
	"path/filepath"
	"testing"
)

func TestOpenAndPing(t *testing.T) {
	dir := t.TempDir()
	path := filepath.Join(dir, "test.sqlite3")
	db, err := Open(path, true)
	if err != nil {
		t.Fatalf("Open: %v", err)
	}
	defer db.Close()

	if err := db.Ping(); err != nil {
		t.Fatalf("Ping: %v", err)
	}

	// Verify tables exist
	var count int
	if err := db.pool.QueryRow("SELECT COUNT(*) FROM runtime_kv").Scan(&count); err != nil {
		t.Fatalf("runtime_kv table missing: %v", err)
	}
	if err := db.pool.QueryRow("SELECT COUNT(*) FROM timeout_profiles").Scan(&count); err != nil {
		t.Fatalf("timeout_profiles table missing: %v", err)
	}
}

func TestRuntimeKVSetGet(t *testing.T) {
	dir := t.TempDir()
	db, err := Open(filepath.Join(dir, "test.sqlite3"), true)
	if err != nil {
		t.Fatalf("Open: %v", err)
	}
	defer db.Close()

	// Get non-existent key
	v, found, err := db.GetRuntimeKV("no-such-key")
	if err != nil {
		t.Fatalf("GetRuntimeKV: %v", err)
	}
	if found {
		t.Fatalf("expected not found, got value=%q", v)
	}

	// Set
	if err := db.SetRuntimeKV("k1", "v1"); err != nil {
		t.Fatalf("SetRuntimeKV: %v", err)
	}

	// Get existing
	v, found, err = db.GetRuntimeKV("k1")
	if err != nil {
		t.Fatalf("GetRuntimeKV: %v", err)
	}
	if !found || v != "v1" {
		t.Fatalf("expected v1, got found=%v value=%q", found, v)
	}

	// Overwrite
	if err := db.SetRuntimeKV("k1", "v2"); err != nil {
		t.Fatalf("SetRuntimeKV overwrite: %v", err)
	}
	v, found, err = db.GetRuntimeKV("k1")
	if err != nil || !found || v != "v2" {
		t.Fatalf("expected v2 after overwrite, got found=%v value=%q err=%v", found, v, err)
	}
}

func TestTimeoutProfileCRUD(t *testing.T) {
	dir := t.TempDir()
	db, err := Open(filepath.Join(dir, "test.sqlite3"), true)
	if err != nil {
		t.Fatalf("Open: %v", err)
	}
	defer db.Close()

	// Load non-existent
	p, found, err := db.LoadTimeoutProfile("default", "eth0")
	if err != nil {
		t.Fatalf("LoadTimeoutProfile: %v", err)
	}
	if found {
		t.Fatalf("expected not found, got %+v", p)
	}

	// Upsert
	profile := TimeoutProfile{
		ConnectMs:  500,
		TtfbMs:     2000,
		ReadIdleMs: 10000,
		TotalMs:    30000,
	}
	if err := db.UpsertTimeoutProfile("default", "eth0", profile); err != nil {
		t.Fatalf("UpsertTimeoutProfile: %v", err)
	}

	// Load existing
	p, found, err = db.LoadTimeoutProfile("default", "eth0")
	if err != nil {
		t.Fatalf("LoadTimeoutProfile after upsert: %v", err)
	}
	if !found {
		t.Fatal("expected found after upsert")
	}
	if p.ConnectMs != 500 || p.TtfbMs != 2000 || p.ReadIdleMs != 10000 || p.TotalMs != 30000 {
		t.Fatalf("profile mismatch: %+v", p)
	}

	// Update
	profile.ConnectMs = 300
	if err := db.UpsertTimeoutProfile("default", "eth0", profile); err != nil {
		t.Fatalf("UpsertTimeoutProfile update: %v", err)
	}
	p, found, err = db.LoadTimeoutProfile("default", "eth0")
	if err != nil || !found || p.ConnectMs != 300 {
		t.Fatalf("expected ConnectMs=300 after update, got %+v err=%v", p, err)
	}

	// Different tenant/iface is isolated
	p, found, err = db.LoadTimeoutProfile("other", "eth0")
	if err != nil {
		t.Fatalf("LoadTimeoutProfile other tenant: %v", err)
	}
	if found {
		t.Fatalf("expected not found for different tenant, got %+v", p)
	}
}

func TestIntegrityCheck(t *testing.T) {
	dir := t.TempDir()
	db, err := Open(filepath.Join(dir, "test.sqlite3"), true)
	if err != nil {
		t.Fatalf("Open: %v", err)
	}
	defer db.Close()

	if err := db.IntegrityCheck(); err != nil {
		t.Fatalf("IntegrityCheck: %v", err)
	}
}

func TestWALCheckpoint(t *testing.T) {
	dir := t.TempDir()
	db, err := Open(filepath.Join(dir, "test.sqlite3"), true)
	if err != nil {
		t.Fatalf("Open: %v", err)
	}
	defer db.Close()

	if err := db.WALCheckpointTruncate(); err != nil {
		t.Fatalf("WALCheckpointTruncate: %v", err)
	}
}

func TestOpenCreatesParentDir(t *testing.T) {
	dir := t.TempDir()
	path := filepath.Join(dir, "sub", "nested", "db.sqlite3")
	db, err := Open(path, true)
	if err != nil {
		t.Fatalf("Open with nested dir: %v", err)
	}
	db.Close()

	if _, err := os.Stat(path); os.IsNotExist(err) {
		t.Fatal("db file was not created")
	}
}
