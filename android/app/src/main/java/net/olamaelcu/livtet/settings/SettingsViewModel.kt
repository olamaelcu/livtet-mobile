package net.olamaelcu.livtet.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.olamaelcu.livtet.Bridge
import net.olamaelcu.livtet.DiscoveredSyncDevice
import net.olamaelcu.livtet.DiscoveryService
import net.olamaelcu.livtet.SeedResultMobile
import net.olamaelcu.livtet.ffi.LivtetException
import net.olamaelcu.livtet.ffi.SyncPairingResult
import net.olamaelcu.livtet.sync.PairPayload
import net.olamaelcu.livtet.sync.PairedPeer
import net.olamaelcu.livtet.sync.PendingBook
import net.olamaelcu.livtet.sync.SyncPairing
import net.olamaelcu.livtet.sync.SyncPull
import net.olamaelcu.livtet.sync.TransferProgress

/**
 * State for the Settings screen.
 *
 * The Paired Devices, Discovered on this network, and Plugins sections previously relied on
 * `Bridge.getPairedDevices`, `Bridge.listInstalledPlugins`, `Bridge.getNetworkAddresses`,
 * `Bridge.pairDevice`, `Bridge.unpairDevice`, and `Bridge.setPluginEnabled` — none of which are
 * present in the current `core/livtet-ffi` crate. Those sections are dropped from the screen until
 * upstream catches up. Labs (DataStore) and Appearance (DataStore) still work and remain.
 *
 * `discoveredDevices` is populated by the mDNS `DiscoveryService` and rendered read-only by the
 * Sync & Discovery section. Pairing runs over iroh through the FFI: [pairing] tracks an in-flight
 * claim, and [pairedPeers] holds the peers whose claims were approved.
 */
