package app.roadstr.service.speech

import app.roadstr.core.speech.SpeechModelCatalog
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SpeechModelStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val payload = ByteArray(300_000) { (it * 31 + it / 7).toByte() }
    private val spec = SpeechModelCatalog.default.copy(
        fileName = "test-model.gguf",
        byteSize = payload.size.toLong(),
        sha256 = MessageDigest.getInstance("SHA-256").digest(payload).joinToString("") { "%02x".format(it) },
    )

    private fun store(space: Long = Long.MAX_VALUE) =
        SpeechModelStore(folder.newFolder(), spec, freeSpace = { space })

    /** Serves [bytes] and records the offsets it was asked to resume from. */
    private class FakeTransport(
        private val bytes: ByteArray,
        private val honourRange: Boolean = true,
        private val failAfter: Int = Int.MAX_VALUE,
    ) : SpeechModelTransport {
        val requestedOffsets = mutableListOf<Long>()

        override fun open(url: String, resumeFrom: Long): SpeechModelBody {
            requestedOffsets += resumeFrom
            val offset = if (honourRange) resumeFrom.toInt() else 0
            val body = bytes.copyOfRange(offset, bytes.size)
            return object : SpeechModelBody {
                override val startOffset = offset.toLong()
                override val stream: InputStream = object : InputStream() {
                    private val inner = ByteArrayInputStream(body)
                    private var served = 0
                    override fun read(): Int = inner.read()
                    override fun read(buffer: ByteArray, off: Int, len: Int): Int {
                        if (served >= failAfter) throw java.io.IOException("connection reset")
                        val count = inner.read(buffer, off, minOf(len, 4096))
                        if (count > 0) served += count
                        return count
                    }
                }
                override fun close() = Unit
            }
        }
    }

    @Test
    fun aDownloadIsVerifiedInstalledAndReportedToTheEnd() {
        val store = store()
        val progress = mutableListOf<Long>()
        store.download(FakeTransport(payload), { done, total ->
            assertEquals(payload.size.toLong(), total)
            progress += done
        })
        assertEquals(SpeechModelFileState.Ready, store.state())
        assertTrue(payload.contentEquals(store.modelFile.readBytes()))
        assertEquals(payload.size.toLong(), progress.last())
        assertEquals(progress.sorted(), progress)
        assertFalse(File(store.modelFile.path + ".part").exists())
    }

    @Test
    fun wrongBytesOfTheRightSizeAreRefusedAndNothingIsKept() {
        val store = store()
        val tampered = payload.copyOf().also { it[1234] = (it[1234] + 1).toByte() }
        assertThrows(SpeechModelException.Integrity::class.java) {
            store.download(FakeTransport(tampered), { _, _ -> })
        }
        assertFalse(store.modelFile.exists())
        assertEquals(0L, store.partialBytes())
        assertEquals(SpeechModelFileState.Missing, store.state())
    }

    @Test
    fun aServerThatSendsTooMuchIsStopped() {
        val store = store()
        assertThrows(SpeechModelException.Integrity::class.java) {
            store.download(FakeTransport(payload + ByteArray(5_000)), { _, _ -> })
        }
        assertFalse(store.modelFile.exists())
        assertEquals(0L, store.partialBytes())
    }

    @Test
    fun anInterruptedDownloadResumesFromWhereItStopped() {
        val store = store()
        val first = FakeTransport(payload, failAfter = 100_000)
        assertThrows(SpeechModelException.Network::class.java) { store.download(first, { _, _ -> }) }
        val kept = store.partialBytes()
        assertTrue("kept $kept", kept in 90_000..110_000)

        val second = FakeTransport(payload)
        store.download(second, { _, _ -> })
        assertEquals(listOf(kept), second.requestedOffsets)
        assertEquals(SpeechModelFileState.Ready, store.state())
    }

    @Test
    fun aServerThatIgnoresRangeRestartsFromZeroWithoutCorruptingTheFile() {
        val store = store()
        assertThrows(SpeechModelException.Network::class.java) {
            store.download(FakeTransport(payload, failAfter = 50_000), { _, _ -> })
        }
        store.download(FakeTransport(payload, honourRange = false), { _, _ -> })
        assertEquals(SpeechModelFileState.Ready, store.state())
        assertTrue(payload.contentEquals(store.modelFile.readBytes()))
    }

    @Test
    fun cancellingKeepsThePartialFileForLater() {
        val store = store()
        var reads = 0
        assertThrows(SpeechModelException.Cancelled::class.java) {
            store.download(FakeTransport(payload), { _, _ -> }, isCancelled = { ++reads > 20 })
        }
        assertTrue(store.partialBytes() > 0)
        assertFalse(store.modelFile.exists())
        store.download(FakeTransport(payload), { _, _ -> })
        assertEquals(SpeechModelFileState.Ready, store.state())
    }

    @Test
    fun withoutEnoughFreeStorageNothingIsDownloaded() {
        val store = store(space = 10_000)
        val transport = FakeTransport(payload)
        val error = assertThrows(SpeechModelException.InsufficientStorage::class.java) {
            store.download(transport, { _, _ -> })
        }
        assertTrue(error.neededBytes > payload.size)
        assertTrue(transport.requestedOffsets.isEmpty())
    }

    @Test
    fun anExistingFileIsOnlyReadyWhenSizeAndDigestMatch() {
        val store = store()
        assertEquals(SpeechModelFileState.Missing, store.state())

        store.modelFile.writeBytes(ByteArray(10))
        assertEquals(SpeechModelFileState.Corrupt, store.state())
        assertFalse("a corrupt file is removed", store.modelFile.exists())

        store.modelFile.writeBytes(payload.copyOf().also { it[0] = (it[0] + 1).toByte() })
        assertEquals(SpeechModelFileState.Corrupt, store.state())

        store.modelFile.writeBytes(payload)
        assertEquals(SpeechModelFileState.Ready, store.state())
        assertEquals("the second check uses the marker", SpeechModelFileState.Ready, store.state())
    }

    @Test
    fun aChangedFileInvalidatesTheVerificationMarker() {
        val store = store()
        store.modelFile.writeBytes(payload)
        assertEquals(SpeechModelFileState.Ready, store.state())
        val tampered = payload.copyOf().also { it[99] = (it[99] + 1).toByte() }
        store.modelFile.writeBytes(tampered)
        store.modelFile.setLastModified(System.currentTimeMillis() + 5_000)
        assertEquals(SpeechModelFileState.Corrupt, store.state())
    }

    @Test
    fun deleteRemovesTheModelThePartialAndTheMarker() {
        val store = store()
        store.download(FakeTransport(payload), { _, _ -> })
        store.delete()
        assertEquals(SpeechModelFileState.Missing, store.state())
        assertEquals(0L, store.partialBytes())
        assertEquals(emptyList<String>(), store.modelFile.parentFile!!.list()!!.toList())
    }

    @Test
    fun theHttpTransportResumesWithARangeRequest() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(206)
                .setHeader("Content-Range", "bytes 1000-${payload.size - 1}/${payload.size}")
                .setBody(Buffer().write(payload, 1000, payload.size - 1000)))
            server.start()
            OkHttpSpeechModelTransport().open(server.url("/m.gguf").toString(), 1000).use { body ->
                assertEquals(1000L, body.startOffset)
                assertEquals(payload.size - 1000, body.stream.readBytes().size)
            }
            assertEquals("bytes=1000-", server.takeRequest().getHeader("Range"))
        }
    }

    @Test
    fun theHttpTransportStartsOverWhenTheServerAnswersAFullBody() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(Buffer().write(payload)))
            server.start()
            OkHttpSpeechModelTransport().open(server.url("/m.gguf").toString(), 1000).use { body ->
                assertEquals(0L, body.startOffset)
            }
        }
    }

    @Test
    fun theStoreRecoversFromARejectedRangeAndFromAnErrorPage() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(416))
            server.enqueue(MockResponse().setBody(Buffer().write(payload)))
            server.start()
            val store = store()
            File(store.modelFile.path + ".part").writeBytes(payload.copyOf(5_000))
            // The store always asks for the pinned HTTPS URL; the test points it at the local server.
            val local = SpeechModelTransport { _, from ->
                OkHttpSpeechModelTransport().open(server.url("/m.gguf").toString(), from)
            }
            store.download(local, { _, _ -> })
            assertEquals(SpeechModelFileState.Ready, store.state())
            assertEquals("bytes=5000-", server.takeRequest().getHeader("Range"))
            assertEquals(null, server.takeRequest().getHeader("Range"))
        }
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(404))
            server.start()
            assertThrows(SpeechModelException.Network::class.java) {
                OkHttpSpeechModelTransport().open(server.url("/m.gguf").toString(), 0)
            }
        }
    }
}
