#[derive(Clone, Debug)]
pub struct AppConfig {
    pub listen_addr: String,
    pub sqlite_path: String,
    pub auto_migrate: bool,
}

impl AppConfig {
    pub fn from_env() -> Self {
        Self {
            listen_addr: std::env::var("LISTEN_ADDR")
                .unwrap_or_else(|_| "0.0.0.0:5344".to_string()),
            sqlite_path: std::env::var("SQLITE_PATH")
                .unwrap_or_else(|_| "./data/openencrypt.sqlite3".to_string()),
            auto_migrate: std::env::var("AUTO_MIGRATE")
                .map(|v| matches!(v.as_str(), "1" | "true" | "TRUE"))
                .unwrap_or(true),
        }
    }
}

#[derive(Clone, Debug, serde::Serialize, serde::Deserialize)]
pub struct TimeoutProfile {
    pub connect_ms: u64,
    pub ttfb_ms: u64,
    pub read_idle_ms: u64,
    pub total_ms: u64,
}
