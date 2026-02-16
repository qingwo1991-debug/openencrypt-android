package org.openlist.encrypt.android.app

import android.app.Application
import org.openlist.encrypt.android.runtime.NativeBinaryInstaller
import org.openlist.encrypt.android.ui.ThemeModeStore

class OpenEncryptApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ThemeModeStore.apply(this)
        NativeBinaryInstaller(this).installIfPresent()
    }
}
