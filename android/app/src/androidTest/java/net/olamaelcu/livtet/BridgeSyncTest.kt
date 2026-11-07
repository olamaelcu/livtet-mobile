package net.olamaelcu.livtet

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The bridge's peer-sync surface, exercised on a device.
 *
 * These calls cross the UniFFI boundary into real code — an endpoint is bound, a
 * key file is written, and the pairing link is formatted by the same crate the
 * desktop renders from — so a wrong argument name or a mismatched type shows up
 * here rather than as a crash on the sync screen.
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
    fun pairingLinkRoundTripsThroughTheBridge() =
        runBlocking {
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
    fun aLegacyPairLinkStillParses() =
        runBlocking {
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

    private companion object {
        const val DEVICE_ID = "01ARZ3NDEKTSV4RRFFQ69G5FAW"
    }
}
