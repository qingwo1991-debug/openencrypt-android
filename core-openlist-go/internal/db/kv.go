package db

import "database/sql"

func (db *DB) GetRuntimeKV(key string) (string, bool, error) {
	var v string
	err := db.pool.QueryRow(
		"SELECT v FROM runtime_kv WHERE k = ? LIMIT 1", key,
	).Scan(&v)
	if err == sql.ErrNoRows {
		return "", false, nil
	}
	if err != nil {
		return "", false, err
	}
	return v, true, nil
}

func (db *DB) SetRuntimeKV(key, value string) error {
	_, err := db.pool.Exec(
		`INSERT INTO runtime_kv (k, v, updated_at) VALUES (?, ?, datetime('now'))
		 ON CONFLICT(k) DO UPDATE SET v = excluded.v, updated_at = datetime('now')`,
		key, value,
	)
	return err
}
