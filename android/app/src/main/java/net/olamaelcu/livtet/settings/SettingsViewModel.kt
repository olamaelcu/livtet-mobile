package net.olamaelcu.livtet.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.olamaelcu.livtet.Bridge
import net.olamaelcu.livtet.DiscoveredSyncDevice
import net.olamaelcu.livtet.DiscoveryService
import net.olamaelcu.livtet.SeedResultMobile
import net.olamaelcu.livtet.ffi.SyncPairingResult
import net.olamaelcu.livtet.ffi.LivtetException
import net.olamaelcu.livtet.sync.PairedPeer
import net.olamaelcu.livtet.sync.SyncPairing

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
    val labsEffective: Map<String, Boolean> = emptyMap(),
    val labsBuildTimeDefaults: Map<String, Boolean> = emptyMap(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val seedRunning: Boolean = false,
    val seedResult: SeedResultMobile? = null,
)

/** An in-flight or settled pairing attempt, owned by the dialog that started it. */
data class PairingUiState(
    val peer: DiscoveredSyncDevice,
    /** Name filed with the claim; editable until the request is sent. */
    val name: String,
    val stage: PairingStage,
)

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
    private var appContext: Context? = null

    fun attachDiscovery(context: Context) {
        if (discoveryService != null) return
        val service = DiscoveryService(context.applicationContext)
        discoveryService = service
        service.start()
        viewModelScope.launch {
            service.devices.collect { devices ->
                _state.update {
                    it.copy(discoveredDevices = devices)
                }
            }
        }
        appContext = context.applicationContext
        viewModelScope.launch {
            SyncPairing.pairedPeers(context.applicationContext).collect { peers ->
                _state.update { it.copy(pairedPeers = peers) }
            }
        }
        labsJob = viewModelScope.launch {
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

    fun setTheme(context: Context, mode: ThemeManager.Mode) {
        viewModelScope.launch { ThemeManager.setMode(context, mode) }
    }

    fun setLabsFlag(flag: LabsFlag, enabled: Boolean) {
        val ctx = appContext ?: return
        viewModelScope.launch { FeatureFlagsManager.setEnabled(ctx, flag, enabled) }
    }

    fun resetLabs() {
        val ctx = appContext ?: return
        viewModelScope.launch { FeatureFlagsManager.reset(ctx) }
    }

    /**
     * Wipes every user-data table in the local DB and re-seeds it with
     * `numWorks` demo books. Debug-only action; the UI is gated by
     * `BuildConfig.DEBUG` in `SettingsScreen`.
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

    /** Open the pairing dialog for [device] with this device's saved name. */
    fun beginPairing(device: DiscoveredSyncDevice) {
        val context = appContext ?: return
        pairingJob?.cancel()
        viewModelScope.launch {
            val name = SyncPairing.deviceName(context)
            _state.update { it.copy(pairing = PairingUiState(device, name, PairingStage.Ready)) }
        }
    }

    /** Edit the name that will be filed with the claim. */
    fun updatePairingName(name: String) {
        _state.update { state ->
            val pairing = state.pairing ?: return@update state
            if (pairing.stage !is PairingStage.Ready) return@update state
            state.copy(pairing = pairing.copy(name = name))
        }
    }

    /**
     * File a pairing claim with [device] and wait for the desktop to decide.
     *
     * The name is what the desktop shows while approving; it is stored for the
     * next attempt once the claim is filed.
     */
    fun pairDevice(device: DiscoveredSyncDevice, name: String) {
        val context = appContext ?: return
        pairingJob?.cancel()
        _state.update { it.copy(pairing = PairingUiState(device, name, PairingStage.Sending)) }
        pairingJob =
            viewModelScope.launch {
                SyncPairing.setDeviceName(context, name)
                val stage =
                    runCatching { SyncPairing.pair(context, device, name) }
                        .fold(
                            onSuccess = { it.toStage() },
                            onFailure = { PairingStage.Failed(it.pairingMessage()) },
                        )
                _state.update { it.copy(pairing = it.pairing?.copy(stage = stage)) }
            }
    }

    /** Keep waiting on the claim the current attempt already filed. */
    fun checkPairingAgain() {
        val context = appContext ?: return
        val pairing = _state.value.pairing ?: return
        val awaiting = pairing.stage as? PairingStage.AwaitingDecision ?: return
        pairingJob?.cancel()
        _state.update { it.copy(pairing = pairing.copy(stage = PairingStage.Sending)) }
        pairingJob =
            viewModelScope.launch {
                val stage =
                    runCatching {
                            SyncPairing.status(
                                context,
                                pairing.peer,
                                pairing.name,
                                awaiting.claim,
                            )
                        }
                        .fold(
                            onSuccess = { it.toStage() },
                            onFailure = { PairingStage.Failed(it.pairingMessage()) },
                        )
                _state.update { it.copy(pairing = it.pairing?.copy(stage = stage)) }
            }
    }

    /** Drop the dialog state; a filed claim stays valid on the desktop. */
    fun dismissPairing() {
        pairingJob?.cancel()
        _state.update { it.copy(pairing = null) }
    }

    override fun onCleared() {
        super.onCleared()
        discoveryJob?.cancel()
        labsJob?.cancel()
        seedJob?.cancel()
        pairingJob?.cancel()
        discoveryService?.stop()
        discoveryService = null
    }
}

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
