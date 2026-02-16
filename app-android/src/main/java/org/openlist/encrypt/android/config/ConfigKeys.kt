package org.openlist.encrypt.android.config

object ConfigKeys {
    const val OPENLIST_HOST = "openlist.host"
    const val OPENLIST_PORT = "openlist.port"
    const val GATEWAY_PORT = "gateway.port"
    const val ENABLE_PARALLEL_DECRYPT = "gateway.enable_parallel_decrypt"
    const val PARALLEL_DECRYPT_CONCURRENCY = "gateway.parallel_decrypt_concurrency"
    const val PROBE_TIMEOUT_SECONDS = "runtime.probe_timeout_seconds"
    const val UPSTREAM_TIMEOUT_SECONDS = "runtime.upstream_timeout_seconds"
    const val UPSTREAM_BACKOFF_SECONDS = "runtime.upstream_backoff_seconds"
}
