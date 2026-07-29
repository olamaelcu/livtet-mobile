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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Settings screen. Three sections previously existed — Paired
 * Devices, Plugins, and Labs/Appearance. The Paired Devices and
 * Plugins sections depended on `Bridge.getPairedDevices`,
 * `Bridge.listInstalledPlugins`, `Bridge.getNetworkAddresses`,
 * `Bridge.pairDevice`, `Bridge.unpairDevice`, and
 * `Bridge.setPluginEnabled` — none of which are present in the
 * current `core/livtet-ffi` crate. Those sections are removed
 * until upstream catches up; Labs and Appearance remain.
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

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Settings") })
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    EmptyStateHint(
                        "Paired Devices, Plugins, and Pair New Device are unavailable in this build — the Rust core does not yet expose the supporting FFI calls. They will return when upstream livtet-ffi is restored."
                    )
                }

                item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

                item { SectionHeader("Labs") }
                item {
                    EmptyStateHint(
                        "Experimental features. Off by default; some may be locked by your build."
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
            verticalAlignment = Alignment.CenterVertically,
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
                        text = context.getString(net.olamaelcu.livtet.R.string.labs_locked_by_build),
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