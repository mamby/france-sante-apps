package net.mamby.health.security

import android.view.Window
import android.view.WindowManager

object AppLockWindowProtector {
    fun protect(window: Window) {
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}
