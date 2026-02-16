package org.openlist.encrypt.android.service

enum class RuntimeState {
    Idle,
    Starting,
    Running,
    Degraded,
    Recovering,
    Stopped
}
