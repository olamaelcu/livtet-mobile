package net.olamaelcu.livtet

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.commitNow
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.olamaelcu.livtet.core.designsystem.LivtetTheme
import net.olamaelcu.livtet.core.reader.ReaderError
import net.olamaelcu.livtet.core.reader.ReaderSession
import net.olamaelcu.livtet.core.reader.ReadingPosition
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.publication.Locator

/**
 * Reading one edition.
 *
 * The screen takes an edition id and nothing else: it resolves that edition's `digital_inventory`
 * row for the file to point at, so a book that arrived from a peer is readable without the caller
 * knowing where the bytes landed.
 *
 * The toolkit's navigator is a `Fragment`, which is why this is a [FragmentActivity] hosting it
 * inside the Compose layout. The manifest declares the configuration changes this activity handles:
 * a `Publication` cannot be parcelled, so a rotation that rebuilt the activity would have to reopen
 * the book and lose the exact passage the reader was on.
 */
class ReaderActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val editionId = intent.getStringExtra(EXTRA_EDITION_ID)
        if (editionId.isNullOrBlank()) {
            finish()
            return
        }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        setContent {
            // No theme here: the reader owns its page's colours, so it wraps
            // itself once it knows which paper the reader chose.
            ReaderRoute(editionId = editionId, onBack = { finish() })
        }
    }

    companion object {
        private const val EXTRA_EDITION_ID = "net.olamaelcu.livtet.reader.EDITION_ID"

        /** Open [editionId]; the activity finds that edition's file itself. */
        fun intent(context: Context, editionId: String): Intent =
            Intent(context, ReaderActivity::class.java).putExtra(EXTRA_EDITION_ID, editionId)
    }
}

private sealed interface ReaderState {
    data object Loading : ReaderState

    data class Failed(val message: String) : ReaderState

    data class Ready(val title: String) : ReaderState
}

