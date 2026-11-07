package net.olamaelcu.livtet

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.olamaelcu.livtet.core.designsystem.LivtetTheme
import net.olamaelcu.livtet.ffi.SyncOfferSummary
import net.olamaelcu.livtet.ffi.TransferProgressListener

/**
 * Books a peer offered, waiting on a decision.
 *
 * Two shapes for one job, chosen by how much is waiting: a deck while the count is small enough to
 * consider one at a time, a list once it is a queue to drain. Opening the screen pulls first, so
 * what it shows is what the peers have now rather than what was here when the app last ran.
 */
class OffersActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        setContent {
            LivtetTheme(darkTheme = isSystemInDarkTheme()) { OffersRoute(onBack = { finish() }) }
        }
    }

    companion object {
        fun intent(context: Context): Intent = Intent(context, OffersActivity::class.java)
    }
}

private sealed interface InboxState {
    data object Loading : InboxState

    data class Failed(val message: String) : InboxState

    data class Ready(val offers: List<SyncOfferSummary>) : InboxState
}

@Composable
private fun OffersRoute(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    var state: InboxState by remember { mutableStateOf(InboxState.Loading) }
    var deciding: String? by remember { mutableStateOf(null) }
    var progress: Float? by remember { mutableStateOf(null) }
    var notice: String? by remember { mutableStateOf(null) }

    suspend fun reload() {
        state =
            try {
                InboxState.Ready(Bridge.refreshOffers())
            } catch (error: Exception) {
                InboxState.Failed(readable(error))
            }
    }

    LaunchedEffect(Unit) {
        if (!Bridge.isInitialized()) {
            Bridge.init(java.io.File(context.filesDir, "livtet.db").absolutePath)
        }
        reload()
    }

    fun decide(offer: SyncOfferSummary, accept: Boolean) {
        scope.launch {
            deciding = offer.offerId
            progress = if (accept) 0f else null
            notice = null
            try {
                if (accept) {
                    val listener =
                        object : TransferProgressListener {
                            override fun onProgress(done: ULong, total: ULong) {
                                progress =
                                    if (total == 0UL) 0f
                                    else (done.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                            }
                        }
                    Bridge.acceptOffer(offer, listener)
                    notice = "Added ${offer.title}"
                } else {
                    Bridge.rejectOffer(offer)
                    notice = "Declined ${offer.title}"
                }
                reload()
            } catch (error: Exception) {
                notice = readable(error)
            } finally {
                deciding = null
                progress = null
            }
        }
    }

    /** Accept everything still waiting, one at a time so progress stays honest. */
    fun acceptAll(offers: List<SyncOfferSummary>) {
        scope.launch {
            var added = 0
            var failed = 0
            for (offer in offers) {
                deciding = offer.offerId
                progress = 0f
                try {
                    Bridge.acceptOffer(offer)
                    added += 1
                } catch (_: Exception) {
                    failed += 1
                }
            }
            deciding = null
            progress = null
            notice = AcceptAllResult(added = added, failed = failed).summary
            reload()
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack) { Text("\u2039  Library") }
                Spacer(modifier = Modifier.weight(1f))
                Text("Waiting for you", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.weight(1f))
                val count = (state as? InboxState.Ready)?.offers?.size ?: 0
                Text(
                    text = if (count > 1) "$count" else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                )
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (val current = state) {
                    InboxState.Loading ->
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            CircularProgressIndicator(strokeWidth = 3.dp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Looking for books\u2026",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                            )
                        }

                    is InboxState.Failed ->
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
                            TextButton(onClick = { scope.launch { reload() } }) {
                                Text("Try again")
                            }
                        }

                    is InboxState.Ready ->
                        when {
                            current.offers.isEmpty() -> EmptyInbox()
                            presentationFor(current.offers.size) == OfferPresentation.DECK ->
                                OfferDeck(
                                    offers = current.offers,
                                    deciding = deciding,
                                    progress = progress,
                                    onAccept = { decide(it, true) },
                                    onDecline = { decide(it, false) },
                                )

                            else ->
                                OfferList(
                                    offers = current.offers,
                                    deciding = deciding,
                                    progress = progress,
                                    onAccept = { decide(it, true) },
                                    onDecline = { decide(it, false) },
                                    onAcceptAll = { acceptAll(current.offers) },
                                )
                        }
                }
            }

            notice?.let {
                Text(
                    text = it,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                )
            }
        }
    }
}

