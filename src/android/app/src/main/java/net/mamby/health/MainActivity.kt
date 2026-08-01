package net.mamby.health

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import net.mamby.health.navigation.DeepLinkCoordinator
import net.mamby.health.security.AppLockWindowProtector
import net.mamby.health.ui.HealthVaultApp

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject
    lateinit var deepLinkCoordinator: DeepLinkCoordinator

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AppLockWindowProtector.protect(window)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        deepLinkCoordinator.accept(intent)

        setContent { HealthVaultApp() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deepLinkCoordinator.accept(intent)
    }
}
