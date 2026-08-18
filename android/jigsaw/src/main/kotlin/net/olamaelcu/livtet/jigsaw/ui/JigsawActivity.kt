package net.olamaelcu.livtet.jigsaw.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import net.olamaelcu.livtet.jigsaw.BuildConfig
import net.olamaelcu.livtet.jigsaw.JigsawPuzzle
import net.olamaelcu.livtet.jigsaw.sound.PuzzleSoundPlayer

class JigsawActivity : ComponentActivity() {

    private lateinit var soundPlayer: PuzzleSoundPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        soundPlayer = PuzzleSoundPlayer(this)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PuzzleContainer(soundPlayer = soundPlayer)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        soundPlayer.release()
    }
}

@Composable
private fun PuzzleContainer(soundPlayer: PuzzleSoundPlayer) {
    if (BuildConfig.DEBUG) {
        DebugPuzzleContent(soundPlayer)
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "Open a book first to start the jigsaw puzzle.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DebugPuzzleContent(soundPlayer: PuzzleSoundPlayer) {
    LaunchedEffect(Unit) { soundPlayer.load() }

    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(Unit) {
        val request = ImageRequest.Builder(context).data(DEBUG_COVER_URL).build()
        val result = context.imageLoader.execute(request)
        bitmap = (result as? SuccessResult)?.image?.toBitmap()?.asImageBitmap()
    }

    JigsawPuzzle(image = bitmap, soundPlayer = soundPlayer)
}
