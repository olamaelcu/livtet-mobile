package net.olamaelcu.livtet.jigsaw.hint

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
fun HintOverlay(
    hintsRemaining: Int,
    onHighlight: () -> Unit,
    onReveal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HintButton(icon = Icons.Filled.Lightbulb, label = "Highlight",
            enabled = hintsRemaining > 0, onClick = onHighlight)
        HintButton(icon = Icons.Filled.Visibility, label = "Reveal",
            enabled = hintsRemaining > 0, onClick = onReveal)
        BadgedBox(badge = { Badge { Text(hintsRemaining.toString()) } }) {
            Text(text = if (hintsRemaining > 0) "$hintsRemaining hints" else "No hints",
                modifier = Modifier.padding(8.dp))
        }
    }
}

@Composable
private fun HintButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    FloatingActionButton(onClick = onClick, modifier = Modifier) {
        Icon(imageVector = icon, contentDescription = label)
    }
}
