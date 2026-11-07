package net.olamaelcu.livtet.sync

import net.olamaelcu.livtet.DiscoveredSyncDevice

/**
 * What the discovery list should show, given who is on the network.
 *
 * [visible] are the peers worth offering to pair with. [othersOnNetwork] says whether anything was
 * heard at all, so the UI can tell "nobody is here" apart from "everybody here is already paired" —
 * both leave the list empty, and the two are very different things to tell a reader.
 */
data class PeerVisibility(val visible: List<DiscoveredSyncDevice>, val othersOnNetwork: Boolean)

/**
 * Split the discovered peers into the ones to offer and the ones to keep out.
 *
 * Three kinds are hidden, all of them for the same reason — pairing with them cannot succeed or is
 * already done:
 *
 * - this device's own advertisement (`ownNodeId`), which a browsing device hears;
 * - another install of the app on this device, which advertises the same generated name with its
 *   own ids (`ownDeviceId`, `ownDeviceName`);
 * - anything already paired, which the paired list already shows with its token.
 */
fun visiblePeers(
    devices: List<DiscoveredSyncDevice>,
    pairedNodeIds: Set<String>,
    ownNodeId: String?,
    ownDeviceId: String,
    ownDeviceName: String,
): PeerVisibility {
    val others =
        devices.filterNot { peer ->
            peer.nodeId == ownNodeId ||
                peer.deviceId == ownDeviceId ||
                (ownDeviceName.isNotBlank() && peer.name == ownDeviceName)
        }
    return PeerVisibility(
        visible = others.filterNot { peer -> peer.nodeId in pairedNodeIds },
        othersOnNetwork = others.isNotEmpty(),
    )
}
