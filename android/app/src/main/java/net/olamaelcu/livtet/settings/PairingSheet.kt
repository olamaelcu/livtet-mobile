package net.olamaelcu.livtet.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import net.olamaelcu.livtet.ffi.NetworkAddressesMobile

/**
 * Manual device-pairing sheet. Lets the user pair a device by
 * entering its address and port directly. Used when mDNS discovery
 * isn't available (CI, sandboxed networks, restricted LANs) or
 * the user prefers to skip the auto-discovery path.
 *
 * The networkAddresses parameter is non-null only when the host
 * already ran `getNetworkAddresses` — we surface the list as
 * prefill suggestions so the user doesn't have to remember the
 * device's IP.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairingSheet(
    onDismiss: () -> Unit,
    onPair: (name: String, address: String, port: Int, deviceType: String) -> Unit,
    networkAddresses: NetworkAddressesMobile?,
    initialName: String = "Desktop",
    initialAddress: String = "",
    initialPort: Int = 3120,
    initialDeviceType: String = "desktop",
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var name by remember { mutableStateOf(initialName) }
    var address by remember { mutableStateOf(initialAddress) }
    var portText by remember { mutableStateOf(initialPort.toString()) }
    var deviceType by remember { mutableStateOf(initialDeviceType) }
    var addressError by remember { mutableStateOf<String?>(null) }

    val port = portText.toIntOrNull()
    val canPair = address.isNotBlank() && (port != null && port in 1..65535) && name.isNotBlank()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Text(
                "Pair New Device",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = address,
                onValueChange = { address = it; addressError = null },
                label = { Text("Address") },
                placeholder = { Text("e.g. 192.168.1.42") },
                isError = addressError != null,
                supportingText = { addressError?.let { Text(it) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next
                ),
            )
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = portText,
                onValueChange = { portText = it.filter { c -> c.isDigit() } },
                label = { Text("Port") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next
                ),
            )
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = deviceType,
                onValueChange = { deviceType = it.lowercase().filter { c -> c.isLetter() } },
                label = { Text("Device Type") },
                placeholder = { Text("desktop, mobile, web, ereader") },
                supportingText = { Text("e.g. desktop, mobile, web, ereader, koreader, kobo, kindle") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            )

            networkAddresses?.let { addrs ->
                if (addrs.addresses.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Local interfaces (tap to fill address):",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        addrs.addresses.forEach { addr ->
                            TextButton(onClick = { address = addr }) {
                                Text(addr, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        addressError = validateAddress(address)
                        if (addressError == null && port != null) {
                            onPair(name, address, port, deviceType)
                        }
                    },
                    enabled = canPair,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Pair")
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

private fun validateAddress(addr: String): String? {
    if (addr.isBlank()) return "Address is required"
    val parts = addr.split('.')
    if (parts.size != 4) return "Must be an IPv4 address (x.x.x.x)"
    parts.forEach { p ->
        val n = p.toIntOrNull() ?: return "Invalid octet: $p"
        if (n < 0 || n > 255) return "Octet out of range: $n"
    }
    return null
}
