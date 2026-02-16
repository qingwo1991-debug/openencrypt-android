use std::path::Path;

use anyhow::{anyhow, Result};
use sqlx::{sqlite::SqliteConnectOptions, sqlite::SqlitePoolOptions, ConnectOptions, SqlitePool};

use crate::config::TimeoutProfile;

#[derive(Clone)]
pub struct Db {
    pool: SqlitePool,
}

impl Db {
    pub async fn connect(sqlite_path: &str, auto_migrate: bool) -> Result<Self> {
        if let Some(parent) = Path::new(sqlite_path).parent() {
            std::fs::create_dir_all(parent)?;
        }

        let opts = SqliteConnectOptions::new()
            .filename(sqlite_path)
            .create_if_missing(true)
            .log_statements(tracing::log::LevelFilter::Off);

        let pool = SqlitePoolOptions::new()
            .max_connections(10)
            .connect_with(opts)
            .await?;

        sqlx::query("PRAGMA journal_mode=WAL;")
            .execute(&pool)
            .await?;
        sqlx::query("PRAGMA synchronous=NORMAL;")
            .execute(&pool)
            .await?;

        if auto_migrate {
            let sql = include_str!("../migrations/001_init.sql");
            run_bootstrap_sql(&pool, sql).await?;
        }

        Ok(Self { pool })
    }

    pub async fn ping(&self) -> Result<()> {
        sqlx::query("SELECT 1").execute(&self.pool).await?;
        Ok(())
    }

    pub async fn integrity_check(&self) -> Result<()> {
        let row: (String,) = sqlx::query_as("PRAGMA integrity_check;")
            .fetch_one(&self.pool)
            .await?;
        if row.0.eq_ignore_ascii_case("ok") {
            Ok(())
        } else {
            Err(anyhow!("sqlite integrity_check failed: {}", row.0))
        }
    }

    pub async fn wal_checkpoint_truncate(&self) -> Result<()> {
        sqlx::query("PRAGMA wal_checkpoint(TRUNCATE);")
            .execute(&self.pool)
            .await?;
        Ok(())
    }

    pub async fn get_runtime_kv(&self, key: &str) -> Result<Option<String>> {
        let row: Option<(String,)> = sqlx::query_as("SELECT v FROM runtime_kv WHERE k = ? LIMIT 1")
            .bind(key)
            .fetch_optional(&self.pool)
            .await?;
        Ok(row.map(|(v,)| v))
    }

    pub async fn set_runtime_kv(&self, key: &str, value: &str) -> Result<()> {
        sqlx::query(
            "INSERT INTO runtime_kv (k, v, updated_at) VALUES (?, ?, datetime('now')) \
             ON CONFLICT(k) DO UPDATE SET v = excluded.v, updated_at = datetime('now')",
        )
        .bind(key)
        .bind(value)
        .execute(&self.pool)
        .await?;
        Ok(())
    }

    pub async fn load_timeout_profile(
        &self,
        tenant_id: &str,
        iface_name: &str,
    ) -> Result<Option<TimeoutProfile>> {
        let row: Option<(i64, i64, i64, i64)> = sqlx::query_as(
            "SELECT connect_ms, ttfb_ms, read_idle_ms, total_ms \
             FROM timeout_profiles \
             WHERE tenant_id = ? AND iface_name = ? AND enabled = 1 \
             LIMIT 1",
        )
        .bind(tenant_id)
        .bind(iface_name)
        .fetch_optional(&self.pool)
        .await?;

        Ok(row.map(|(a, b, c, d)| TimeoutProfile {
            connect_ms: a.max(0) as u64,
            ttfb_ms: b.max(0) as u64,
            read_idle_ms: c.max(0) as u64,
            total_ms: d.max(0) as u64,
        }))
    }

    pub async fn upsert_timeout_profile(
        &self,
        tenant_id: &str,
        iface_name: &str,
        profile: TimeoutProfile,
    ) -> Result<()> {
        sqlx::query(
            "INSERT INTO timeout_profiles \
             (tenant_id, iface_name, connect_ms, ttfb_ms, read_idle_ms, total_ms, enabled, updated_at) \
             VALUES (?, ?, ?, ?, ?, ?, 1, datetime('now')) \
             ON CONFLICT(tenant_id, iface_name) DO UPDATE SET \
             connect_ms = excluded.connect_ms, \
             ttfb_ms = excluded.ttfb_ms, \
             read_idle_ms = excluded.read_idle_ms, \
             total_ms = excluded.total_ms, \
             enabled = 1, \
             updated_at = datetime('now')",
        )
        .bind(tenant_id)
        .bind(iface_name)
        .bind(profile.connect_ms as i64)
        .bind(profile.ttfb_ms as i64)
        .bind(profile.read_idle_ms as i64)
        .bind(profile.total_ms as i64)
        .execute(&self.pool)
        .await?;
        Ok(())
    }
}

async fn run_bootstrap_sql(pool: &SqlitePool, sql: &str) -> Result<()> {
    for stmt in split_sql_statements(sql) {
        if stmt.trim().is_empty() {
            continue;
        }
        sqlx::query(stmt).execute(pool).await?;
    }
    Ok(())
}

fn split_sql_statements(sql: &str) -> Vec<&str> {
    sql.split(';')
        .map(str::trim)
        .filter(|s| !s.is_empty())
        .collect()
}

#[cfg(test)]
mod tests {
    use super::split_sql_statements;

    #[test]
    fn split_sql_statements_basic() {
        let sql = "SELECT 1;\n\nSELECT 2;\n";
        let stmts = split_sql_statements(sql);
        assert_eq!(stmts, vec!["SELECT 1", "SELECT 2"]);
    }
}
