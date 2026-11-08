package net.olamaelcu.livtet

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.olamaelcu.livtet.core.designsystem.LivtetTheme
import net.olamaelcu.livtet.ffi.SyncPairedDevice
import net.olamaelcu.livtet.ffi.SyncPairingResult
import net.olamaelcu.livtet.ffi.SyncPendingPairing

/**
 * Sync & Devices.
 *
 * Three things a reader does here, all of them real operations rather than status readouts: let
 * this device be found, answer whoever asks to pair, and pair outward with a peer seen on the
 * network.
 *
 * Advertising stops when the screen closes. A phone that advertised forever would answer pairing
 * requests nobody is looking at, and the desktop's claim would sit unanswered — better to be
 * reachable while the screen that can answer is open.
 */
class SyncActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        setContent {
            LivtetTheme(darkTheme = isSystemInDarkTheme()) { SyncRoute(onBack = { finish() }) }
        }
    }

    companion object {
        /** The name peers see for this device. */
        fun deviceName(): String = "${Build.MODEL} (Livtet)"

        fun intent(context: Context): Intent = Intent(context, SyncActivity::class.java)
    }
}

@Composable
private fun SyncRoute(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var nodeId by remember { mutableStateOf<String?>(null) }
    var link by remember { mutableStateOf<String?>(null) }
    var nearby by remember { mutableStateOf<List<NearbyPeer>>(emptyList()) }
    var requests by remember { mutableStateOf<List<SyncPendingPairing>>(emptyList()) }
    var paired by remember { mutableStateOf<List<SyncPairedDevice>>(emptyList()) }
    var notice by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    var typedTicket by remember { mutableStateOf("") }

    // Advertising is what makes polling possible, so its failure is its own
    // state: otherwise the first poll's "not advertising yet" sticks forever.
    var advertiseError by remember { mutableStateOf<String?>(null) }

    val name = SyncActivity.deviceName()

    suspend fun refresh() {
        try {
            requests = Bridge.pendingPairings()
            paired = Bridge.pairedDevices()
            failure = null
        } catch (error: Exception) {
            failure = readable(error)
        }
    }

    // Advertise first: the node id is what a peer's browse resolves, and it is
    // also what this screen must not list as "nearby" — it is this device.
    LaunchedEffect(Unit) {
        try {
            nodeId = Bridge.advertiseForSync(Bridge.deviceId(), name)
            link = Bridge.pairingLink(name)
            advertiseError = null
        } catch (error: Exception) {
            advertiseError = readable(error)
        }
    }

    // Anything waiting on this device can arrive at any moment, so the screen
    // re-reads while it is open rather than showing a stale list. It waits for the
    // advertisement, because claims arrive at the endpoint advertising binds.
    LaunchedEffect(nodeId) {
        if (nodeId == null) return@LaunchedEffect
        refresh()
        while (true) {
            delay(2_000)
            refresh()
        }
    }

    val ownNodeId = nodeId
    DisposableEffect(ownNodeId) {
        val discovery = NearbyPeers(context, ownNodeId)
        val job = scope.launch { discovery.browse().collect { nearby = it } }
        onDispose {
            job.cancel()
            discovery.close()
        }
    }

    // Advertising belongs to the screen, not to the node id: keying the effect
    // above on the id means this must not live there, or the first recomposition
    // after advertising succeeds would tear the advertisement down again.
    DisposableEffect(Unit) { onDispose { Bridge.stopAdvertising() } }

    /**
     * Claim a peer by node id.
     *
     * Shared by the discovered list and the field: a node id typed from another screen is the same
     * claim as one a browse found, so the approval wait and its messages live here rather than in
     * each caller.
     */
    fun pairNode(peerNodeId: String, label: String) {
        scope.launch {
            busy = peerNodeId
            failure = null
            notice = "Waiting for $label to approve\u2026"
            try {
                when (val first = Bridge.pairWithPeer(peerNodeId, name, 1u)) {
                    is SyncPairingResult.Paired -> notice = "Paired with $label"
                    is SyncPairingResult.Refused -> notice = "Refused: ${first.reason}"
                    is SyncPairingResult.Pending -> {
                        var status: SyncPairingResult = first
                        repeat(30) {
                            if (status !is SyncPairingResult.Pending) return@repeat
                            delay(2_000)
                            status = Bridge.pairingStatus(peerNodeId, first.claim, name, 2u)
                        }
                        notice =
                            when (status) {
                                is SyncPairingResult.Paired -> "Paired with $label"
                                is SyncPairingResult.Refused -> "Refused: ${status.reason}"
                                is SyncPairingResult.Pending ->
                                    "Still waiting \u2014 approve on $label"
                            }
                    }
                }
            } catch (error: Exception) {
                notice = null
                failure = error.message ?: error.toString()
            } finally {
                busy = null
                refresh()
            }
        }
    }

    fun pair(peer: NearbyPeer) = pairNode(peer.nodeId, peer.label)

    /** Pair with whatever the reader pasted: a node id, or a pairing link. */
    fun pairTyped() {
        val input = typedTicket.trim()
        if (input.isEmpty()) return
        failure = null
        when {
            PairingTicket.isLink(input) ->
                runCatching { Bridge.parsePairingLink(input) }
                    .onSuccess { link ->
                        pairNode(
                            link.nodeId,
                            link.name?.takeIf { it.isNotBlank() } ?: "Peer ${link.nodeId.take(6)}",
                        )
                    }
                    .onFailure { failure = "That pairing link cannot be read." }
            else ->
                PairingTicket.nodeId(input)?.let { nodeId ->
                    pairNode(nodeId, "Peer ${nodeId.take(6)}")
                }
                    ?: run {
                        failure = "That is not a node id (64 hex characters) or a pairing link."
                    }
        }
    }

    fun decide(request: SyncPendingPairing, accept: Boolean) {
        scope.launch {
            busy = request.nodeId
            runCatching { Bridge.decidePairing(request.nodeId, accept) }
                .onSuccess { token ->
                    notice =
                        if (accept) {
                            if (token == null) {
                                "Approved"
                            } else {
                                "Approved \u2014 this device can now serve books to ${request.name ?: "the peer"}"
                            }
                        } else {
                            "Declined"
                        }
                }
                .onFailure { failure = it.message ?: it.toString() }
            busy = null
            refresh()
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .appHeaderInset()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("\u2039  Back") }
                Spacer(modifier = Modifier.weight(1f))
                Text("Sync & Devices", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(4.dp))

            ThisDeviceCard(
                name = name,
                nodeId = nodeId,
                link = link,
                onCopy = { text ->
                    val clipboard =
                        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Livtet pairing link", text))
                    notice = "Pairing link copied"
                },
            )

            // Discovery only finds devices on this network that are advertising
            // right now. A node id from another screen, or from a QR read on the
            // desktop, is the way in when the browse cannot see it.
            PairByNodeIdCard(
                value = typedTicket,
                onChange = { typedTicket = it },
                onPair = ::pairTyped,
                enabled = busy == null,
            )

            if (requests.isNotEmpty()) {
                Section("Waiting for you")
                requests.forEach { request ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text =
                                    request.name?.takeIf { it.isNotBlank() } ?: "A nearby device",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text = request.nodeId.take(12) + "\u2026",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                            )
                        }
                        if (busy == request.nodeId) {
                            CircularProgressIndicator(
                                modifier = Modifier.height(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            TextButton(onClick = { decide(request, false) }) { Text("Decline") }
                            TextButton(onClick = { decide(request, true) }) { Text("Approve") }
                        }
                    }
                }
            }

            Section("Nearby")
            when {
                nodeId == null && advertiseError == null ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(18.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(modifier = Modifier.padding(6.dp))
                        Text(
                            text = "Starting\u2026",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        )
                    }

                nodeId == null ->
                    Text(
                        text = advertiseError.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )

                nearby.isEmpty() ->
                    Text(
                        text =
                            "Looking for devices on this network. Have the other device open its sync screen too.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    )

                else ->
                    nearby.forEach { peer ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(peer.label, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text =
                                        listOfNotNull(peer.deviceType, peer.nodeId.take(10))
                                            .joinToString(" \u00b7 "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color =
                                        MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                )
                            }
                            if (busy == peer.nodeId) {
                                CircularProgressIndicator(
                                    modifier = Modifier.height(20.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                TextButton(onClick = { pair(peer) }) { Text("Pair") }
                            }
                        }
                    }
            }

            Section("Paired")
            if (paired.isEmpty()) {
                Text(
                    text = "No devices yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                )
            } else {
                paired.forEach { device ->
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(
                            text =
                                device.name?.takeIf { it.isNotBlank() } ?: device.deviceId.take(8),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text =
                                if (device.outbound) {
                                    "You can pull books from this device"
                                } else {
                                    "This device can pull books from you"
                                },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        )
                    }
                }
            }

            // Both outcomes land here: what just happened, and what went wrong.
            notice?.let {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                )
            }
            failure?.let {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ThisDeviceCard(name: String, nodeId: String?, link: String?, onCopy: (String) -> Unit) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "THIS DEVICE",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(name, style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = nodeId ?: "starting\u2026",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            )
            if (link != null) {
                Spacer(modifier = Modifier.height(4.dp))
                TextButton(onClick = { onCopy(link) }) { Text("Copy pairing link") }
            }
        }
    }
}

/** The manual door: a node id or pairing link, typed or pasted. */
@Composable
private fun PairByNodeIdCard(
    value: String,
    onChange: (String) -> Unit,
    onPair: () -> Unit,
    enabled: Boolean,
) {
    Spacer(modifier = Modifier.height(12.dp))
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "PAIR BY NODE ID",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text =
                    "Paste the id the other device shows, or a livtet:// pairing " +
                        "link. Use this when the device is not listed below.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = value,
                onValueChange = onChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Node id or link") },
                textStyle =
                    MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onPair, enabled = enabled && value.isNotBlank()) {
                    Text("Pair")
                }
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    Spacer(modifier = Modifier.height(20.dp))
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
    )
    Spacer(modifier = Modifier.height(4.dp))
}
