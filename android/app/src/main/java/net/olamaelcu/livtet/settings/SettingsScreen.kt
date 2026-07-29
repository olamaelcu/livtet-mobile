package net.olamaelcu.livtet.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import net.olamaelcu.livtet.DiscoveredSyncDevice
import net.olamaelcu.livtet.R
import net.olamaelcu.livtet.ffi.InstalledPluginMobile
import net.olamaelcu.livtet.ffi.PairedDeviceMobile

/**
 * Top-level Settings screen. Renders four sections:
 *
 * - Paired Devices: list + manual entry sheet
 * - Plugins: per-plugin enable/disable toggles
 * - Labs: experimental feature flags with build-time gates
 * - Appearance: theme dropdown
 *
 * The Paired Devices section shows mDNS-discovered devices on top
 * and a "Pair New Device" button at the bottom that opens the
 * `PairingSheet` (see [PairingSheet] for the manual entry flow).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var showPairSheet by remember { mutableStateOf(false) }
    // Prefill values for the PairingSheet. Set when the user
    // taps a discovered device (or the manual "Pair New Device"
    // button, which uses the defaults).
    var prefillName by remember { mutableStateOf("Desktop") }
    var prefillAddress by remember { mutableStateOf("") }
    var prefillPort by remember { mutableStateOf(3120) }
    var prefillDeviceType by remember { mutableStateOf("desktop") }
    val themeMode by ThemeManager.mode(context).collectAsState(initial = ThemeManager.Mode.SYSTEM)

    LaunchedEffect(Unit) {
        viewModel.load()
        viewModel.attachDiscovery(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                actions = {
                    IconButton(onClick = { viewModel.load() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reload")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (state.isLoading) {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            state.errorMessage?.let { err ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(err, modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    SectionHeader("Paired Devices")
                }
                if (state.pairedDevices.isEmpty()) {
                    item {
                        EmptyStateHint(
                            "No devices paired yet. Use Pair New Device to add a desktop, mobile, or e-reader by IP address."
                        )
                    }
                }
                items(state.pairedDevices, key = { it.deviceId.toString() }) { d ->
                    PairedDeviceRow(d, onUnpair = { viewModel.unpairDevice(d.deviceId) })
                }
                item {
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            // Reset to defaults so the user gets a
                            // clean sheet rather than whatever they
                            // last tapped from discovery.
                            prefillName = "Desktop"
                            prefillAddress = ""
                            prefillPort = 3120
                            prefillDeviceType = "desktop"
                            showPairSheet = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("Pair New Device")
                    }
                }

                item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

                item { SectionHeader("Discovered on this network") }
                if (state.discoveredDevices.isEmpty()) {
                    item {
                        EmptyStateHint(
                            "No livtet sync peers found on this network yet."
                        )
                    }
                }
                items(state.discoveredDevices, key = { "${it.deviceId}:${it.host}:${it.port}" }) { d ->
                    DiscoveredDeviceRow(d) {
                        prefillName = d.name.ifEmpty { "livtet peer" }
                        prefillAddress = d.host
                        prefillPort = d.port
                        prefillDeviceType = d.deviceFlavor ?: "desktop"
                        showPairSheet = true
                    }
                }

                item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

                item { SectionHeader("Plugins") }
                if (state.installedPlugins.isEmpty()) {
                    item {
                        EmptyStateHint(
                            "No plugins installed yet."
                        )
                    }
                }
                items(state.installedPlugins, key = { it.pluginId }) { p ->
                    PluginRow(p, onToggle = { viewModel.togglePlugin(p, it) })
                }

                item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

                item { SectionHeader("Labs") }
                item {
                    EmptyStateHint(
                        "Experimental features. Off by default; some may be locked by your build."
                    )
                }
                items(LabsFlag.ALL, key = { it.key }) { flag ->
                    LabsRow(
                        flag = flag,
                        effective = state.labsEffective[flag.key] ?: flag.defaultEnabled,
                        buildTimeDefault = state.labsBuildTimeDefaults[flag.key] ?: true,
                        onToggle = { viewModel.setLabsFlag(flag, it) },
                    )
                }
                item {
                    TextButton(
                        onClick = { viewModel.resetLabs() },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Reset to defaults") }
                }

                item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

                item { SectionHeader("Appearance") }
                item { ThemeSection(current = themeMode, onChange = { viewModel.setTheme(context, it) }) }
            }
        }
    }

    if (showPairSheet) {
        PairingSheet(
            onDismiss = { showPairSheet = false },
            onPair = { name, address, port, deviceType ->
                viewModel.pairDevice(name, address, port, deviceType)
                showPairSheet = false
            },
            networkAddresses = state.networkAddresses,
            initialName = prefillName,
            initialAddress = prefillAddress,
            initialPort = prefillPort,
            initialDeviceType = prefillDeviceType,
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
    )
}

@Composable
private fun EmptyStateHint(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(12.dp)
    )
}

@Composable
private fun PairedDeviceRow(
    device: PairedDeviceMobile,
    onUnpair: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(device.name.ifEmpty { "Unnamed" }, fontWeight = FontWeight.Medium)
                Text(
                    "${device.deviceType} • ${device.listenOn}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onUnpair) {
                Icon(Icons.Default.Delete, contentDescription = "Unpair")
            }
        }
    }
}

@Composable
private fun DiscoveredDeviceRow(
    device: DiscoveredSyncDevice,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    device.name.ifEmpty { "Unnamed peer" },
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                val flavor = device.deviceFlavor?.takeIf { it.isNotBlank() }
                val subtitle = buildString {
                    append(device.host)
                    append(":")
                    append(device.port)
                    if (flavor != null) append(" • $flavor")
                }
                Text(
                    subtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

@Composable
private fun PluginRow(
    plugin: InstalledPluginMobile,
    onToggle: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(plugin.name, fontWeight = FontWeight.Medium)
                Text(
                    "v${plugin.version}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = plugin.enabled, onCheckedChange = onToggle)
        }
    }
}

/**
 * One Labs / experimental-feature row. Renders the flag's title
 * and description; the `Switch` is disabled (greyed-out,
 * non-interactive) when the build-time gate for the flag is off,
 * matching the "Locked by this build" copy shown beneath the
 * label. When unlocked, the switch reflects the effective value
 * (build-time gate AND persisted override) and writes through to
 * the DataStore via [onToggle].
 */
@Composable
private fun LabsRow(
    flag: LabsFlag,
    effective: Boolean,
    buildTimeDefault: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val context = LocalContext.current
            Column(Modifier.weight(1f)) {
                Text(
                    text = context.getString(flag.titleRes),
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = context.getString(flag.descriptionRes),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!buildTimeDefault) {
                    Text(
                        text = context.getString(R.string.labs_locked_by_build),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            Switch(
                checked = effective,
                onCheckedChange = onToggle,
                enabled = buildTimeDefault,
            )
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
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Theme", modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.size(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeOptionChip("System", current == ThemeManager.Mode.SYSTEM) { onChange(ThemeManager.Mode.SYSTEM) }
                ThemeOptionChip("Light", current == ThemeManager.Mode.LIGHT) { onChange(ThemeManager.Mode.LIGHT) }
                ThemeOptionChip("Dark", current == ThemeManager.Mode.DARK) { onChange(ThemeManager.Mode.DARK) }
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
    val bg = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface
    val fg = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurface
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = bg),
    ) {
        Text(label, color = fg, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
    }
}
