package net.olamaelcu.livtet

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import java.io.File
import kotlinx.coroutines.launch
import net.olamaelcu.livtet.core.designsystem.LivtetTheme

/**
 * Named reading lists.
 *
 * The store has had reading lists since the beginning — with membership positions, not just a set —
 * and nothing showed them. This is the screen for the ones a reader makes themselves: "Winter",
 * "For the train", rather than the four status values the library already groups by.
 */
class ShelvesActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        setContent {
            LivtetTheme(darkTheme = isSystemInDarkTheme()) { ShelvesRoute(onBack = { finish() }) }
        }
    }

    companion object {
        fun intent(context: Context): Intent = Intent(context, ShelvesActivity::class.java)
    }
}

/** A shelf with its members' titles resolved, so the screen never shows raw ids. */
private data class ShelfView(
    val id: String,
    val name: String,
    val entries: List<Pair<String, String>>,
)

private sealed interface ShelvesState {
    data object Loading : ShelvesState

    data class Ready(val shelves: List<ShelfView>) : ShelvesState

    data class Failed(val message: String) : ShelvesState
}

@Composable
private fun ShelvesRoute(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state: ShelvesState by remember { mutableStateOf(ShelvesState.Loading) }
    var reloads by remember { mutableIntStateOf(0) }
    var newName by remember { mutableStateOf("") }
    var failure by remember { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf<ShelfView?>(null) }
    var deleting by remember { mutableStateOf<ShelfView?>(null) }

    LaunchedEffect(reloads) {
        if (!Bridge.isInitialized()) {
            Bridge.init(File(context.filesDir, "livtet.db").absolutePath)
        }
        state =
            runCatching {
                    val titles = mutableMapOf<String, String>()
                    Bridge.listShelves().map { shelf ->
                        val entries =
                            shelf.editionIds.map { editionId ->
                                val title =
                                    titles.getOrPut(editionId) {
                                        Bridge.editionDetail(editionId)?.title ?: "Unknown book"
                                    }
                                editionId to title
                            }
                        ShelfView(id = shelf.id, name = shelf.name, entries = entries)
                    }
                }
                .fold(
                    onSuccess = { ShelvesState.Ready(it) },
                    onFailure = { ShelvesState.Failed(it.message ?: it.toString()) },
                )
    }

    fun create() {
        val name = newName.trim()
        if (name.isEmpty()) return
        scope.launch {
            runCatching { Bridge.createShelf(name) }
                .onSuccess {
                    newName = ""
                    reloads++
                }
                .onFailure { failure = it.message }
        }
    }

    fun rename(shelf: ShelfView, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed == shelf.name) {
            renaming = null
            return
        }
        scope.launch {
            runCatching { Bridge.renameShelf(shelf.id, trimmed) }
                .onSuccess { reloads++ }
                .onFailure { failure = it.message }
            renaming = null
        }
    }

    fun delete(shelf: ShelfView) {
        scope.launch {
            runCatching { Bridge.deleteShelf(shelf.id) }
                .onSuccess { reloads++ }
                .onFailure { failure = it.message }
            deleting = null
        }
    }

    fun removeFrom(shelf: ShelfView, editionId: String) {
        scope.launch {
            runCatching { Bridge.removeFromShelf(shelf.id, editionId) }
                .onSuccess { reloads++ }
                .onFailure { failure = it.message }
        }
    }

    renaming?.let { shelf ->
        RenameDialog(
            shelf = shelf,
            onDismiss = { renaming = null },
            onRename = { rename(shelf, it) },
        )
    }
    deleting?.let { shelf ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete this shelf?") },
            text = {
                Text("\u201c${shelf.name}\u201d goes away. The books on it stay in your library.")
            },
            confirmButton = { TextButton(onClick = { delete(shelf) }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Keep") } },
        )
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().appHeaderInset()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack) { Text("\u2039  Back") }
                Spacer(modifier = Modifier.weight(1f))
                Text("Shelves", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.padding(end = 8.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("New shelf") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                )
                TextButton(onClick = ::create, enabled = newName.isNotBlank()) { Text("Create") }
            }

            failure?.let {
                Text(
                    text = it,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            when (val current = state) {
                ShelvesState.Loading ->
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(strokeWidth = 3.dp)
                    }

                is ShelvesState.Failed ->
                    Text(
                        text = current.message,
                        modifier = Modifier.padding(24.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )

                is ShelvesState.Ready ->
                    if (current.shelves.isEmpty()) {
                        Text(
                            text =
                                "No shelves yet. Make one for the books that belong together " +
                                    "\u2014 a season, a mood, a trip.",
                            modifier = Modifier.padding(24.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        )
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(current.shelves, key = { it.id }) { shelf ->
                                ShelfRow(
                                    shelf = shelf,
                                    onRename = { renaming = shelf },
                                    onDelete = { deleting = shelf },
                                    onRemove = { removeFrom(shelf, it) },
                                )
                            }
                        }
                    }
            }
        }
    }
}

@Composable
private fun ShelfRow(
    shelf: ShelfView,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onRemove: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f).clickable { expanded = !expanded }) {
                Text(
                    text = shelf.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text =
                        when (shelf.entries.size) {
                            0 -> "empty"
                            1 -> "1 book"
                            else -> "${shelf.entries.size} books"
                        },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                )
            }
            TextButton(onClick = onRename) { Text("Rename") }
            TextButton(onClick = onDelete) { Text("Delete") }
        }
        if (expanded) {
            shelf.entries.forEach { (editionId, title) ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 8.dp, top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = title,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    TextButton(onClick = { onRemove(editionId) }) { Text("Remove") }
                }
            }
            if (shelf.entries.isEmpty()) {
                Text(
                    text = "Add books from the library.",
                    modifier = Modifier.padding(start = 8.dp, top = 6.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                )
            }
        }
    }
}

@Composable
private fun RenameDialog(shelf: ShelfView, onDismiss: () -> Unit, onRename: (String) -> Unit) {
    var name by remember { mutableStateOf(shelf.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename shelf") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text("Name") },
            )
        },
        confirmButton = { TextButton(onClick = { onRename(name) }) { Text("Rename") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * Pick a shelf for books chosen in the library's bulk toolbar.
 *
 * Separate from [AddToShelfDialog], which files one edition as it opens: a bulk action has already
 * chosen the books, so all this needs is the shelf's name.
 */
@Composable
internal fun BulkShelfDialog(count: Int, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    var shelves by remember { mutableStateOf<List<Bridge.Shelf>?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        shelves =
            runCatching { Bridge.listShelves() }
                .onFailure { failure = it.message }
                .getOrDefault(emptyList())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (count == 1) "Shelve this book" else "Shelve $count books") },
        text = {
            Column {
                failure?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                val current = shelves
                when {
                    current == null -> CircularProgressIndicator(strokeWidth = 2.dp)
                    current.isEmpty() ->
                        Text(
                            text = "No shelves yet. Make one from Shelves first.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        )
                    else ->
                        current.forEach { shelf ->
                            TextButton(
                                onClick = { onPick(shelf.id) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(shelf.name, modifier = Modifier.fillMaxWidth())
                            }
                        }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
