package net.olamaelcu.livtet.sync

import net.olamaelcu.livtet.DiscoveredSyncDevice
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SyncPeersTest {
    private val ownNodeId = "a7fe4952e461f9395d6e0cab664382ff"
    private val ownDeviceId = "01M6HC6S1Q1H070FT7NS5WN4SQ"
    private val ownName = "Livtet on sdk_gphone_x86_64"

    private fun peer(
        nodeId: String,
        deviceId: String = "01ARZ3NDEKTSV4RRFFQ69G5FAV",
        name: String = "Josh's MacBook",
    ) = DiscoveredSyncDevice(
        nodeId = nodeId,
        deviceId = deviceId,
        name = name,
        host = "192.168.1.20",
        port = 41234,
        appVersion = "0.2.1",
        deviceFlavor = "desktop",
    )

    private fun visibility(
        devices: List<DiscoveredSyncDevice>,
        paired: Set<String> = emptySet(),
    ) = visiblePeers(
        devices = devices,
        pairedNodeIds = paired,
        ownNodeId = ownNodeId,
        ownDeviceId = ownDeviceId,
        ownDeviceName = ownName,
    )

    @Test
    fun `a peer already paired is not offered again`() {
        val paired = peer("11".repeat(32))
        val fresh = peer("22".repeat(32), name = "Other Desktop")

        val result = visibility(listOf(paired, fresh), paired = setOf(paired.nodeId))

        assertEquals(listOf(fresh), result.visible)
        assertTrue(result.othersOnNetwork, "the paired peer is still on the network")
    }

    @Test
    fun `a network with only paired peers reports that rather than silence`() {
        val only = peer("33".repeat(32))

        val result = visibility(listOf(only), paired = setOf(only.nodeId))

        assertEquals(emptyList(), result.visible)
        assertTrue(
            result.othersOnNetwork,
            "the UI says 'already paired', not 'no devices', when something answered",
        )
    }

    @Test
    fun `an empty network is silent`() {
        val result = visibility(emptyList())

        assertEquals(emptyList(), result.visible)
        assertFalse(result.othersOnNetwork)
    }

    @Test
    fun `this device and its other installs are never offered`() {
        val self = peer(ownNodeId)
        val sameDevice = peer("44".repeat(32), deviceId = ownDeviceId)
        val sameName = peer("55".repeat(32), name = ownName)
        val desk = peer("66".repeat(32), name = "Josh's MacBook")

        val result = visibility(listOf(self, sameDevice, sameName, desk))

        assertEquals(listOf(desk), result.visible)
        assertTrue(result.othersOnNetwork)
    }
}
