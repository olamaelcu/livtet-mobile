package net.olamaelcu.livtet.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import net.olamaelcu.livtet.BuildConfig
import net.olamaelcu.livtet.DiscoveredSyncDevice
import net.olamaelcu.livtet.SeedResultMobile
import net.olamaelcu.livtet.sync.BOOKS_DIR
import net.olamaelcu.livtet.sync.PairPayload
import net.olamaelcu.livtet.sync.PairedPeer
import net.olamaelcu.livtet.sync.PendingBook
import net.olamaelcu.livtet.sync.QrScannerActivity
import net.olamaelcu.livtet.sync.TransferProgress
import net.olamaelcu.livtet.sync.percentOfCurrentFile
import net.olamaelcu.livtet.sync.visiblePeers

/**
 * Settings screen. Four sections previously existed — Paired Devices, Discovered on this network,
 * Plugins, and Labs/Appearance. The Paired Devices and Plugins sections depended on
 * `Bridge.getPairedDevices`, `Bridge.listInstalledPlugins`, `Bridge.getNetworkAddresses`,
 * `Bridge.pairDevice`, `Bridge.unpairDevice`, and `Bridge.setPluginEnabled` — none of which are
 * present in the current `core/livtet-ffi` crate. Those sections are removed until upstream catches
 * up.
 *
 * Sync & Discovery renders whatever `DiscoveryService` found over mDNS and pairs with those peers
 * over iroh via `sync.SyncPairing`: claim, desktop approval, session token.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val themeMode by ThemeManager.mode(context).collectAsState(initial = ThemeManager.Mode.SYSTEM)

    LaunchedEffect(Unit) {
        viewModel.load()
        viewModel.attachDiscovery(context)
    }

    // The all-files grant is toggled in system settings, outside this process
    // and without an activity result to wait on, so the row has to be re-read
    // every time the reader comes back rather than only when this screen is
    // first composed.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshStorageGrant()
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    SyncDiscoverySection(
                        devices = state.discoveredDevices,
                        pairedPeers = state.pairedPeers,
                        localNodeId = state.localNodeId,
                        ownDeviceName = state.ownDeviceName,
                        ownDeviceId = state.ownDeviceId,
                        onPairDevice = viewModel::beginPairing,
                        onEnterNodeId = viewModel::beginManualPairing,
                    )
                }

                item {
                    PairedDevicesSection(
                        pairedPeers = state.pairedPeers,
                        offeredCounts = state.pendingBooks.groupingBy { it.peerNodeId }.eachCount(),
                        receivingFromPeer = state.receivingFromPeer,
                        transfer = state.transfer,
                        onReceiveAll = viewModel::receiveAllFromPeer,
                        onForget = viewModel::forgetPairedPeer,
                    )
                }

                item {
                    IncomingBooksSection(
                        books = state.pendingBooks,
                        autoAccept = state.autoAcceptBooks,
                        storageGranted = state.storageGranted,
                        onAccept = viewModel::acceptBook,
                        onReject = viewModel::rejectBook,
                        onAutoAccept = viewModel::setAutoAcceptBooks,
                        onCheck = viewModel::checkForBooks,
                        onOpenSettings = { openAllFilesSettings(context) },
                    )
                }

                item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

                item { SectionHeader("Labs") }
                item {
                    EmptyStateHint(
                        "Experimental features. Off by default; some may be locked by your build.",
                    )
                }
                items(LabsFlag.ALL.size) { i ->
                    val flag = LabsFlag.ALL[i]
                    LabsRow(
                        flag = flag,
                        effective = state.labsEffective[flag.key] ?: flag.defaultEnabled,
                        buildTimeDefault = state.labsBuildTimeDefaults[flag.key] ?: true,
                        onToggle = { viewModel.setLabsFlag(flag, it) },
                    )
                }

                if (BuildConfig.DEBUG) {
                    item { DeveloperToolsRow(viewModel = viewModel) }
                }
                item {
                    TextButton(
                        onClick = { viewModel.resetLabs() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Reset to defaults")
                    }
                }

                item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

                item { SectionHeader("Appearance") }
                item {
                    ThemeSection(
                        current = themeMode,
                        onChange = { viewModel.setTheme(context, it) },
                    )
                }
            }
        }
        state.pairing?.let { pairing ->
            PairingDialog(
                pairing = pairing,
                alreadyPaired =
                    PairPayload.nodeId(pairing.target.nodeId)?.let { nodeId ->
                        state.pairedPeers.containsKey(nodeId)
                    } ?: false,
                onNodeIdChange = viewModel::updatePairingNodeId,
                onScanned = viewModel::onScannedPayload,
                onNameChange = viewModel::updatePairingName,
                onSend = viewModel::sendPairingRequest,
                onDismiss = viewModel::dismissPairing,
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
    )
}

@Composable
private fun EmptyStateHint(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(12.dp),
    )
}

/**
 * Discovered peers on the LAN, plus the entry point to pairing.
 *
 * `DiscoveryService` fills the list from mDNS; tapping a peer opens the pairing dialog, which files
 * a real claim over iroh. Peers already paired are filtered out — they are listed under "Paired
 * devices" with their token — so this list only ever offers a pairing that is still missing.
 */
