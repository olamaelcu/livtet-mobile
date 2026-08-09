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
import net.olamaelcu.livtet.ffi.SeedResultMobile

/**
 * State for the Settings screen.
 *
 * The Paired Devices, Discovered on this network, and Plugins sections previously relied on
 * `Bridge.getPairedDevices`, `Bridge.listInstalledPlugins`, `Bridge.getNetworkAddresses`,
 * `Bridge.pairDevice`, `Bridge.unpairDevice`, and `Bridge.setPluginEnabled` — none of which are
 * present in the current `core/livtet-ffi` crate. Those sections are dropped from the screen until
 * upstream catches up. Labs (DataStore) and Appearance (DataStore) still work and remain.
 *
 * `discoveredDevices` is kept so the mDNS DiscoveryService can still populate state; it's just not
 * surfaced in the UI yet.
 */
data class SettingsUiState(
    val discoveredDevices: List<DiscoveredSyncDevice> = emptyList(),
    val labsEffective: Map<String, Boolean> = emptyMap(),
    val labsBuildTimeDefaults: Map<String, Boolean> = emptyMap(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val seedRunning: Boolean = false,
    val seedResult: SeedResultMobile? = null,
)

class SettingsViewModel : ViewModel() {
    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    @Volatile private var discoveryService: DiscoveryService? = null
    private var discoveryJob: Job? = null
    private var labsJob: Job? = null
    private var seedJob: Job? = null
    private var labsContext: Context? = null

    fun attachDiscovery(context: Context) {
        if (discoveryService != null) return
        val service = DiscoveryService(context.applicationContext)
        discoveryService = service
        service.start()
        discoveryJob = viewModelScope.launch {
            service.devices.collect { devices ->
                _state.update { it.copy(discoveredDevices = devices) }
            }
        }
        labsContext = context.applicationContext
        labsJob = viewModelScope.launch {
            FeatureFlagsManager.flow(labsContext!!).collect { effective ->
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
        val ctx = labsContext ?: return
        viewModelScope.launch { FeatureFlagsManager.setEnabled(ctx, flag, enabled) }
    }

    fun resetLabs() {
        val ctx = labsContext ?: return
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

    override fun onCleared() {
        super.onCleared()
        discoveryJob?.cancel()
        labsJob?.cancel()
        seedJob?.cancel()
        discoveryService?.stop()
        discoveryService = null
    }
}
