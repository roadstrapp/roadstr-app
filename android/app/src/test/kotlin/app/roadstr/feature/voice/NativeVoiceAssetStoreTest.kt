package app.roadstr.feature.voice

import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeVoiceAssetStoreTest {
    @Test
    fun `store distinguishes missing size hash and valid files`() {
        withTempDirectory { root ->
            val expected = "roadstr-voice".encodeToByteArray()
            val asset = asset("kokoro/test.bin", expected)
            val target = File(root, asset.relativePath)
            val store = NativeVoiceAssetStore(root)

            assertEquals(NativeVoiceAssetState.Missing, store.inspect(asset).state)
            target.parentFile!!.mkdirs()
            target.writeBytes(byteArrayOf(1))
            assertEquals(NativeVoiceAssetState.InvalidSize, store.inspect(asset).state)
            target.writeBytes(ByteArray(expected.size))
            assertEquals(NativeVoiceAssetState.InvalidHash, store.inspect(asset).state)
            target.writeBytes(expected)
            assertEquals(NativeVoiceAssetState.Valid, store.inspect(asset).state)
            assertTrue(store.pendingAssets(listOf(asset)).isEmpty())
        }
    }

    @Test
    fun `store rejects symbolic links instead of hashing outside content`() {
        withTempDirectory { root ->
            val outside = Files.createTempFile("roadstr-voice-outside", ".bin").toFile()
            val outsideDirectory = createTempDirectory("roadstr-voice-outside-dir-").toFile()
            try {
                outside.writeBytes("outside".encodeToByteArray())
                val asset = asset("kokoro/link.bin", outside.readBytes())
                val link = File(root, asset.relativePath)
                link.parentFile!!.mkdirs()
                Files.createSymbolicLink(link.toPath(), outside.toPath())

                assertEquals(NativeVoiceAssetState.UnsafeFile, NativeVoiceAssetStore(root).inspect(asset).state)

                val nestedAsset = asset("piper/model.bin", "nested".encodeToByteArray())
                Files.createSymbolicLink(File(root, "piper").toPath(), outsideDirectory.toPath())
                assertEquals(
                    NativeVoiceAssetState.UnsafeFile,
                    NativeVoiceAssetStore(root).inspect(nestedAsset).state,
                )
            } finally {
                outside.delete()
                outsideDirectory.deleteRecursively()
            }
        }
    }

    @Test
    fun `downloader skips valid files and atomically installs pending ones`() {
        withTempDirectory { root ->
            val firstBytes = "already-valid".encodeToByteArray()
            val secondBytes = "new-download".encodeToByteArray()
            val first = asset("kokoro/first.bin", firstBytes)
            val second = asset("piper/second.bin", secondBytes)
            File(root, first.relativePath).apply { parentFile!!.mkdirs(); writeBytes(firstBytes) }
            val opened = mutableListOf<String>()
            val progress = mutableListOf<NativeVoiceDownloadProgress>()
            val downloader = NativeVoiceAssetDownloader { requested ->
                opened += requested.relativePath
                body(secondBytes)
            }

            val result = downloader.ensure(root, listOf(first, second), progress::add)

            assertEquals(listOf(second.relativePath), opened)
            assertTrue(result.all { it.state == NativeVoiceAssetState.Valid })
            assertEquals(secondBytes.toList(), File(root, second.relativePath).readBytes().toList())
            assertFalse(File(root, "${second.relativePath}.part").exists())
            assertEquals(1.0, progress.last().fraction, 0.0)
        }
    }

    @Test
    fun `failed integrity removes partial and does not replace existing file`() {
        withTempDirectory { root ->
            val expected = "correct-data".encodeToByteArray()
            val asset = asset("kokoro/model.bin", expected)
            val target = File(root, asset.relativePath).apply {
                parentFile!!.mkdirs()
                writeBytes("old-invalid!".encodeToByteArray())
            }
            val before = target.readBytes()
            val downloader = NativeVoiceAssetDownloader { body(ByteArray(expected.size) { 7 }) }

            assertThrows(NativeVoiceDownloadException::class.java) {
                downloader.ensure(root, listOf(asset))
            }
            assertEquals(before.toList(), target.readBytes().toList())
            assertFalse(File(target.parentFile, "${target.name}.part").exists())
        }
    }

    @Test
    fun `declared and streamed size overruns fail closed`() {
        withTempDirectory { root ->
            val expected = byteArrayOf(1, 2, 3)
            val asset = asset("kokoro/model.bin", expected)
            val declaredWrong = NativeVoiceAssetDownloader {
                body(expected, declaredLength = 99)
            }
            assertThrows(NativeVoiceDownloadException::class.java) {
                declaredWrong.ensure(root, listOf(asset))
            }

            val oversized = NativeVoiceAssetDownloader { body(byteArrayOf(1, 2, 3, 4), declaredLength = null) }
            assertThrows(NativeVoiceDownloadException::class.java) {
                oversized.ensure(root, listOf(asset))
            }
        }
    }

    @Test
    fun `duplicate paths are rejected before transport access`() {
        withTempDirectory { root ->
            val asset = asset("kokoro/model.bin", byteArrayOf(1))
            var opened = false
            val downloader = NativeVoiceAssetDownloader {
                opened = true
                body(byteArrayOf(1))
            }

            assertThrows(IllegalArgumentException::class.java) {
                downloader.ensure(root, listOf(asset, asset))
            }
            assertFalse(opened)
        }
    }

    private fun asset(path: String, bytes: ByteArray) = NativeVoiceAsset(
        relativePath = path,
        remoteUrl = "https://example.com/$path",
        expectedBytes = bytes.size.toLong(),
        sha256 = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) },
    )

    private fun body(bytes: ByteArray, declaredLength: Long? = bytes.size.toLong()) =
        object : NativeVoiceDownloadBody {
            override val contentLength = declaredLength
            override val stream: InputStream = ByteArrayInputStream(bytes)
            override fun close() = stream.close()
        }

    private fun withTempDirectory(block: (File) -> Unit) {
        val root = createTempDirectory("roadstr-native-voice-").toFile()
        try {
            block(root)
        } finally {
            root.deleteRecursively()
        }
    }
}
