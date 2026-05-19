package db

import (
	"database/sql"
	_ "embed"
	"fmt"
	"os"
	"path/filepath"
	"strings"

	_ "modernc.org/sqlite"
)

//go:embed migrations/001_init.sql
var migrationSQL string

type DB struct {
	pool *sql.DB
}

func Open(sqlitePath string, autoMigrate bool) (*DB, error) {
	if err := os.MkdirAll(filepath.Dir(sqlitePath), 0755); err != nil {
		return nil, fmt.Errorf("create db dir: %w", err)
	}

	pool, err := sql.Open("sqlite", sqlitePath+"?_journal_mode=WAL&_synchronous=NORMAL")
	if err != nil {
		return nil, fmt.Errorf("open sqlite: %w", err)
	}

	pool.SetMaxOpenConns(1) // SQLite serialized mode — single writer

	if _, err := pool.Exec("PRAGMA journal_mode=WAL"); err != nil {
		pool.Close()
		return nil, fmt.Errorf("set WAL: %w", err)
	}
	if _, err := pool.Exec("PRAGMA synchronous=NORMAL"); err != nil {
		pool.Close()
		return nil, fmt.Errorf("set synchronous: %w", err)
	}

	if autoMigrate {
		if err := migrate(pool, migrationSQL); err != nil {
			pool.Close()
			return nil, fmt.Errorf("migrate: %w", err)
		}
	}

	return &DB{pool: pool}, nil
}

func (db *DB) Ping() error {
	return db.pool.Ping()
}

func (db *DB) Close() error {
	return db.pool.Close()
}

func (db *DB) IntegrityCheck() error {
	var result string
	if err := db.pool.QueryRow("PRAGMA integrity_check").Scan(&result); err != nil {
		return fmt.Errorf("integrity_check: %w", err)
	}
	if !strings.EqualFold(result, "ok") {
		return fmt.Errorf("sqlite integrity_check failed: %s", result)
	}
	return nil
}

func (db *DB) WALCheckpointTruncate() error {
	if _, err := db.pool.Exec("PRAGMA wal_checkpoint(TRUNCATE)"); err != nil {
		return fmt.Errorf("wal_checkpoint truncate: %w", err)
	}
	return nil
}

func migrate(pool *sql.DB, sqlStr string) error {
	stmts := splitSQL(sqlStr)
	for _, stmt := range stmts {
		if stmt == "" {
			continue
		}
		if _, err := pool.Exec(stmt); err != nil {
			return fmt.Errorf("exec migration stmt: %w", err)
		}
	}
	return nil
}

func splitSQL(s string) []string {
	parts := strings.Split(s, ";")
	out := make([]string, 0, len(parts))
	for _, p := range parts {
		p = strings.TrimSpace(p)
		if p != "" {
			out = append(out, p)
		}
	}
	return out
}
