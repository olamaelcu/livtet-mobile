package net.olamaelcu.livtet

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import net.olamaelcu.livtet.ffi.SyncPairedDevice
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The bridge's peer-sync surface, exercised on a device.
 *
 * These calls cross the UniFFI boundary into real code — an endpoint is bound, a key file is
 * written, and the pairing link is formatted by the same crate the desktop renders from — so a
 * wrong argument name or a mismatched type shows up here rather than as a crash on the sync screen.
 */
@RunWith(AndroidJUnit4::class)
class BridgeSyncTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val databasePath = File(context.filesDir, "bridge-sync.db").absolutePath

    @After
    fun tearDown() {
        Bridge.stopAdvertising()
    }

    @Test
    fun pairingLinkRoundTripsThroughTheBridge() = runBlocking {
        Bridge.init(databasePath)

        val nodeId = Bridge.advertiseForSync(DEVICE_ID, "Test phone")
        assertTrue("an advertised device has a node id", nodeId.isNotEmpty())

        val link = Bridge.pairingLink("Test phone")
        assertTrue(
            "the link names the node it was built for: $link",
            link.contains("nodeId=$nodeId"),
        )

        val parsed = Bridge.parsePairingLink(link)
        assertEquals(nodeId, parsed.nodeId)
        assertEquals("Test phone", parsed.name)
        assertEquals(2.toUShort(), parsed.version)
    }

    @Test
    fun aLegacyPairLinkStillParses() = runBlocking {
        Bridge.init(databasePath)

        val parsed = Bridge.parsePairingLink("livtet://pair?node=$DEVICE_ID")
        assertEquals(DEVICE_ID, parsed.nodeId)
        assertEquals(null, parsed.name)
    }

    @Test
    fun anUnknownLinkIsRejected() {
        var rejected = false
        try {
            Bridge.parsePairingLink("https://livtet.olamaelcu.net/book/abc")
        } catch (error: Exception) {
            rejected = true
        }
        assertTrue("a permalink is not a pairing link", rejected)
    }

    /**
     * The app-start path is what keeps a phone pullable, so it must bind only when there is a peer
     * to serve: an unpaired install has nothing to answer with and must not open an endpoint.
     */
    @Test
    fun anUnpairedAppStartBindsNothing() = runBlocking {
        Bridge.init(databasePath)

        val nodeId = SyncAdvertisement.advertise { emptyList() }
        assertNull("an unpaired app start has no node id to give out", nodeId)
        assertFalse(
            "nothing answers, so a claim cannot even be listed",
            runCatching { Bridge.pendingPairings() }.isSuccess,
        )
    }

    /** With a peer on record the same path binds the endpoint a pull needs. */
    @Test
    fun aPairedAppStartBindsTheEndpoint() = runBlocking {
        Bridge.init(databasePath)
        val peer =
            SyncPairedDevice(
                deviceId = "01ARZ3NDEKTSV4RRFFQ69G5FAW",
                nodeId = "peer-node-id",
                name = "Desktop",
                outbound = false,
            )

        val nodeId = SyncAdvertisement.advertise { listOf(peer) }
        assertNotNull("a paired app start advertises", nodeId)
        assertTrue(
            "the endpoint answers once it is advertised",
            runCatching { Bridge.pendingPairings() }.isSuccess,
        )
    }

    private companion object {
        const val DEVICE_ID = "01ARZ3NDEKTSV4RRFFQ69G5FAW"
    }
}
