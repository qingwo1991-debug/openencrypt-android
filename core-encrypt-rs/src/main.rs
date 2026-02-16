mod config;
mod db;

use std::{collections::HashMap, sync::Arc};

use axum::{
    extract::{Path, State},
    http::StatusCode,
    routing::get,
    Json, Router,
};
use config::{AppConfig, TimeoutProfile};
use db::Db;
use tokio::sync::RwLock;
use tracing::{info, warn};

#[derive(Clone)]
struct AppState {
    db: Arc<Db>,
    runtime_cache: Arc<RwLock<HashMap<String, String>>>,
}

#[tokio::main]
async fn main() {
    tracing_subscriber::fmt()
        .with_env_filter(
            std::env::var("RUST_LOG")
                .unwrap_or_else(|_| "info,openencrypt_gateway=debug".to_string()),
        )
        .init();

    let cfg = AppConfig::from_env();
    let db = match Db::connect(&cfg.sqlite_path, cfg.auto_migrate).await {
        Ok(db) => db,
        Err(e) => {
            eprintln!("failed to init sqlite db: {e}");
            std::process::exit(1);
        }
    };

    let state = AppState {
        db: Arc::new(db),
        runtime_cache: Arc::new(RwLock::new(HashMap::new())),
    };

    let app = Router::new()
        .route("/healthz", get(healthz))
        .route(
            "/v2/admin/runtime-kv/:key",
            get(get_runtime_kv).put(put_runtime_kv),
        )
        .route(
            "/v2/admin/timeout-profiles/:iface_name",
            get(get_timeout_profile).put(put_timeout_profile),
        )
        .route("/v2/admin/db/integrity", get(get_db_integrity))
        .route("/v2/admin/db/checkpoint", get(checkpoint_db))
        .with_state(state);

    let listener = tokio::net::TcpListener::bind(&cfg.listen_addr)
        .await
        .expect("bind failed");
    info!(listen = %cfg.listen_addr, db = %cfg.sqlite_path, "openencrypt gateway started");

    if let Err(e) = axum::serve(listener, app)
        .with_graceful_shutdown(shutdown_signal())
        .await
    {
        warn!(error = %e, "server exited with error");
    }
}

async fn healthz(State(state): State<AppState>) -> (StatusCode, Json<serde_json::Value>) {
    let db_ok = state.db.ping().await.is_ok();
    (
        StatusCode::OK,
        Json(serde_json::json!({
            "status": "ok",
            "db": db_ok
        })),
    )
}

async fn get_db_integrity(
    State(state): State<AppState>,
) -> Result<Json<serde_json::Value>, (StatusCode, String)> {
    state
        .db
        .integrity_check()
        .await
        .map_err(|e| (StatusCode::INTERNAL_SERVER_ERROR, e.to_string()))?;
    Ok(Json(serde_json::json!({"ok": true})))
}

async fn checkpoint_db(
    State(state): State<AppState>,
) -> Result<Json<serde_json::Value>, (StatusCode, String)> {
    state
        .db
        .wal_checkpoint_truncate()
        .await
        .map_err(|e| (StatusCode::INTERNAL_SERVER_ERROR, e.to_string()))?;
    Ok(Json(
        serde_json::json!({"ok": true, "checkpoint": "TRUNCATE"}),
    ))
}

async fn get_runtime_kv(
    State(state): State<AppState>,
    Path(key): Path<String>,
) -> Result<Json<serde_json::Value>, (StatusCode, String)> {
    match state.db.get_runtime_kv(&key).await {
        Ok(v) => Ok(Json(serde_json::json!({"key": key, "value": v}))),
        Err(e) => Err((StatusCode::INTERNAL_SERVER_ERROR, e.to_string())),
    }
}

async fn put_runtime_kv(
    State(state): State<AppState>,
    Path(key): Path<String>,
    Json(payload): Json<serde_json::Value>,
) -> Result<Json<serde_json::Value>, (StatusCode, String)> {
    let value = payload
        .get("value")
        .and_then(|v| v.as_str())
        .ok_or_else(|| {
            (
                StatusCode::BAD_REQUEST,
                "missing 'value' string".to_string(),
            )
        })?;

    state
        .db
        .set_runtime_kv(&key, value)
        .await
        .map_err(|e| (StatusCode::INTERNAL_SERVER_ERROR, e.to_string()))?;

    state
        .runtime_cache
        .write()
        .await
        .insert(key.clone(), value.to_string());

    Ok(Json(serde_json::json!({"ok": true, "key": key})))
}

async fn get_timeout_profile(
    State(state): State<AppState>,
    Path(iface_name): Path<String>,
) -> Result<Json<serde_json::Value>, (StatusCode, String)> {
    let tenant_id = "default";
    let profile = state
        .db
        .load_timeout_profile(tenant_id, &iface_name)
        .await
        .map_err(|e| (StatusCode::INTERNAL_SERVER_ERROR, e.to_string()))?;

    Ok(Json(
        serde_json::json!({"tenant_id": tenant_id, "iface_name": iface_name, "profile": profile}),
    ))
}

async fn put_timeout_profile(
    State(state): State<AppState>,
    Path(iface_name): Path<String>,
    Json(profile): Json<TimeoutProfile>,
) -> Result<Json<serde_json::Value>, (StatusCode, String)> {
    let tenant_id = "default";
    state
        .db
        .upsert_timeout_profile(tenant_id, &iface_name, profile)
        .await
        .map_err(|e| (StatusCode::INTERNAL_SERVER_ERROR, e.to_string()))?;

    Ok(Json(
        serde_json::json!({"ok": true, "tenant_id": tenant_id, "iface_name": iface_name}),
    ))
}

async fn shutdown_signal() {
    let ctrl_c = async {
        tokio::signal::ctrl_c()
            .await
            .expect("failed to install Ctrl+C signal handler");
    };

    #[cfg(unix)]
    let terminate = async {
        use tokio::signal::unix::{signal, SignalKind};
        let mut sigterm =
            signal(SignalKind::terminate()).expect("failed to install SIGTERM handler");
        sigterm.recv().await;
    };

    #[cfg(not(unix))]
    let terminate = std::future::pending::<()>();

    tokio::select! {
        _ = ctrl_c => {},
        _ = terminate => {},
    }
}
