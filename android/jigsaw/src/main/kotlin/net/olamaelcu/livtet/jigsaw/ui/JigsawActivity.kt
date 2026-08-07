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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "Open a book first to start the jigsaw puzzle.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
