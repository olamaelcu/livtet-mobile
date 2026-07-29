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
import net.olamaelcu.livtet.ffi.InstalledPluginMobile
import net.olamaelcu.livtet.ffi.NetworkAddressesMobile
import net.olamaelcu.livtet.ffi.PairedDeviceMobile

/**
 * State for the Settings screen.
 *
 * `pairedDevices` and `installedPlugins` start empty and populate
 * via the matching `load*` call. `networkAddresses` is filled by
 * the same load — it doubles as a connectivity hint ("is the device
 * on a LAN?") for the user.
 *
 * `labsEffective` is the map of `LabsFlag.key -> Boolean` after
 * both gates (build-time + persisted override) have been applied.
 * `labsBuildTimeDefaults` mirrors the `LabsFlag.key -> Boolean`
 * map from `BuildConfig.LABS_BUILD_TIME_DEFAULTS_JSON`, so the UI
 * can render a flag as "locked by this build" when the gate is off
 * without re-reading `BuildConfig` from the compose layer.
 */
data class SettingsUiState(
    val pairedDevices: List<PairedDeviceMobile> = emptyList(),
    val discoveredDevices: List<DiscoveredSyncDevice> = emptyList(),
    val installedPlugins: List<InstalledPluginMobile> = emptyList(),
    val networkAddresses: NetworkAddressesMobile? = null,
    val labsEffective: Map<String, Boolean> = emptyMap(),
    val labsBuildTimeDefaults: Map<String, Boolean> = emptyMap(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

/**
 * Drives the Settings screen. Holds the `MutableStateFlow` that
 * Compose collects and the suspending functions that call the
 * `Bridge` (and thus the Rust FFI) to load and mutate the data.
 *
 * Each call to `load*` updates only the slice it owns. We do not
 * use a single "refresh" entry point because the only mutation
 * (`unpairDevice`) should reload `pairedDevices` only — we don't
 * need to re-fetch the installed-plugin list just because a
 * device was removed.
 */
class SettingsViewModel : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    // mDNS discovery — created lazily from a Context the UI passes
    // in via [attachDiscovery]. Held as `@Volatile` because startup
    // happens on the main thread but the collector runs on
    // viewModelScope's dispatcher.
    @Volatile
    private var discoveryService: DiscoveryService? = null
    private var discoveryJob: Job? = null
    private var labsJob: Job? = null
    private var labsContext: Context? = null

    /**
     * Wire up mDNS discovery using the host Activity/Application
     * Context. Idempotent — calling twice is a no-op. Starts the
     * NSD browser immediately and begins collecting discovered
     * devices into [state].
     *
     * This is split out from [load] because the ViewModel doesn't
     * otherwise have a Context reference, and we don't want to
     * inject Android framework types through the constructor
     * (keeps the ViewModel testable and rotation-safe).
     */
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
        // Cache the application Context so `setLabsFlag` /
        // `resetLabs` can write to DataStore without requiring
        // the caller to thread a Context through every UI
        // callback. Application context is safe for DataStore
        // writes — there is no Activity lifecycle dependency.
        labsContext = context.applicationContext
        labsJob = viewModelScope.launch {
            FeatureFlagsManager.flow(labsContext!!).collect { effective ->
                val buildTime = LabsFlag.ALL.associate { flag ->
                    flag.key to FeatureFlagsManager.buildTimeDefault(flag)
                }
                _state.update {
                    it.copy(labsEffective = effective, labsBuildTimeDefaults = buildTime)
                }
            }
        }
    }

    fun load() {
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                val paired = Bridge.getPairedDevices()
                val plugins = Bridge.listInstalledPlugins()
                val addresses = Bridge.getNetworkAddresses()
                _state.update {
                    it.copy(
                        pairedDevices = paired,
                        installedPlugins = plugins,
                        networkAddresses = addresses,
                        isLoading = false,
                    )
                }
            } catch (e: Throwable) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Failed to load settings: ${e.message}",
                    )
                }
            }
        }
    }

    /**
     * Manually pair a device by address. Used when mDNS discovery
     * isn't available (CI, sandboxed networks) or the user
     * prefers to type the address directly. On success the device
     * shows up in the Paired Devices section and the
     * `pairedDevices` state is refreshed.
     */
    fun pairDevice(
        name: String,
        address: String,
        port: Int,
        deviceType: String,
    ) {
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                val paired = Bridge.pairDevice(name, address, port, deviceType)
                _state.update {
                    it.copy(
                        pairedDevices = it.pairedDevices + paired,
                        isLoading = false,
                    )
                }
            } catch (e: Throwable) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Pairing failed: ${e.message}",
                    )
                }
            }
        }
    }

    fun unpairDevice(id: net.olamaelcu.livtet.ffi.DbId) {
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                Bridge.unpairDevice(id)
                _state.update {
                    it.copy(
                        pairedDevices = it.pairedDevices.filter { d -> d.deviceId != id },
                        isLoading = false,
                    )
                }
            } catch (e: Throwable) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Unpair failed: ${e.message}",
                    )
                }
            }
        }
    }

    fun togglePlugin(plugin: InstalledPluginMobile, enabled: Boolean) {
        viewModelScope.launch {
            try {
                Bridge.setPluginEnabled(plugin.pluginId, enabled)
                _state.update { current ->
                    current.copy(
                        installedPlugins = current.installedPlugins.map { p ->
                            if (p.pluginId == plugin.pluginId) {
                                InstalledPluginMobile(
                                    id = p.id,
                                    pluginId = p.pluginId,
                                    name = p.name,
                                    version = p.version,
                                    enabled = enabled,
                                    sourcePath = p.sourcePath,
                                )
                            } else p
                        }
                    )
                }
            } catch (e: Throwable) {
                _state.update { it.copy(errorMessage = "Toggle failed: ${e.message}") }
            }
        }
    }

    /** Apply a new theme preference. UI-only — written to DataStore. */
    fun setTheme(context: Context, mode: ThemeManager.Mode) {
        viewModelScope.launch { ThemeManager.setMode(context, mode) }
    }

    /**
     * Persist a Labs override. The DataStore write is a no-op if
     * the flag is locked off by the build-time gate — that's
     * intentional: a locked-off flag should not let the user
     * "set" it on, only appear disabled in the UI.
     */
    fun setLabsFlag(flag: LabsFlag, enabled: Boolean) {
        val ctx = labsContext ?: return
        viewModelScope.launch { FeatureFlagsManager.setEnabled(ctx, flag, enabled) }
    }

    /**
     * Wipe every Labs override. Each flag falls back to its
     * `LabsFlag.defaultEnabled` (then AND'd with the build-time
     * gate by the resolver). No-op if [attachDiscovery] hasn't
     * run yet — there's nowhere to write to.
     */
    fun resetLabs() {
        val ctx = labsContext ?: return
        viewModelScope.launch { FeatureFlagsManager.reset(ctx) }
    }

    override fun onCleared() {
        super.onCleared()
        // Cancel the collector before stopping so we don't race
        // with a final emit on a dead scope.
        discoveryJob?.cancel()
        labsJob?.cancel()
        discoveryService?.stop()
        discoveryService = null
    }
}
