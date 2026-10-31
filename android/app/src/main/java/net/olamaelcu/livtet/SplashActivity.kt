package net.olamaelcu.livtet

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import net.olamaelcu.livtet.branding.LivtetColors
import net.olamaelcu.livtet.core.designsystem.LivtetTheme
import net.olamaelcu.livtet.settings.ThemeManager

class SplashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val deepLink = intent?.dataString?.takeIf { AppLinkRouter.canHandle(it) }
        // A warm start already has the store open, so an app link can skip the
        // splash entirely. A cold start must still run `Bridge.init` first:
        // the dashboard's screens read the store as soon as they compose.
        if (deepLink != null && Bridge.isInitialized()) {
            AppLinkRouter.handle(deepLink)
            openDashboard(deepLink)
            return
        }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        setContent {
            val themeMode by
                ThemeManager.mode(this).collectAsState(initial = ThemeManager.Mode.SYSTEM)
            val isDark =
                when (themeMode) {
                    ThemeManager.Mode.SYSTEM -> isSystemInDarkTheme()
                    ThemeManager.Mode.LIGHT -> false
                    ThemeManager.Mode.DARK -> true
                }
            LivtetTheme(darkTheme = isDark) {
                SplashContent(
                    context = this,
                    databasePath = File(filesDir, "livtet.db").absolutePath,
                    onComplete = { openDashboard(deepLink) },
                )
            }
        }
    }

    /**
     * Hands the dashboard the link (if any) and finishes the splash.
     *
     * With a link the dashboard is reused when it is already on the stack, so the app-link handler
     * there replaces the current screen instead of piling a second dashboard underneath it. Without
     * one this is a fresh start, so the existing task is cleared.
     */
    private fun openDashboard(deepLink: String?) {
        val intent =
            Intent(this, DashboardActivity::class.java).apply {
                if (deepLink != null) {
                    data = Uri.parse(deepLink)
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                } else {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
            }
        startActivity(intent)
        finish()
    }
}

@Composable
private fun SplashContent(
    context: android.content.Context,
    databasePath: String,
    onComplete: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(id = R.drawable.logo),
            contentDescription = "Livtet",
            modifier = Modifier.size(96.dp),
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Livtet",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(modifier = Modifier.height(32.dp))

        CircularProgressIndicator(color = LivtetColors.Brand, strokeWidth = 3.dp)

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Loading your library\u2026",
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
        )
    }

    LaunchedEffect(Unit) {
        Bridge.init(databasePath)
        onComplete()
    }
}