@Composable
private fun SyncDiscoverySection(
    devices: List<DiscoveredSyncDevice>,
    pairedPeers: Map<String, PairedPeer>,
    localNodeId: String?,
    ownDeviceName: String,
    ownDeviceId: String,
    onPairDevice: (DiscoveredSyncDevice) -> Unit,
    onEnterNodeId: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Sync & Discovery", fontWeight = FontWeight.Medium)
            Spacer(Modifier.size(4.dp))
            val peers =
                visiblePeers(
                    devices = devices,
                    pairedNodeIds = pairedPeers.keys,
                    ownNodeId = localNodeId,
                    ownDeviceId = ownDeviceId,
                    ownDeviceName = ownDeviceName,
                )
            if (peers.visible.isEmpty() && peers.othersOnNetwork) {
                Text(
                    text =
                        "Every device on this network is already paired. Remove one above to " +
                            "pair with it again.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (peers.visible.isEmpty()) {
                Text(
                    text =
                        "No devices on this network yet. Open Livtet on a desktop that is on the " +
                            "same Wi-Fi.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                peers.visible.forEach { device ->
                    DeviceRow(device = device, onPair = { onPairDevice(device) })
                }
                Spacer(Modifier.size(4.dp))
                Text(
                    text = "Tap a device to send a pairing request. Approve it on the desktop.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.size(4.dp))
            TextButton(onClick = onEnterNodeId, modifier = Modifier.fillMaxWidth()) {
                Text("Pair with a node ID")
            }
            localNodeId?.let { nodeId ->
                Spacer(Modifier.size(8.dp))
                Text(
                    text = "This device is visible to desktops while this screen is open.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = nodeId,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

/**
 * Peers whose claims this device's reader approved, with the token each issued.
 *
 * This is the durable view of pairing: discovery is transient and depends on the peer being awake
 * and on the same network, while an approved pairing is a record that survives restarts. Rows come
 * straight from DataStore, so a peer still appears here after it stops advertising.
 */
@Composable
private fun PairedDevicesSection(
    pairedPeers: Map<String, PairedPeer>,
    offeredCounts: Map<String, Int>,
    receivingFromPeer: String?,
    transfer: TransferProgress?,
    onReceiveAll: (String) -> Unit,
    onForget: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Paired devices", fontWeight = FontWeight.Medium)
            Spacer(Modifier.size(4.dp))
            if (pairedPeers.isEmpty()) {
                Text(
                    text =
                        "No approved pairings yet. A desktop appears here once it accepts a " +
                            "request from this device.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                var confirming by remember { mutableStateOf<String?>(null) }
                pairedPeers.values
                    .sortedBy { it.name.lowercase() }
                    .forEach { peer ->
                        PairedDeviceRow(
                            peer = peer,
                            offeredCount = offeredCounts[peer.nodeId] ?: 0,
                            receiving = receivingFromPeer == peer.nodeId,
                            transfer = transfer,
                            onReceiveAll = { onReceiveAll(peer.nodeId) },
                            onRemove = { confirming = peer.nodeId },
                        )
                    }
                confirming?.let { nodeId ->
                    pairedPeers[nodeId]?.let { peer ->
                        ForgetPairingDialog(
                            peer = peer,
                            onConfirm = {
                                confirming = null
                                onForget(nodeId)
                            },
                            onDismiss = { confirming = null },
                        )
                    }
                }
            }
        }
    }
}

/** Two-tap confirmation for the one destructive action on this screen. */
@Composable
private fun ForgetPairingDialog(
    peer: PairedPeer,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remove ${peer.name.ifBlank { "node ${peer.nodeId.take(8)}" }}?") },
        text = {
            Text(
                text =
                    "This deletes the session token on this device, so it can no longer sync " +
                        "with that desktop until it approves a new request. The desktop keeps its own " +
                        "record of this device.",
                fontSize = 12.sp,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Remove", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Keep") } },
    )
}

/**
 * Books a paired peer is offering.
 *
 * Receiving writes into `Documents/Livtet`, so it needs all-files access; until that is granted the
 * list can still be inspected, but accepting is refused by the core with a clear reason.
 */
@Composable
private fun IncomingBooksSection(
    books: List<PendingBook>,
    autoAccept: Boolean,
    storageGranted: Boolean,
    onAccept: (PendingBook) -> Unit,
    onReject: (PendingBook) -> Unit,
    onAutoAccept: (Boolean) -> Unit,
    onCheck: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Incoming books",
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onCheck) { Text("Check now") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Accept automatically", fontSize = 13.sp)
                    Text(
                        text =
                            "Paired devices send books without asking. Off means every book " +
                                "waits for you below.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = autoAccept, onCheckedChange = onAutoAccept)
            }
            if (!storageGranted) {
                Spacer(Modifier.size(8.dp))
                Text(
                    text =
                        "Receiving needs access to ${BOOKS_DIR}, so a received book is " +
                            "openable in KOReader.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = onOpenSettings) { Text("Allow access to Documents") }
            }
            Spacer(Modifier.size(4.dp))
            if (books.isEmpty()) {
                Text(
                    text = "No books waiting. Paired devices are asked every minute.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                books.forEach { book ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(book.fileName, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                            Text(
                                text =
                                    "from ${book.peerName.ifBlank { "node ${book.peerNodeId.take(8)}" }}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { onReject(book) }) { Text("Decline") }
                        TextButton(onClick = { onAccept(book) }) { Text("Receive") }
                    }
                }
            }
        }
    }
}

/** Open the system screen where all-files access is granted. */
private fun openAllFilesSettings(context: Context) {
    val app =
        Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.fromParts("package", context.packageName, null),
        )
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    // Some OEM builds omit the per-app screen; fall back to the list.
    runCatching { context.startActivity(app) }
        .onFailure {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
}

/** One approved peer: the name it gave, then the ids both sides key on. */
@Composable
private fun PairedDeviceRow(
    peer: PairedPeer,
    offeredCount: Int,
    receiving: Boolean,
    transfer: TransferProgress?,
    onReceiveAll: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = peer.name.ifBlank { "node ${peer.nodeId.take(8)}" },
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            // Only offered when the peer actually has books queued: an always-on
            // button would promise a sync that has nothing to do.
            if (receiving) {
                val label =
                    transfer?.let { progress ->
                        val percent = progress.percentOfCurrentFile()
                        "Receiving ${progress.done} of ${progress.total} · $percent%"
                    } ?: "Receiving…"
                Text(
                    text = label,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (offeredCount > 0) {
                TextButton(onClick = onReceiveAll) {
                    Text(if (offeredCount == 1) "Receive book" else "Receive $offeredCount books")
                }
            }
            TextButton(onClick = onRemove) {
                Text("Remove", color = MaterialTheme.colorScheme.error)
            }
        }
        val meta =
            buildList {
                // The title already carries the node id when the peer never told us a name.
                if (peer.name.isNotBlank() && peer.nodeId.isNotBlank()) {
                    add("node ${peer.nodeId.take(8)}")
                }
                if (peer.deviceId.isNotBlank()) add("device ${peer.deviceId.take(8)}")
            }
        if (meta.isNotEmpty()) {
            Spacer(Modifier.size(2.dp))
            Text(
                text = meta.joinToString(" · "),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (receiving && transfer != null) {
            Spacer(Modifier.size(4.dp))
            LinearProgressIndicator(
                // Per file, not per queue: the bar answers "how far through this
                // book", which is what a reader waiting on a large file wants.
                progress = { transfer.percentOfCurrentFile() / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = transfer.fileName,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * One discovered peer: friendly name, then the id/version/flavor the TXT record carried.
 *
 * A record with no node id cannot be paired (pairing dials that id), so the row stays inert rather
 * than opening a dialog that could only fail.
 */
@Composable
private fun DeviceRow(
    device: DiscoveredSyncDevice,
    onPair: () -> Unit,
) {
    val canPair = device.nodeId.isNotBlank()
    val modifier =
        if (canPair) Modifier.fillMaxWidth().clickable { onPair() } else Modifier.fillMaxWidth()
    Column(modifier = modifier.padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(device.name, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            if (!canPair) {
                Text(text = "No node id", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
            }
        }
        val meta =
            buildList {
                if (device.deviceId.isNotBlank()) add(device.deviceId.take(8))
                if (device.nodeId.isNotBlank()) add("node ${device.nodeId.take(8)}")
                device.appVersion?.takeIf { it.isNotBlank() }?.let { add("v$it") }
                device.deviceFlavor?.takeIf { it.isNotBlank() }?.let(::add)
            }
        if (meta.isNotEmpty()) {
            Spacer(Modifier.size(2.dp))
            Text(
                text = meta.joinToString(" · "),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Pairs this device with one peer over iroh, by discovered node id or by hand.
 *
 * The flow is a claim the desktop approves by hand: the name stays editable until the request is
 * sent, then the dialog reports what the peer actually decided — waiting, approved, or refused. The
 * node id field is the escape hatch for a desktop mDNS did not surface — another subnet, a filtered
 * multicast network, or a peer that never advertised.
 */
@Composable
private fun PairingDialog(
    pairing: PairingUiState,
    alreadyPaired: Boolean,
    onNodeIdChange: (String) -> Unit,
    onScanned: (String) -> Unit,
    onNameChange: (String) -> Unit,
    onSend: () -> Unit,
    onDismiss: () -> Unit,
) {
    val stage = pairing.stage
    val editable = stage is PairingStage.Ready
    val context = LocalContext.current
    val scanner =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result,
            ->
            val payload =
                result.data?.getStringExtra(QrScannerActivity.EXTRA_PAYLOAD)
                    ?: return@rememberLauncherForActivityResult
            onScanned(payload)
        }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pair with ${pairing.target.title()}") },
        text = {
            Column {
                OutlinedTextField(
                    value = pairing.target.nodeId,
                    onValueChange = onNodeIdChange,
                    singleLine = true,
                    label = { Text("Desktop node ID") },
                    enabled = editable,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.size(4.dp))
                TextButton(
                    onClick = { scanner.launch(Intent(context, QrScannerActivity::class.java)) },
                    enabled = editable,
                ) {
                    Text("Scan QR code")
                }
                pairing.inputError?.let { error ->
                    Text(text = error, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.size(8.dp))
                OutlinedTextField(
                    value = pairing.name,
                    onValueChange = onNameChange,
                    singleLine = true,
                    label = { Text("Name this device for the desktop") },
                    enabled = editable,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.size(8.dp))
                PairingStageBody(stage)
                if (alreadyPaired && editable) {
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text =
                            "This device is already paired. The desktop keeps the first " +
                                "approval; pairing again cannot reopen it.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        confirmButton = {
            when (stage) {
                is PairingStage.Ready ->
                    TextButton(onClick = onSend, enabled = !alreadyPaired) { Text("Send request") }
                else -> Unit
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (stage is PairingStage.Sending) "Cancel" else "Close")
            }
        },
    )
}

@Composable
private fun PairingStageBody(stage: PairingStage) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    when (stage) {
        is PairingStage.Ready ->
            Text(
                text =
                    "The desktop shows a request to approve. Nothing is paired until it is " +
                        "accepted there. Its node ID is under the desktop's sync diagnostics; scan " +
                        "its pairing code to fill the field.",
                fontSize = 12.sp,
                color = muted,
            )
        is PairingStage.Sending ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.size(8.dp))
                Text("Contacting the desktop…", fontSize = 12.sp, color = muted)
            }
        is PairingStage.AwaitingDecision ->
            Text(
                text =
                    "Request filed. Waiting for approval on the desktop — claim " +
                        "${stage.claim.take(8)}. Checking again automatically; closing this dialog " +
                        "keeps checking.",
                fontSize = 12.sp,
                color = muted,
            )
        is PairingStage.Paired ->
            Text(
                text = "Paired. The session token is stored, so this device can sync.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary,
            )
        is PairingStage.Refused ->
            Text(
                text = "Refused: ${stage.reason}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.error,
            )
        is PairingStage.Failed ->
            Text(
                text = "Could not pair: ${stage.message}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.error,
            )
    }
}

@Composable
private fun LabsRow(
    flag: LabsFlag,
    effective: Boolean,
    buildTimeDefault: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            val context = LocalContext.current
            Column(Modifier.weight(1f)) {
                Text(text = context.getString(flag.titleRes), fontWeight = FontWeight.Medium)
                Text(
                    text = context.getString(flag.descriptionRes),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!buildTimeDefault) {
                    Text(
                        text =
                            context.getString(net.olamaelcu.livtet.R.string.labs_locked_by_build),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            Switch(checked = effective, onCheckedChange = onToggle, enabled = buildTimeDefault)
        }
    }
}

@Composable
private fun ThemeSection(
    current: ThemeManager.Mode,
    onChange: (ThemeManager.Mode) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Theme", modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.size(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeOptionChip("System", current == ThemeManager.Mode.SYSTEM) {
                    onChange(ThemeManager.Mode.SYSTEM)
                }
                ThemeOptionChip("Light", current == ThemeManager.Mode.LIGHT) {
                    onChange(ThemeManager.Mode.LIGHT)
                }
                ThemeOptionChip("Dark", current == ThemeManager.Mode.DARK) {
                    onChange(ThemeManager.Mode.DARK)
                }
            }
        }
    }
}

@Composable
private fun ThemeOptionChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bg =
        if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        }
    val fg =
        if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        }
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = bg)) {
        Text(label, color = fg, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
    }
}

/**
 * Debug-only "Reset and seed" developer tool. Wipes every user-data table in the local SQLite
 * database via the FFI and re-seeds it with N demo books. Two-tap confirmation matches Material
 * guidance for destructive actions; the row also shows the latest SeedResultMobile so the user sees
 * "Seeded 25 works / M editions…" feedback.
 *
 * Gated on `BuildConfig.DEBUG` at the call site in `SettingsScreen`.
 */
@Composable
private fun DeveloperToolsRow(viewModel: SettingsViewModel) {
    val state by viewModel.state.collectAsState()
    var input by rememberSaveable { mutableStateOf("25") }
    var confirming by rememberSaveable { mutableStateOf(false) }
    val parsed = input.toIntOrNull()?.coerceIn(0, 1000) ?: 25
    val running = state.seedRunning
    val result = state.seedResult
    val error = state.errorMessage

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Developer tools", fontWeight = FontWeight.Medium)
            Spacer(Modifier.size(4.dp))
            Text(
                "Wipe the local database and reseed it with demo books.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(8.dp))
            OutlinedTextField(
                value = input,
                onValueChange = { v -> if (v.all { it.isDigit() } && v.length <= 4) input = v },
                singleLine = true,
                label = { Text("Number of works") },
                keyboardOptions =
                    androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                    ),
                enabled = !running,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.size(8.dp))
            if (confirming) {
                Text(
                    "This will delete every row in the local database and seed $parsed demo works.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.size(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { confirming = false }, enabled = !running) {
                        Text("Cancel")
                    }
                    Spacer(Modifier.size(8.dp))
                    FilledTonalButton(
                        onClick = {
                            confirming = false
                            viewModel.resetAndSeed(parsed)
                        },
                        enabled = !running,
                        colors =
                            ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            ),
                    ) {
                        Text("Confirm reset")
                    }
                }
            } else {
                FilledTonalButton(
                    onClick = { confirming = true },
                    enabled = !running,
                    colors =
                        ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                ) {
                    Text(if (running) "Resetting…" else "Reset and seed library")
                }
            }
            if (error != null && !running) {
                Spacer(Modifier.size(8.dp))
                Text(
                    text = "Error: $error",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            result?.let { r ->
                Spacer(Modifier.size(8.dp))
                Text(
                    text = seedResultSummary(r),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun seedResultSummary(r: SeedResultMobile): String =
    "Seeded ${r.worksCreated} works, ${r.editionsCreated} editions, " +
        "${r.authorsCreated} authors, ${r.publishersCreated} publishers " +
        "(${r.readingStatusCount} reading-status, ${r.readingSessionsCreated} sessions, " +
        "${r.savedSearchesCreated} saved searches, ${r.readingListsCreated} reading lists)."
