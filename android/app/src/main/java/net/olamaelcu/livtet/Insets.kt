package net.olamaelcu.livtet

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The top safe area for a screen header.
 *
 * `statusBarsPadding()` reports the status bar alone, which on a phone with a centred camera is
 * shorter than the cutout: the header then starts under the lens. Safe drawing takes the larger of
 * the status bar and the cutout, and the horizontal component covers the cutout in landscape.
 */
@Composable
fun Modifier.appHeaderInset(): Modifier =
    windowInsetsPadding(
        WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    )