data class SettingsUiState(
    val discoveredDevices: List<DiscoveredSyncDevice> = emptyList(),
    val pairedPeers: Map<String, PairedPeer> = emptyMap(),
    val pairing: PairingUiState? = null,
    /** This device's iroh node id, once it is advertising itself as a peer. */
    val localNodeId: String? = null,
    /** This device's own name and ULID, used to keep it out of the peer list. */
    val ownDeviceName: String = "",
    val ownDeviceId: String = "",
    /** Books paired peers are offering, waiting on this reader's decision. */
    val pendingBooks: List<PendingBook> = emptyList(),
    /** Accept offers without asking, for peers already trusted. */
    val autoAcceptBooks: Boolean = false,
    /** Peer whose whole set of offers is being downloaded right now. */
    val receivingFromPeer: String? = null,
    /** Which book of that peer's queue is in flight, and how many there are. */
    val transfer: TransferProgress? = null,
    /** Whether the app may write into the shared books folder. */
    val storageGranted: Boolean = false,
    val labsEffective: Map<String, Boolean> = emptyMap(),
    val labsBuildTimeDefaults: Map<String, Boolean> = emptyMap(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val seedRunning: Boolean = false,
    val seedResult: SeedResultMobile? = null,
)

/** An in-flight or settled pairing attempt, owned by the dialog that started it. */
data class PairingUiState(
    val target: PairingTarget,
    /** Name filed with the claim; editable until the request is sent. */
    val name: String,
    val stage: PairingStage,
    /** Why the last scan or typed value was rejected, if it was. */
    val inputError: String? = null,
)

/**
 * Who the claim is addressed to: an iroh node id, plus what to call it in the UI.
 *
 * [label] comes from the mDNS TXT record when the peer was discovered, and is empty when the reader
 * typed or scanned the id, in which case the node id is the only thing that identifies the peer.
 */
data class PairingTarget(val nodeId: String, val label: String) {
    /** Fallback title for a peer that was not discovered. */
    fun title(): String = label.ifBlank { nodeId.take(8).ifBlank { "this node" } }
}

sealed interface PairingStage {
    /** The dialog is open and nothing has been sent yet; the name is editable. */
    data object Ready : PairingStage

    /** The claim is being filed or the endpoint is still dialling. */
    data object Sending : PairingStage

    /** Filed, and the desktop has not decided yet. */
    data class AwaitingDecision(val claim: String) : PairingStage

    data object Paired : PairingStage

    data class Refused(val reason: String) : PairingStage

    /** The call itself failed (no endpoint, peer unreachable, bad key file). */
    data class Failed(val message: String) : PairingStage
}

class SettingsViewModel : ViewModel() {
    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    @Volatile private var discoveryService: DiscoveryService? = null
    private var discoveryJob: Job? = null
    private var labsJob: Job? = null
    private var seedJob: Job? = null
    private var pairingJob: Job? = null
    private var pollJob: Job? = null
    private var appContext: Context? = null

    fun attachDiscovery(context: Context) {
        if (discoveryService != null) return
        val service = DiscoveryService(context.applicationContext)
        discoveryService = service
        service.start()
        viewModelScope.launch {
            service.devices.collect { devices ->
                _state.update { it.copy(discoveredDevices = devices) }
            }
        }
        appContext = context.applicationContext
        viewModelScope.launch {
            val name = SyncPairing.deviceName(context.applicationContext)
            val deviceId = SyncPairing.deviceId(context.applicationContext)
            _state.update { it.copy(ownDeviceName = name, ownDeviceId = deviceId) }
            // Records from an earlier build carry this device's own identity; drop those fields,
            // then ask each peer who it is so the list shows the desktop rather than the phone.
            SyncPairing.repairPairedPeers(context.applicationContext)
            SyncPairing.pairedPeers(context.applicationContext)
                .first()
                .values
                .filter { it.deviceId.isBlank() }
                .forEach { peer ->
                    runCatching {
                        SyncPairing.refreshPeerIdentity(context.applicationContext, peer.nodeId)
                    }
                }
            val nodeId =
                runCatching { SyncPairing.startAdvertising(context.applicationContext, name) }
                    .getOrNull()
            _state.update { it.copy(localNodeId = nodeId) }
        }
        viewModelScope.launch {
            SyncPairing.pairedPeers(context.applicationContext).collect { peers ->
                _state.update { it.copy(pairedPeers = peers) }
            }
        }
        viewModelScope.launch {
            SyncPull.pendingOffers(context.applicationContext).collect { books ->
                _state.update { it.copy(pendingBooks = books) }
            }
        }
        viewModelScope.launch {
            SyncPull.autoAccept(context.applicationContext).collect { enabled ->
                _state.update { it.copy(autoAcceptBooks = enabled) }
            }
        }
        _state.update { it.copy(storageGranted = SyncPull.hasStorageAccess()) }
        labsJob =
            viewModelScope.launch {
                FeatureFlagsManager.flow(appContext!!).collect { effective ->
                    val buildTime =
                        LabsFlag.ALL.associate { flag ->
                            flag.key to FeatureFlagsManager.buildTimeDefault(flag)
                        }
                    _state.update {
                        it.copy(labsEffective = effective, labsBuildTimeDefaults = buildTime)
                    }
                }
            }
    }

    fun load() {
        // No-op for now: the FFI calls that previously populated
        // paired devices / installed plugins / network addresses
        // are not yet present in the core FFI. Restoring this
        // method is tracked alongside the upstream FFI work.
    }

    fun setTheme(
        context: Context,
        mode: ThemeManager.Mode,
    ) {
        viewModelScope.launch { ThemeManager.setMode(context, mode) }
    }

    fun setLabsFlag(
        flag: LabsFlag,
        enabled: Boolean,
    ) {
        val ctx = appContext ?: return
        viewModelScope.launch { FeatureFlagsManager.setEnabled(ctx, flag, enabled) }
    }

    fun resetLabs() {
        val ctx = appContext ?: return
        viewModelScope.launch { FeatureFlagsManager.reset(ctx) }
    }

    /**
     * Wipes every user-data table in the local DB and re-seeds it with `numWorks` demo books.
     * Debug-only action; the UI is gated by `BuildConfig.DEBUG` in `SettingsScreen`.
     *
     * Cancels any in-flight seed before starting a new one.
     */
    fun resetAndSeed(numWorks: Int) {
        seedJob?.cancel()
        _state.update { it.copy(seedRunning = true, seedResult = null, errorMessage = null) }
        seedJob =
            viewModelScope.launch {
                runCatching { Bridge.resetAndSeed(numWorks) }
                    .onSuccess { result ->
                        _state.update {
                            it.copy(seedRunning = false, seedResult = result, errorMessage = null)
                        }
                    }
                    .onFailure { e ->
                        _state.update {
                            it.copy(
                                seedRunning = false,
                                seedResult = null,
                                errorMessage = e.message ?: e::class.simpleName,
                            )
                        }
                    }
            }
    }

    fun dismissSeedResult() {
        _state.update { it.copy(seedResult = null, errorMessage = null) }
    }

    /** Open the pairing dialog for a discovered [device], node id already filled in. */
    fun beginPairing(device: DiscoveredSyncDevice) = openPairing(PairingTarget(device.nodeId, device.name))

    /**
     * Open the pairing dialog with an empty node id.
     *
     * This is the path that works when mDNS finds nothing — a desktop on another subnet, a network
     * that filters multicast, or a phone that simply cannot see the advertisement. The reader
     * pastes or scans the desktop's node id.
     */
    fun beginManualPairing() = openPairing(PairingTarget("", ""))

    private fun openPairing(target: PairingTarget) {
        val context = appContext ?: return
        pairingJob?.cancel()
        viewModelScope.launch {
            val name = SyncPairing.deviceName(context)
            _state.update { it.copy(pairing = PairingUiState(target, name, PairingStage.Ready)) }
        }
    }

    /** Edit the node id the claim is addressed to. */
    fun updatePairingNodeId(nodeId: String) {
        updatePairing { it.copy(target = it.target.copy(nodeId = nodeId), inputError = null) }
    }

    /** Edit the name that will be filed with the claim. */
    fun updatePairingName(name: String) {
        updatePairing { it.copy(name = name) }
    }

    /**
     * Take a scanned QR payload into the dialog.
     *
     * The payload is returned unparsed by the scanner, so a QR that holds something else — a URL, a
     * book barcode — is refused here with a message instead of being sent to the core as a node id.
     */
    fun onScannedPayload(payload: String) {
        val nodeId = PairPayload.nodeId(payload)
        updatePairing { pairing ->
            if (nodeId == null) {
                pairing.copy(inputError = "That code does not hold a node id.")
            } else {
                pairing.copy(target = pairing.target.copy(nodeId = nodeId), inputError = null)
            }
        }
    }

    private fun updatePairing(change: (PairingUiState) -> PairingUiState) {
        _state.update { state ->
            val pairing = state.pairing ?: return@update state
            if (pairing.stage !is PairingStage.Ready) return@update state
            state.copy(pairing = change(pairing))
        }
    }

    /**
     * File a claim with the node id in the dialog and wait for the desktop to decide.
     *
     * The typed value goes through the same parser as a scanned code, so a pasted
     * `livtet://pair?node=…` link works too, and a value that cannot be a node id fails before any
     * network work.
     */
    fun sendPairingRequest() {
        val context = appContext ?: return
        val pairing = _state.value.pairing ?: return
        if (pairing.stage !is PairingStage.Ready) return
        val nodeId = PairPayload.nodeId(pairing.target.nodeId)
        if (nodeId == null) {
            updatePairing { it.copy(inputError = "Enter the node id shown on the desktop.") }
            return
        }
        if (_state.value.pairedPeers.containsKey(nodeId)) {
            updatePairing { it.copy(inputError = "This device is already paired.") }
            return
        }

        pairingJob?.cancel()
        _state.update {
            it.copy(
                pairing =
                    pairing.copy(
                        target = pairing.target.copy(nodeId = nodeId),
                        stage = PairingStage.Sending,
                        inputError = null,
                    ),
            )
        }
        pairingJob =
            viewModelScope.launch {
                SyncPairing.setDeviceName(context, pairing.name)
                val result =
                    runCatching {
                        SyncPairing.pair(context, nodeId, pairing.target.label, pairing.name)
                    }
                        .getOrElse { error ->
                            updatePairingFor(nodeId, PairingStage.Failed(error.pairingMessage()))
                            return@launch
                        }
                updatePairingFor(nodeId, result.toStage())
                (result as? SyncPairingResult.Pending)?.let { pending ->
                    pollForDecision(context, nodeId, pairing.target.label, pending.claim)
                }
            }
    }

    /**
     * Keep asking the peer about an undecided claim until it answers.
     *
     * The reader approves on the desktop whenever they get to it, so one call that times out is not
     * an answer. Each round waits [SyncPairing.WAIT_SECS] inside the FFI, which keeps the endpoint
     * from being rebound more often than the decision can plausibly change.
     *
     * Polling is not tied to the dialog: closing it must not abandon a claim the desktop may still
     * approve, because the approval writes the session token the paired list reads.
     */
    private fun pollForDecision(
        context: Context,
        nodeId: String,
        label: String,
        claim: String,
    ) {
        pollJob?.cancel()
        pollJob =
            viewModelScope.launch {
                var currentClaim = claim
                repeat(MAX_POLL_ROUNDS) {
                    val result =
                        runCatching { SyncPairing.status(context, nodeId, label, currentClaim) }
                            .getOrElse { error ->
                                updatePairingFor(nodeId, PairingStage.Failed(error.pairingMessage()))
                                return@launch
                            }
                    updatePairingFor(nodeId, result.toStage())
                    val undecided = result as? SyncPairingResult.Pending ?: return@launch
                    currentClaim = undecided.claim
                    delay(POLL_GAP_MS)
                }
                updatePairingFor(
                    nodeId,
                    PairingStage.Failed(
                        "Stopped checking after ${MAX_POLL_ROUNDS} rounds without a decision. " +
                            "Send the request again if the desktop still lists it as waiting.",
                    ),
                )
            }
    }

    /**
     * Apply a stage to the dialog only if it is still showing [nodeId].
     *
     * A result that arrives after the reader closed the dialog is still kept by [SyncPairing] (the
     * token lands in the paired list); it just must not resurrect a dialog for a different peer.
     */
    private fun updatePairingFor(
        nodeId: String,
        stage: PairingStage,
    ) {
        _state.update { state ->
            val pairing = state.pairing ?: return@update state
            if (pairing.target.nodeId != nodeId) return@update state
            state.copy(pairing = pairing.copy(stage = stage))
        }
    }

    /** Drop the dialog state; a filed claim stays valid and keeps being polled. */
    fun dismissPairing() {
        pairingJob?.cancel()
        _state.update { it.copy(pairing = null) }
    }

    /** Ask paired peers for offers now instead of waiting for the interval. */
    fun checkForBooks() {
        val context = appContext ?: return
        viewModelScope.launch {
            runCatching { SyncPull.pullAll(context) }
            _state.update { it.copy(storageGranted = SyncPull.hasStorageAccess()) }
        }
    }

    /** Re-read the all-files grant after the reader returns from system settings. */
    fun refreshStorageGrant() {
        _state.update { it.copy(storageGranted = SyncPull.hasStorageAccess()) }
    }

    /** Download an offered book and register it in the library. */
    fun acceptBook(book: PendingBook) {
        val context = appContext ?: return
        viewModelScope.launch {
            runCatching { SyncPull.accept(context, book) }
                .onFailure { error ->
                    _state.update {
                        it.copy(errorMessage = error.message ?: "could not receive the book")
                    }
                }
        }
    }

    /** Decline an offer; the book stays on the peer. */
    fun rejectBook(book: PendingBook) {
        val context = appContext ?: return
        viewModelScope.launch { runCatching { SyncPull.reject(context, book) } }
    }

    /**
     * Receive every book [nodeId] is offering, one download at a time.
     *
     * The reader asked for everything that peer has queued, so the books arrive back to back and
     * anything that failed is reported together rather than as one dialog per book.
     */
    fun receiveAllFromPeer(nodeId: String) {
        val context = appContext ?: return
        viewModelScope.launch {
            _state.update {
                it.copy(receivingFromPeer = nodeId, errorMessage = null, transfer = null)
            }
            val failures =
                runCatching {
                    SyncPull.acceptAllFrom(context, nodeId) { progress ->
                        _state.update { it.copy(transfer = progress) }
                    }
                }
                    .getOrElse { error -> listOf(error.message ?: "could not receive the books") }
            _state.update {
                it.copy(
                    receivingFromPeer = null,
                    transfer = null,
                    errorMessage =
                        failures.takeIf { lines -> lines.isNotEmpty() }?.joinToString("\n"),
                )
            }
        }
    }

    /** Accept offers from paired peers without asking. */
    fun setAutoAcceptBooks(enabled: Boolean) {
        val context = appContext ?: return
        viewModelScope.launch { SyncPull.setAutoAccept(context, enabled) }
    }

    /**
     * Forget an approved pairing on this device.
     *
     * Local half only: the session token is deleted here, which is what the app authenticates with.
     * Nothing on the wire revokes a decided pairing, so the desktop keeps its own record until the
     * reader removes it there.
     */
    fun forgetPairedPeer(nodeId: String) {
        val context = appContext ?: return
        viewModelScope.launch { SyncPairing.forgetPeer(context, nodeId) }
    }

    override fun onCleared() {
        super.onCleared()
        discoveryJob?.cancel()
        labsJob?.cancel()
        seedJob?.cancel()
        pairingJob?.cancel()
        appContext?.let { SyncPairing.stopAdvertising(it) }
        pollJob?.cancel()
        discoveryService?.stop()
        discoveryService = null
    }
}

/** How long to wait between approval checks; the FFI call itself waits inside. */
private const val POLL_GAP_MS = 2_000L

/**
 * Rounds to check before giving up, at roughly `SyncPairing.WAIT_SECS` each. Twenty rounds is about
 * ten minutes — long enough for a reader to walk over to the desktop, short enough not to poll a
 * claim nobody is going to answer.
 */
private const val MAX_POLL_ROUNDS = 20

private fun SyncPairingResult.toStage(): PairingStage =
    when (this) {
        is SyncPairingResult.Paired -> PairingStage.Paired
        is SyncPairingResult.Refused -> PairingStage.Refused(reason)
        is SyncPairingResult.Pending -> PairingStage.AwaitingDecision(claim)
    }

/**
 * The message to show for a failed pairing call.
 *
 * `LivtetException.message` is the generated `v1=<payload>` form, which is noise to a reader; the
 * `Sync` variant exposes the Rust-side text as its payload, so that is what the dialog shows.
 */
private fun Throwable.pairingMessage(): String =
    when (this) {
        is LivtetException.Sync -> v1
        else -> message ?: this::class.simpleName.orEmpty()
    }