@Composable
private fun ReaderRoute(editionId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as FragmentActivity
    val scope = rememberCoroutineScope()

    var state: ReaderState by remember(editionId) { mutableStateOf(ReaderState.Loading) }
    var progression: Double by remember(editionId) { mutableStateOf(0.0) }
    // The reader's display choices, and whether they have been read yet: saving
    // before the load finishes would overwrite what is stored with the default.
    var display by remember { mutableStateOf(ReaderDisplay.Default) }
    var displayLoaded by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var navigator by remember { mutableStateOf<EpubNavigatorFragment?>(null) }
    var formatId: String? by remember(editionId) { mutableStateOf(null) }
    var lastSavedAt: Long by remember(editionId) { mutableLongStateOf(0L) }
    var attempt: Int by remember(editionId) { mutableStateOf(0) }

    LaunchedEffect(editionId, attempt) {
        state = ReaderState.Loading
        state =
            runCatching {
                    // Reopening after process death starts here, not at the
                    // launcher, so the store has to come up on demand.
                    if (!Bridge.isInitialized()) {
                        Bridge.init(File(context.filesDir, "livtet.db").absolutePath)
                    }
                    val detail = Bridge.editionDetail(editionId)
                    val path =
                        Bridge.editionFilePath(editionId)
                            ?: throw ReaderError.MissingFile(
                                "this edition has no file yet; accept it from a peer first"
                            )
                    val stored = Bridge.readingProgress(editionId)?.lastLocation
                    // Read before the factory, so the book opens in the type and
                    // paper it was left in rather than correcting itself after.
                    display = runCatching { Bridge.readerDisplay() }.getOrDefault(display)
                    displayLoaded = true
                    val session = ReaderSession.open(context, path)
                    val factory =
                        session.navigatorFactory
                            ?: throw ReaderError.Unsupported(path, "this file is not an EPUB")
                    formatId = Bridge.formatId(detail?.format ?: "epub") ?: Bridge.formatId("epub")
                    activity.supportFragmentManager.fragmentFactory =
                        factory.createFragmentFactory(
                            initialLocator = ReadingPosition.locator(stored),
                            initialPreferences = display.toPreferences(),
                        )
                    ReaderState.Ready(session.title ?: detail?.title ?: "Untitled")
                }
                .getOrElse { error ->
                    ReaderState.Failed(
                        when (error) {
                            is ReaderError -> error.message ?: "could not open this book"
                            else -> error.message ?: error.toString()
                        }
                    )
                }
    }

    // A change of display re-lays the page and is remembered for the next book;
    // waiting on `displayLoaded` keeps the first frame from saving the default
    // over whatever the reader actually chose last time.
    LaunchedEffect(display, displayLoaded) {
        if (!displayLoaded) return@LaunchedEffect
        navigator?.submitPreferences(display.toPreferences())
        runCatching { Bridge.saveReaderDisplay(display) }
    }

    // The reader paints its own page: sepia is not a brand surface, so the
    // scheme is chosen here rather than inherited from the launcher.
    LivtetTheme(
        darkTheme = display.theme == ReaderTheme.DARK,
        colorScheme = display.theme.colorScheme(),
    ) {
        // The page sits on the theme's own surface: the window background is the
        // brand color, which is right for a cold start and wrong behind a book.
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onBack) { Text("\u2039  Library") }
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = (state as? ReaderState.Ready)?.title.orEmpty(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = { showSettings = !showSettings }) {
                        Text("Aa", style = MaterialTheme.typography.labelLarge)
                    }
                    Text(
                        text = "${(progression * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    )
                }

                if (showSettings) {
                    ReaderDisplayPanel(display = display, onChange = { display = it })
                }

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when (val current = state) {
                        ReaderState.Loading ->
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                CircularProgressIndicator(strokeWidth = 3.dp)
                                Spacer(modifier = Modifier.padding(6.dp))
                                Text(
                                    text = "Opening\u2026",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color =
                                        MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                )
                            }

                        is ReaderState.Failed ->
                            Column(
                                modifier = Modifier.fillMaxSize().padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text(
                                    text = current.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                    textAlign = TextAlign.Center,
                                )
                                Spacer(modifier = Modifier.padding(6.dp))
                                Row {
                                    TextButton(onClick = onBack) { Text("Back") }
                                    TextButton(onClick = { attempt += 1 }) { Text("Try again") }
                                }
                            }

                        is ReaderState.Ready ->
                            AndroidView(
                                modifier = Modifier.fillMaxSize(),
                                factory = { viewContext ->
                                    FragmentContainerView(viewContext).apply {
                                        id = R.id.reader_host
                                        // `post` runs once the container is attached, which
                                        // is the first moment a fragment can go into it.
                                        post {
                                            installNavigator(
                                                activity = activity,
                                                scope = scope,
                                                onNavigator = { live -> navigator = live },
                                            ) { locator ->
                                                val fid = formatId
                                                val now = System.currentTimeMillis()
                                                val seconds =
                                                    if (lastSavedAt == 0L) {
                                                        0L
                                                    } else {
                                                        ((now - lastSavedAt) / 1000).coerceIn(
                                                            0,
                                                            300,
                                                        )
                                                    }
                                                lastSavedAt = now
                                                val position = ReadingPosition.of(locator)
                                                progression = position.progression
                                                if (fid == null) {
                                                    android.util.Log.w(
                                                        SAVE_TAG,
                                                        "no format row for this edition; position dropped",
                                                    )
                                                }
                                                if (fid != null) {
                                                    scope.launch {
                                                        try {
                                                            Bridge.recordProgress(
                                                                editionId = editionId,
                                                                formatId = fid,
                                                                progression = position.progression,
                                                                lastLocation = position.locatorJson,
                                                                additionalSeconds = seconds,
                                                            )
                                                        } catch (error: Exception) {
                                                            // The screen must keep rendering even
                                                            // when
                                                            // the write fails, but a dropped
                                                            // position
                                                            // is
                                                            // not silent: it is what sync carries.
                                                            android.util.Log.w(
                                                                SAVE_TAG,
                                                                "could not record the position",
                                                                error,
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                },
                            )
                    }
                }
            }
        }
    }
}

/**
 * Put the EPUB navigator in the container and report every position it takes.
 *
 * The factory the activity installed is what creates the fragment, so this only asks for it and
 * subscribes; each emission is the place to resume from, which is why a back-out loses nothing.
 */
private fun FragmentContainerView.installNavigator(
    activity: FragmentActivity,
    scope: CoroutineScope,
    onNavigator: (EpubNavigatorFragment) -> Unit,
    onLocator: (Locator) -> Unit,
) {
    val manager = activity.supportFragmentManager
    if (manager.findFragmentById(id) == null) {
        manager.commitNow {
            setReorderingAllowed(true)
            add(id, EpubNavigatorFragment::class.java, Bundle(), NAVIGATOR_TAG)
        }
    }
    val navigator = manager.findFragmentById(id) as? EpubNavigatorFragment ?: return
    // Handed back so display changes can reach it: preferences are a request to
    // the live navigator, not something a factory can change afterwards.
    onNavigator(navigator)
    scope.launch { navigator.currentLocator.collect { locator -> onLocator(locator) } }
}

private const val NAVIGATOR_TAG = "EpubNavigatorFragment"
private const val SAVE_TAG = "ReaderActivity"

/**
 * The display choices, inline rather than modal.
 *
 * A sheet over the page would hide the thing being adjusted: every change here is visible behind
 * the panel while it is being made, which is the only way to choose type size and margins sensibly.
 */
@Composable
private fun ReaderDisplayPanel(display: ReaderDisplay, onChange: (ReaderDisplay) -> Unit) {
    OutlinedCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Text",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = { onChange(display.smallerText()) }) { Text("A\u2212") }
                TextButton(onClick = { onChange(display.largerText()) }) { Text("A+") }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "${(display.textScale * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Paper",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                ReaderTheme.entries.forEach { theme ->
                    val selected = display.theme == theme
                    TextButton(onClick = { onChange(display.withTheme(theme)) }) {
                        Text(
                            text = theme.name.lowercase().replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.labelMedium,
                            color =
                                if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                },
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Margins",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                ReaderDisplay.MARGIN_STEPS.indices.forEach { step ->
                    val selected = display.marginStep == step
                    TextButton(onClick = { onChange(display.withMargins(step)) }) {
                        Text(
                            text = MARGIN_LABELS[step],
                            style = MaterialTheme.typography.labelMedium,
                            color =
                                if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                },
                        )
                    }
                }
            }
        }
    }
}

private val MARGIN_LABELS = listOf("Narrow", "Normal", "Wide")
