package app.roadstr.feature.voice

import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeVoiceCatalogTest {
    @Test
    fun `catalogue preserves all Flutter assets and reusable paths`() {
        val assets = NativeVoiceCatalog.downloadAssets

        assertEquals(17, assets.size)
        assertEquals(18, NativeVoiceCatalog.reusableRelativePaths.size)
        assertEquals(13, assets.count { it.relativePath.endsWith(".bin") })
        assertTrue(NativeVoiceCatalog.ESPEAK_SENTINEL_PATH in NativeVoiceCatalog.reusableRelativePaths)
        assertEquals(86_033_585L, assets.first().expectedBytes)
        assertEquals(63_201_294L, assets.first { it.relativePath.startsWith("piper/") }.expectedBytes)
        assertEquals(17, assets.map { it.relativePath }.toSet().size)
    }

    @Test
    fun `language and voice selection match Kokoro and Piper policy`() {
        val italian = NativeVoiceCatalog.selection("IT", NativeVoiceGender.Male)!!
        val french = NativeVoiceCatalog.selection("fr", NativeVoiceGender.Male)!!
        val german = NativeVoiceCatalog.selection("de", NativeVoiceGender.Female)!!

        assertEquals(NativeVoiceEngine.Kokoro, italian.engine)
        assertEquals("im_nicola", italian.voiceName)
        assertTrue(italian.genderChoiceAvailable)
        assertEquals("ff_siwis", french.voiceName)
        assertEquals(NativeVoiceGender.Female, french.gender)
        assertFalse(french.genderChoiceAvailable)
        assertEquals(NativeVoiceEngine.Piper, german.engine)
        assertEquals(NativeVoiceGender.Male, german.gender)
        assertEquals(22_050, german.sampleRateHz)
        assertNull(NativeVoiceCatalog.selection("nl", NativeVoiceGender.Male))
    }

    @Test
    fun `settings speeds and default preserve Flutter values`() {
        assertEquals(listOf(0.7, 0.85, 1.0, 1.15, 1.3, 1.5), NativeVoiceCatalog.speedStages)
        assertEquals(4, NativeVoiceCatalog.DEFAULT_SPEED_STAGE)
        assertEquals(1.3, NativeVoiceCatalog.speedForStage(4), 0.0)
        assertThrows(IllegalArgumentException::class.java) { NativeVoiceCatalog.speedForStage(6) }
    }

    @Test
    fun `eSpeak voices and fixed phrase assets keep engine fallbacks`() {
        assertEquals("en-us", NativeVoiceCatalog.eSpeakVoice("en"))
        assertEquals("cmn", NativeVoiceCatalog.eSpeakVoice("zh"))
        assertEquals("pt-BR", NativeVoiceCatalog.eSpeakVoice("pt"))
        assertEquals("en", NativeVoiceCatalog.eSpeakVoice("xx"))
        assertEquals(
            "assets/kokoro_phrases/fr_f_arrived.wav",
            NativeVoiceCatalog.bundledPhrasePath(
                "fr",
                NativeVoiceGender.Male,
                NativeVoiceFixedPhrase.Arrived,
            ),
        )
        assertNull(
            NativeVoiceCatalog.bundledPhrasePath(
                "de",
                NativeVoiceGender.Male,
                NativeVoiceFixedPhrase.LetsGo,
            ),
        )
    }

    @Test
    fun `combined progress uses the shipped approximate weighting`() {
        val kokoroOnly = NativeVoiceCatalog.combinedDownloadProgress(1.0, 0.0)
        val piperOnly = NativeVoiceCatalog.combinedDownloadProgress(0.0, 1.0)

        assertEquals(1.0, kokoroOnly + piperOnly, 1e-12)
        assertTrue(kokoroOnly > piperOnly)
        assertEquals(1.0, NativeVoiceCatalog.combinedDownloadProgress(1.0, 1.0), 1e-12)
        assertThrows(IllegalArgumentException::class.java) {
            NativeVoiceCatalog.combinedDownloadProgress(Double.NaN, 0.0)
        }
    }

    @Test
    fun `asset descriptors reject traversal cleartext and malformed hashes`() {
        assertThrows(IllegalArgumentException::class.java) {
            testAsset("../voice.bin", byteArrayOf(1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            NativeVoiceAsset("voice.bin", "http://example.com/voice", 1, "00".repeat(32))
        }
        assertThrows(IllegalArgumentException::class.java) {
            NativeVoiceAsset("voice.bin", "https://example.com/voice", 1, "AA".repeat(32))
        }
    }

    private fun testAsset(path: String, bytes: ByteArray): NativeVoiceAsset = NativeVoiceAsset(
        relativePath = path,
        remoteUrl = "https://example.com/$path",
        expectedBytes = bytes.size.toLong(),
        sha256 = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) },
    )
}
