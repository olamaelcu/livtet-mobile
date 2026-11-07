package net.olamaelcu.livtet

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Where pairing information lives.
 *
 * It goes in the library, not a preferences file: the token a peer issued is what authorizes an
 * outbound pull, and the row that holds it is the same `paired_devices` row that says who this
 * device may dial. The assertions here are about that row surviving — a fresh read through a second
 * call, and a device with no pairing reporting nothing rather than inventing a token.
 */
@RunWith(AndroidJUnit4::class)
class PeerTokenTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val databasePath = File(context.filesDir, "peer-token-test.db").absolutePath

    private fun reset() {
        listOf("", "-wal", "-shm").forEach { suffix -> File(databasePath + suffix).delete() }
    }

    @Test
    fun aPeerWithNoPairingHasNoToken() = runBlocking {
        reset()
        Bridge.init(databasePath)

        assertNull(
            "nothing was paired, so nothing is stored",
            Bridge.peerToken("bb11bb22cc33dd44ee55ff6677889900aabbccddeeff00112233445566778899"),
        )
    }

    @Test
    fun aPullWithoutAPairingFailsLoudlyInsteadOfGuessing() = runBlocking {
        reset()
        Bridge.init(databasePath)

        val error =
            runCatching {
                    Bridge.syncOnce(
                        peerNodeId =
                            "cc11bb22cc33dd44ee55ff6677889900aabbccddeeff00112233445566778899",
                        deviceId = Bridge.deviceId(),
                    )
                }
                .exceptionOrNull()

        assertTrue(
            "an unpaired pull has no token to present: $error",
            error is IllegalStateException || error is net.olamaelcu.livtet.ffi.LivtetException,
        )
        assertEquals(
            "the store must not record a pairing it never made",
            emptyList<net.olamaelcu.livtet.ffi.SyncPairedDevice>(),
            Bridge.pairedDevices(),
        )
    }
}
