package org.openlist.encrypt.android.app

import android.app.Application
import org.openlist.encrypt.android.runtime.NativeBinaryInstaller

class OpenEncryptApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NativeBinaryInstaller(this).installIfPresent()
    }
}
