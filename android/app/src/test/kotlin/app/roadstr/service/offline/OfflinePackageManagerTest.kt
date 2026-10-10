package app.roadstr.service.offline

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class OfflinePackageManagerTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun `explicit wifi download verifies installs and reloads atomically`() = runBlocking {
        val bytes = packageBytes(7)
        val transport = QueueTransport(response(200, bytes, etag = "v1"))
        val manager = manager(transport)

        val result = manager.install(artifact(1, bytes), WIFI)

        assertTrue(result is OfflineInstallOutcome.Installed)
        assertEquals(1, manager.installed().size)
        assertEquals(bytes.size.toLong(), java.io.File(manager.installed().single().filePath).length())
        assertEquals(listOf(0L), transport.starts)
    }

    @Test
    fun `construction and installed inventory never contact package host`() {
        val transport = QueueTransport()
        val manager = manager(transport)

        assertTrue(manager.installed().isEmpty())
        assertTrue(transport.starts.isEmpty())
    }

    @Test
    fun `mobile data is blocked until one-time confirmation`() = runBlocking {
        val bytes = packageBytes(8)
        val transport = QueueTransport(response(200, bytes))
        val manager = manager(transport)

        val blocked = manager.install(artifact(1, bytes), MOBILE)

        assertEquals(
            OfflineInstallOutcome.Rejected(OfflineDownloadRejection.MobileDataConfirmationRequired),
            blocked,
        )
        assertTrue(transport.starts.isEmpty())
        assertTrue(manager.install(artifact(1, bytes), MOBILE, allowMobileDataOnce = true) is OfflineInstallOutcome.Installed)
    }

    @Test
    fun `range resume requires matching validator and continues received bytes`() = runBlocking {
        val bytes = packageBytes(9)
        val split = 600
        val broken = response(200, ByteArrayInputStream(bytes), bytes.size.toLong(), "v1")
            .copy(body = FailingInputStream(bytes, split))
        val resumed = response(
            status = 206,
            bytes = bytes.copyOfRange(split, bytes.size),
            etag = "v1",
            contentRange = "bytes $split-${bytes.lastIndex}/${bytes.size}",
        )
        val transport = QueueTransport(broken, resumed)
        val manager = manager(transport)

        assertEquals(
            OfflineInstallOutcome.Rejected(OfflineDownloadRejection.TransportFailure),
            manager.install(artifact(1, bytes), WIFI),
        )
        assertTrue(manager.install(artifact(1, bytes), WIFI) is OfflineInstallOutcome.Installed)
        assertEquals(listOf(0L, split.toLong()), transport.starts)
    }

    @Test
    fun `server 200 on resume restarts from zero`() = runBlocking {
        val bytes = packageBytes(10)
        val split = 512
        val transport = QueueTransport(
            response(200, ByteArrayInputStream(bytes), bytes.size.toLong(), "v1")
                .copy(body = FailingInputStream(bytes, split)),
            response(200, bytes, etag = "v2"),
        )
        val manager = manager(transport)

        manager.install(artifact(1, bytes), WIFI)
        val result = manager.install(artifact(1, bytes), WIFI)

        assertTrue(result is OfflineInstallOutcome.Installed)
        assertEquals(listOf(0L, split.toLong()), transport.starts)
    }

    @Test
    fun `cancellation retains resumable partial without activating it`() = runBlocking {
        val bytes = packageBytes(15)
        val enteredRead = CountDownLatch(1)
        val releaseRead = CountDownLatch(1)
        val input = object : InputStream() {
            private val delegate = ByteArrayInputStream(bytes)
            override fun read(): Int = delegate.read()
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                enteredRead.countDown()
                releaseRead.await()
                return delegate.read(buffer, offset, minOf(length, 512))
            }
        }
        val manager = manager(
            QueueTransport(response(200, input, bytes.size.toLong(), "v1")),
        )
        val job = launch(Dispatchers.Default) { manager.install(artifact(1, bytes), WIFI) }
        enteredRead.await()

        job.cancel()
        releaseRead.countDown()
        job.cancelAndJoin()

        assertTrue(manager.installed().isEmpty())
        assertTrue(temporary.root.walkTopDown().any { it.name.endsWith(".part") && it.length() > 0 })
        assertTrue(temporary.root.walkTopDown().any { it.name.endsWith(".journal") })
    }

    @Test
    fun `bad update checksum preserves active version`() = runBlocking {
        val first = packageBytes(11)
        val second = packageBytes(12)
        val transport = QueueTransport(response(200, first), response(200, second))
        val manager = manager(transport)
        manager.install(artifact(1, first), WIFI)

        val result = manager.install(artifact(2, second).copy(sha256 = "0".repeat(64)), WIFI)

        assertEquals(OfflineInstallOutcome.Rejected(OfflineDownloadRejection.IntegrityFailure), result)
        assertEquals(1, manager.installed().single().artifact.version)
    }

    @Test
    fun `space boundary is enforced before transfer`() = runBlocking {
        val bytes = packageBytes(13)
        val transport = QueueTransport(response(200, bytes))
        val root = temporary.newFolder("small")
        var closed = false
        val manager = OfflinePackageManager(
            root,
            transport,
            freeBytes = { 1L },
            closeEngine = { closed = true },
        )

        assertEquals(
            OfflineInstallOutcome.Rejected(OfflineDownloadRejection.InsufficientSpace),
            manager.install(artifact(1, bytes), WIFI),
        )
        assertFalse(closed)
    }

    @Test
    fun `explicit deletion closes the engine and removes the active package`() = runBlocking {
        val bytes = packageBytes(14)
        var closedId: String? = null
        val manager = OfflinePackageManager(
            temporary.newFolder("delete"),
            QueueTransport(response(200, bytes)),
            freeBytes = { Long.MAX_VALUE },
            closeEngine = { closedId = it },
        )
        assertTrue(manager.install(artifact(1, bytes), WIFI) is OfflineInstallOutcome.Installed)

        assertTrue(manager.delete("routing-example"))
        assertEquals("routing-example", closedId)
        assertTrue(manager.installed().isEmpty())
    }

    private fun manager(transport: QueueTransport): OfflinePackageManager = OfflinePackageManager(
        temporary.newFolder(),
        transport,
        freeBytes = { Long.MAX_VALUE },
    )

    private fun artifact(version: Int, bytes: ByteArray): OfflinePackageArtifact = OfflinePackageArtifact(
        id = "routing-example",
        version = version,
        datasetType = OfflineDatasetType.ValhallaRouting,
        area = OfflineCoverageArea.Polygon(
            listOf(
                listOf(
                    OfflineCoveragePoint(0.0, 0.0),
                    OfflineCoveragePoint(0.0, 1.0),
                    OfflineCoveragePoint(1.0, 1.0),
                    OfflineCoveragePoint(1.0, 0.0),
                    OfflineCoveragePoint(0.0, 0.0),
                ),
            ),
        ),
        levels = setOf(0, 1, 2),
        sizeBytes = bytes.size.toLong(),
        installedSizeBytes = bytes.size.toLong(),
        sha256 = sha256(bytes),
        url = "https://packages.example/routing.tar",
        license = "ODbL-1.0",
        attribution = "OpenStreetMap contributors",
        build = OfflinePackageBuild("valhalla", "3.9.1", "test", "config-1"),
    )

    private fun response(
        status: Int,
        bytes: ByteArray,
        etag: String? = null,
        contentRange: String? = null,
    ) = response(status, ByteArrayInputStream(bytes), bytes.size.toLong(), etag, contentRange)

    private fun response(
        status: Int,
        stream: InputStream,
        length: Long,
        etag: String? = null,
        contentRange: String? = null,
    ) = OfflineDownloadResponse(status, length, contentRange, etag, null, null, stream)

    private fun packageBytes(seed: Int): ByteArray = ByteArray(1_024) { index ->
        if (index == 0) seed.toByte() else (index % 251).toByte()
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }

    private class QueueTransport(vararg responses: OfflineDownloadResponse) : OfflinePackageDownloadTransport {
        private val responses = ArrayDeque(responses.toList())
        val starts = mutableListOf<Long>()
        override fun open(url: String, rangeStart: Long, ifRange: String?): OfflineDownloadResponse {
            starts += rangeStart
            return responses.removeFirst()
        }
    }

    private class FailingInputStream(bytes: ByteArray, private val failAt: Int) : InputStream() {
        private val input = ByteArrayInputStream(bytes)
        private var read = 0
        override fun read(): Int {
            if (read >= failAt) throw IOException("interrupted")
            val value = input.read()
            if (value >= 0) read++
            return value
        }
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (read >= failAt) throw IOException("interrupted")
            val count = input.read(buffer, offset, minOf(length, failAt - read))
            if (count > 0) read += count
            return count
        }
    }

    private companion object {
        val WIFI = OfflineNetworkState(connected = true, wifi = true, metered = false)
        val MOBILE = OfflineNetworkState(connected = true, wifi = false, metered = true)
    }
}
