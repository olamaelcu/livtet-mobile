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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import org.readium.r2.navigator.Selection
import org.readium.r2.navigator.Decoration
import org.readium.r2.navigator.DecorableNavigator
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.commitNow
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import net.olamaelcu.livtet.core.designsystem.LivtetTheme
import net.olamaelcu.livtet.core.reader.ReaderError
import net.olamaelcu.livtet.core.reader.ReaderSession
import net.olamaelcu.livtet.core.reader.ReadingPosition
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.publication.Link
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
    var currentHref: String? by remember(editionId) { mutableStateOf(null) }
    // The reader's display choices, and whether they have been read yet: saving
    // before the load finishes would overwrite what is stored with the default.
    var display by remember { mutableStateOf(ReaderDisplay.Default) }
    var displayLoaded by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var navigator by remember { mutableStateOf<EpubNavigatorFragment?>(null) }
    var session by remember { mutableStateOf<ReaderSession?>(null) }
    var showContents by remember { mutableStateOf(false) }
    var showNotes by remember { mutableStateOf(false) }
    var notes by remember { mutableStateOf<List<Bridge.AnnotationView>>(emptyList()) }
    var openNote by remember { mutableStateOf<Bridge.AnnotationView?>(null) }
    var selection by remember { mutableStateOf<Selection?>(null) }
    var pendingNoteLocation by remember { mutableStateOf<String?>(null) }
    var writingNote by remember { mutableStateOf<String?>(null) }
    var noteFailure by remember { mutableStateOf<String?>(null) }
    var formatId: String? by remember(editionId) { mutableStateOf(null) }

    suspend fun reloadNotes() {
        notes =
            runCatching { Bridge.listAnnotations(editionId) }
                .onFailure { noteFailure = it.message }
                .getOrDefault(emptyList())
    }
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
                    reloadNotes()
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
                    val opened = ReaderSession.open(context, path)
                    session = opened
                    val factory =
                        opened.navigatorFactory
                            ?: throw ReaderError.Unsupported(path, "this file is not an EPUB")
                    formatId = Bridge.formatId(detail?.format ?: "epub") ?: Bridge.formatId("epub")
                    activity.supportFragmentManager.fragmentFactory =
                        factory.createFragmentFactory(
                            initialLocator = ReadingPosition.locator(stored),
                            initialPreferences = display.toPreferences(),
                        )
                    ReaderState.Ready(opened.title ?: detail?.title ?: "Untitled")
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

    // Readium exposes selection as a one-shot call, so a visible reader polls it and
    // offers to turn the selection into a note. The note then stores a quote locator,
    // which is what the decoration resolver can turn back into a DOM range.
    LaunchedEffect(navigator) {
        val live = navigator ?: return@LaunchedEffect
        while (isActive) {
            selection = runCatching { live.currentSelection() }.getOrNull()
            delay(250)
        }
    }

    LaunchedEffect(navigator, notes) {
        val live = navigator ?: return@LaunchedEffect
        // A decoration applied before the page publishes a location lands before the
        // page exists and is dropped.
        runCatching { live.currentLocator.first() }
        val decorations =
            notes.mapNotNull { note ->
                val locator = ReadingPosition.locator(note.location) ?: return@mapNotNull null
                // Progress-only locators have no element to tint; the resolver would log
                // and draw nothing. Only quote-bearing locators become decorations.
                if (locator.text?.highlight.isNullOrBlank()) return@mapNotNull null
                Decoration(note.id, locator, Decoration.Style.Highlight(NOTE_TINT))
            }
        live.applyDecorations(decorations, NOTE_GROUP)
    }

    DisposableEffect(navigator) {
        val live = navigator
            ?: return@DisposableEffect onDispose {}
        val listener =
            object : DecorableNavigator.Listener {
                override fun onDecorationActivated(
                    event: DecorableNavigator.OnActivatedEvent
                ): Boolean {
                    val note =
                        notes.firstOrNull { it.id == event.decoration.id } ?: return false
                    openNote = note
                    return true
                }
            }
        live.addDecorationListener(NOTE_GROUP, listener)
        onDispose { live.removeDecorationListener(listener) }
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
            Column(modifier = Modifier.fillMaxSize().appHeaderInset()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // The reader is reached from the home shelf and from the library,
                    // so the way out is named for the action rather than a screen.
                    TextButton(onClick = onBack) { Text("\u2039  Back") }
                    Text(
                        text = (state as? ReaderState.Ready)?.title.orEmpty(),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    TextButton(onClick = { showNotes = true }) {
                        Text(if (notes.isEmpty()) "Notes" else "Notes ${notes.size}")
                    }
                    TextButton(onClick = { showContents = true }) { Text("TOC") }
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

                if (showContents) {
                    ContentsDialog(
                        links = session?.publication?.tableOfContents.orEmpty(),
                        onOpen = { link ->
                            showContents = false
                            navigator?.go(link, animated = true)
                        },
                        onDismiss = { showContents = false },
                    )
                }

                if (showNotes) {
                    NotesSheet(
                        notes = notes,
                        chapterHref = currentHref,
                        chapterTitleFor = { href ->
                            chapterTitle(
                                href,
                                session?.publication?.tableOfContents.orEmpty(),
                            )
                        },
                        failure = noteFailure,
                        onOpen = { note ->
                            showNotes = false
                            openNote = note
                        },
                        onAdd = {
                            showNotes = false
                            writingNote = ""
                        },
                        onDelete = { note ->
                            scope.launch {
                                runCatching { Bridge.deleteAnnotation(note.id) }
                                    .onSuccess { reloadNotes() }
                                    .onFailure { noteFailure = it.message }
                            }
                        },
                        onDismiss = {
                            showNotes = false
                            noteFailure = null
                        },
                    )
                }

                openNote?.let { note ->
                    NotePreviewSheet(
                        note = note,
                        chapter =
                            chapterTitle(
                                ReadingPosition.locator(note.location)?.href?.toString(),
                                session?.publication?.tableOfContents.orEmpty(),
                            ),
                        onJump = {
                            ReadingPosition.locator(note.location)?.let { locator ->
                                navigator?.go(locator, animated = true)
                            }
                            openNote = null
                        },
                        onDelete = {
                            scope.launch {
                                runCatching { Bridge.deleteAnnotation(note.id) }
                                    .onSuccess {
                                        openNote = null
                                        reloadNotes()
                                    }
                                    .onFailure { noteFailure = it.message }
                            }
                        },
                        onDismiss = { openNote = null },
                    )
                }

                writingNote?.let { draft ->
                    WriteNoteDialog(
                        draft = draft,
                        onChange = { writingNote = it },
                        onSave = { content ->
                            scope.launch {
                                val location =
                                    pendingNoteLocation
                                        ?: navigator?.currentLocator?.value?.let {
                                            ReadingPosition.of(it).locatorJson
                                        }
                                runCatching { Bridge.addAnnotation(editionId, content, location) }
                                    .onSuccess {
                                        pendingNoteLocation = null
                                        writingNote = null
                                        reloadNotes()
                                    }
                                    .onFailure { noteFailure = it.message }
                            }
                        },
                        onDismiss = {
                            writingNote = null
                            pendingNoteLocation = null
                        },
                    )
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
                                                currentHref = locator.href.toString()
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

                    selection?.let { selected ->
                        val rect = selected.rect ?: return@let
                        Surface(
                            modifier =
                                Modifier.offset {
                                    IntOffset(
                                        rect.centerX().roundToInt() - 56,
                                        (rect.top - 56).roundToInt().coerceAtLeast(0),
                                    )
                                },
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.primary,
                        ) {
                            TextButton(
                                onClick = {
                                    pendingNoteLocation = selected.locator.toJSON().toString()
                                    navigator?.clearSelection()
                                    selection = null
                                    writingNote = ""
                                }
                            ) {
                                Text(
                                    text = "Add note",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            }
                        }
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

/**
 * The book's contents.
 *
 * Readium's navigator exposes the table of contents but renders none of it, and a reader who wants
 * chapter nine should not have to drag through four chapters to find it. Links are flattened one
 * level: the toolkit's TOC is a tree, and a dialog that cannot scroll sideways shows depth with
 * indentation instead.
 */
@Composable
private fun ContentsDialog(links: List<Link>, onOpen: (Link) -> Unit, onDismiss: () -> Unit) {
    data class Row(val link: Link, val depth: Int, val label: String)

    fun flatten(link: Link, depth: Int): List<Row> {
        val label =
            link.title?.takeIf { it.isNotBlank() }
                ?: link.href.toString().substringAfterLast('/').substringBefore('.').ifBlank {
                    "Untitled section"
                }
        return listOf(Row(link, depth, label)) + link.children.flatMap { flatten(it, depth + 1) }
    }

    val rows = links.flatMap { flatten(it, 0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Contents") },
        text = {
            if (rows.isEmpty()) {
                Text(
                    text = "This book has no table of contents.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                )
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    items(rows.size) { index ->
                        val row = rows[index]
                        TextButton(
                            onClick = { onOpen(row.link) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = row.label,
                                modifier =
                                    Modifier.fillMaxWidth().padding(start = (row.depth * 12).dp),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

/** The group every note decoration is applied under. */
private const val NOTE_GROUP = "notes"

/** Translucent amber: visible on paper and on the dark and sepia themes. */
private const val NOTE_TINT = 0x55F4B860

/** The pull-up index of this edition's notes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotesSheet(
    notes: List<Bridge.AnnotationView>,
    chapterHref: String?,
    chapterTitleFor: (String?) -> String?,
    failure: String?,
    onOpen: (Bridge.AnnotationView) -> Unit,
    onAdd: () -> Unit,
    onDelete: (Bridge.AnnotationView) -> Unit,
    onDismiss: () -> Unit,
) {
    var thisChapterOnly by remember { mutableStateOf(false) }
    val visible =
        if (thisChapterOnly && chapterHref != null) {
            notes.filter { ReadingPosition.locator(it.location)?.href?.toString() == chapterHref }
        } else {
            notes
        }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Annotations", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "${notes.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                )
            }
            Spacer(modifier = Modifier.padding(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !thisChapterOnly,
                    onClick = { thisChapterOnly = false },
                    label = { Text("All") },
                )
                FilterChip(
                    selected = thisChapterOnly,
                    onClick = { thisChapterOnly = true },
                    label = { Text("This chapter") },
                    enabled = chapterHref != null,
                )
            }
            Spacer(modifier = Modifier.padding(2.dp))
            failure?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (visible.isEmpty()) {
                Text(
                    text =
                        if (notes.isEmpty()) {
                            "No notes yet. A note remembers the passage it was written on."
                        } else {
                            "No notes in this chapter."
                        },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    items(visible, key = { it.id }) { note ->
                        NoteRow(
                            note = note,
                            chapter =
                                chapterTitleFor(
                                    ReadingPosition.locator(note.location)?.href?.toString()
                                ),
                            onOpen = { onOpen(note) },
                            onDelete = { onDelete(note) },
                        )
                    }
                }
            }
            TextButton(onClick = onAdd) { Text("Add a note here") }
        }
    }
}

@Composable
private fun NoteRow(
    note: Bridge.AnnotationView,
    chapter: String?,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onOpen() }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = note.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = passageLabel(note.location, chapter),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            )
        }
        TextButton(onClick = onDelete) { Text("Delete") }
    }
}

/** "34% through · Chapter IV", or what the row can say without a passage. */
private fun passageLabel(location: String?, chapter: String?): String {
    val locator = location?.let { ReadingPosition.locator(it) }
    val quote =
        locator
            ?.text
            ?.highlight
            ?.takeIf { it.isNotBlank() }
            ?.let { "\u201C${it.take(60)}\u201D" }
    val percent =
        locator
            ?.locations
            ?.totalProgression
            ?.let { "${(it * 100).toInt()}% through" }
    return listOfNotNull(quote ?: percent, chapter)
        .joinToString(" · ")
        .ifBlank { "no passage recorded" }
}

/** The chapter a href names in the table of contents, if it names one. */
private fun chapterTitle(href: String?, links: List<Link>): String? {
    if (href == null) return null
    fun walk(items: List<Link>): Link? {
        for (link in items) {
            if (link.href.toString() == href && !link.title.isNullOrBlank()) return link
            walk(link.children)?.let { return it }
        }
        return null
    }
    return walk(links)?.title
}

/** One note, pulled up from the page it marks. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotePreviewSheet(
    note: Bridge.AnnotationView,
    chapter: String?,
    onJump: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 28.dp)) {
            Text("Note", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.padding(4.dp))
            Text(
                text = note.content,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.padding(4.dp))
            Text(
                text = passageLabel(note.location, chapter),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            )
            Spacer(modifier = Modifier.padding(8.dp))
            Button(onClick = onJump, modifier = Modifier.fillMaxWidth()) { Text("Jump to passage") }
            TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) { Text("Delete") }
        }
    }
}

/** A note being written, pinned to wherever the reader is now. */
@Composable
private fun WriteNoteDialog(
    draft: String,
    onChange: (String) -> Unit,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a note") },
        text = {
            OutlinedTextField(
                value = draft,
                onValueChange = onChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Note") },
                minLines = 3,
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(draft) }, enabled = draft.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