@Composable
private fun EmptyInbox() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Nothing is waiting.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "When a paired device offers a book it appears here.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
        )
    }
}

/** One book at a time, for a count small enough to consider. */
@Composable
private fun OfferDeck(
    offers: List<SyncOfferSummary>,
    deciding: String?,
    progress: Float?,
    onAccept: (SyncOfferSummary) -> Unit,
    onDecline: (SyncOfferSummary) -> Unit,
) {
    // The deck always shows the first undecided offer: deciding removes it from
    // the list, so the next one takes its place without any index bookkeeping.
    val offer = offers.firstOrNull() ?: return
    // The cover is fetched for the book on the card, not for the whole deck: a
    // preview costs a round trip to the peer, so only the offer being looked at
    // pays for one.
    var cover by remember(offer.offerId) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(offer.offerId, offer.hasCover) {
        cover =
            if (!offer.hasCover) {
                null
            } else {
                runCatching { Bridge.offerCover(offer) }
                    .getOrNull()
                    ?.let { bytes ->
                        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            ?.asImageBitmap()
                    }
            }
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp)) {
                cover?.let { bitmap ->
                    Image(
                        bitmap = bitmap,
                        contentDescription = "Cover of ${offer.title}",
                        contentScale = ContentScale.Crop,
                        modifier =
                            Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(12.dp)),
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
                Text(
                    text = "FROM ${offer.fromDeviceId.take(8).uppercase()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = offer.title, style = MaterialTheme.typography.headlineSmall)
                if (offer.authors.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = offer.authors.joinToString(", "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = offer.fileName?.substringAfterLast('.')?.uppercase() ?: "BOOK",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        // Only a fallback: when the art is on the card, saying so
                        // in words as well is noise.
                        text = if (offer.hasCover && cover == null) "Cover art" else "",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        if (deciding == offer.offerId) {
            LinearProgressIndicator(
                progress = { progress ?: 0f },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Adding\u2026 ${((progress ?: 0f) * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            )
        } else {
            TextButton(onClick = { onAccept(offer) }, modifier = Modifier.fillMaxWidth()) {
                Text("Add to library")
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                TextButton(onClick = { onDecline(offer) }) { Text("Decline") }
            }
            Text(
                text = "${offers.size} waiting",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            )
        }
    }
}

/** A queue to drain, for a count too large to consider one at a time. */
@Composable
private fun OfferList(
    offers: List<SyncOfferSummary>,
    deciding: String?,
    progress: Float?,
    onAccept: (SyncOfferSummary) -> Unit,
    onDecline: (SyncOfferSummary) -> Unit,
    onAcceptAll: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${offers.size} waiting",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            )
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = onAcceptAll, enabled = deciding == null) { Text("Accept all") }
        }
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            offers.forEach { offer ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(offer.title, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text =
                                listOfNotNull(
                                        offer.authors.joinToString(", ").ifBlank { null },
                                        offer.fileName?.substringAfterLast('.')?.uppercase(),
                                    )
                                    .joinToString(" \u00b7 "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        )
                        if (deciding == offer.offerId) {
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { progress ?: 0f },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    if (deciding == offer.offerId) {
                        Text(
                            text = "${((progress ?: 0f) * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        TextButton(onClick = { onAccept(offer) }) { Text("Accept") }
                        TextButton(onClick = { onDecline(offer) }) { Text("Decline") }
                    }
                }
            }
        }
    }
}
