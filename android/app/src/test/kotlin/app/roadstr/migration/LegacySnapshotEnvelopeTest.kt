package app.roadstr.migration

import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.Base64
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class LegacySnapshotEnvelopeTest {
    private val fixturePrivateKey = "11".repeat(32)
    private val fixturePublicKey = "22".repeat(32)

    @Test
    fun `Dart fixture decodes and covers the complete Kotlin contract`() {
        val encoded = fixtureBytes()
        val snapshot = LegacySnapshotEnvelope.decode(encoded)
        val dynamicKeys = setOf(
            "activity_inbox_$fixturePublicKey",
            "activity_zap_cursor_$fixturePublicKey",
            "activity_confirmation_cursor_$fixturePublicKey",
        )

        assertEquals(1, snapshot.schemaVersion)
        assertEquals(LegacyStorageContract.hiveKeys + dynamicKeys, snapshot.ordinaryValues.keys)
        assertEquals(LegacyStorageContract.secureKeys, snapshot.secureValues.keys)
        assertEquals(fixturePublicKey, snapshot.identity.publicKeyHex)
        assertEquals(fixturePrivateKey, snapshot.identity.privateKeyHex)
        assertEquals("nsec", snapshot.identity.flavor)
        assertEquals(
            listOf(
                "espeak-ng-data/.roadstr_extracted",
                "kokoro/if_sara.bin",
                "kokoro/model_q8f16.onnx",
                "kokoro/tokenizer.json",
                "piper/de_DE-thorsten-medium.onnx",
                "piper/de_DE-thorsten-medium.onnx.json",
            ),
            snapshot.assets.map(LegacyAsset::relativePath),
        )
        assertTrue(
            LegacyStorageValidator.validate(
                snapshot,
                IdentityVerifier { privateKey ->
                    if (privateKey == fixturePrivateKey) fixturePublicKey else error("fixture key")
                },
            ).valid,
        )
        assertArrayEquals(encoded, LegacySnapshotEnvelope.encode(snapshot))
    }

    @Test
    fun `encoding is canonical across map and asset order`() {
        val original = snapshot()
        val reordered = original.copy(
            ordinaryValues = original.ordinaryValues.entries.reversed().associate { it.toPair() },
            secureValues = original.secureValues.entries.reversed().associate { it.toPair() },
            assets = original.assets.reversed(),
        )

        assertArrayEquals(
            LegacySnapshotEnvelope.encode(original),
            LegacySnapshotEnvelope.encode(reordered),
        )
    }

    @Test
    fun `reader invokes its source once and never mutates source bytes`() {
        val encoded = LegacySnapshotEnvelope.encode(snapshot())
        val original = encoded.copyOf()
        val expected = LegacySnapshotEnvelope.decode(encoded)
        var reads = 0
        val reader = LegacyEnvelopeSnapshotReader {
            reads++
            encoded
        }

        assertEquals(expected, reader.read())
        assertEquals(1, reads)
        assertArrayEquals(original, encoded)
        assertNull(LegacyEnvelopeSnapshotReader { null }.read())
    }

    @Test
    fun `transport corruption and truncation fail closed`() {
        val encoded = LegacySnapshotEnvelope.encode(snapshot())
        val corrupted = encoded.copyOf().also { it[24] = (it[24].toInt() xor 0xff).toByte() }
        val truncated = encoded.copyOf(encoded.size - 1)

        expectEnvelopeFailure(corrupted, "integrity")
        expectEnvelopeFailure(truncated, "integrity")
    }

    @Test
    fun `wrong magic version and trailing body data are rejected`() {
        val encoded = LegacySnapshotEnvelope.encode(snapshot())
        val wrongMagic = encoded.copyOf().also { it[0] = 'X'.code.toByte() }.resigned()
        val wrongVersion = encoded.copyOf().also {
            ByteBuffer.wrap(it).putInt(8, 99)
        }.resigned()
        val body = encoded.copyOf(encoded.size - 32) + byteArrayOf(0)
        val trailing = body + MessageDigest.getInstance("SHA-256").digest(body)

        expectEnvelopeFailure(wrongMagic, "magic")
        expectEnvelopeFailure(wrongVersion, "format")
        expectEnvelopeFailure(trailing, "trailing")
    }

    @Test
    fun `invalid counts lengths duplicate keys and malformed UTF-8 are rejected`() {
        val encoded = LegacySnapshotEnvelope.encode(snapshot())
        val invalidCount = encoded.copyOf().also {
            ByteBuffer.wrap(it).putInt(16, LegacySnapshotLimits.MAX_ENTRIES_PER_STORE + 1)
        }.resigned()
        val invalidLength = encoded.copyOf().also {
            ByteBuffer.wrap(it).putInt(20, LegacySnapshotLimits.MAX_KEY_BYTES + 1)
        }.resigned()
        val invalidUtf8 = encoded.copyOf().also { it[24] = 0xff.toByte() }.resigned()

        val duplicateSource = LegacySnapshotEnvelope.encode(
            snapshot().copy(ordinaryValues = mapOf("autoDark" to "true", "language" to "it")),
        )
        val duplicateKey = duplicateSource.copyOf()
        val languageOffset = duplicateKey.indexOf("language".toByteArray())
        assertTrue(languageOffset >= 0)
        "autoDark".toByteArray().copyInto(duplicateKey, languageOffset)

        expectEnvelopeFailure(invalidCount, "count")
        expectEnvelopeFailure(invalidLength, "length")
        expectEnvelopeFailure(invalidUtf8, "UTF-8")
        expectEnvelopeFailure(duplicateKey.resigned(), "Duplicate map key")
    }

    @Test
    fun `encoder rejects oversized fields and duplicate assets before allocation`() {
        expectEncodeFailure(
            snapshot().copy(ordinaryValues = mapOf("k".repeat(257) to "value")),
            "field",
        )
        expectEncodeFailure(
            snapshot().copy(assets = listOf(snapshot().assets.first(), snapshot().assets.first())),
            "Duplicate asset path",
        )
    }

    private fun snapshot() = LegacyStorageSnapshot(
        schemaVersion = 1,
        ordinaryValues = linkedMapOf("language" to "it", "autoDark" to "true"),
        secureValues = linkedMapOf("nostr_flavor" to "amber", "nostr_pub_hex" to fixturePublicKey),
        identity = LegacyIdentity(fixturePublicKey, "amber", null),
        assets = listOf(
            LegacyAsset("piper/model.onnx", 200, "bb".repeat(32)),
            LegacyAsset("kokoro/model.onnx", 100, "aa".repeat(32)),
        ),
    )

    private fun fixtureBytes(): ByteArray {
        val resource = requireNotNull(javaClass.getResourceAsStream("/parity/legacy_snapshot_v1.b64"))
        return Base64.getDecoder().decode(resource.bufferedReader().use { it.readText().trim() })
    }

    private fun ByteArray.resigned(): ByteArray {
        val body = copyOf(size - 32)
        return body + MessageDigest.getInstance("SHA-256").digest(body)
    }

    private fun ByteArray.indexOf(needle: ByteArray): Int {
        for (start in 0..size - needle.size) {
            if (needle.indices.all { this[start + it] == needle[it] }) return start
        }
        return -1
    }

    private fun expectEnvelopeFailure(bytes: ByteArray, messagePart: String) {
        try {
            LegacySnapshotEnvelope.decode(bytes)
            fail("Expected LegacyEnvelopeException")
        } catch (error: LegacyEnvelopeException) {
            assertTrue(error.message.orEmpty().contains(messagePart, ignoreCase = true))
            assertFalse(error.message.orEmpty().contains("fixture-secret"))
        }
    }

    private fun expectEncodeFailure(snapshot: LegacyStorageSnapshot, messagePart: String) {
        try {
            LegacySnapshotEnvelope.encode(snapshot)
            fail("Expected LegacyEnvelopeException")
        } catch (error: LegacyEnvelopeException) {
            assertTrue(error.message.orEmpty().contains(messagePart, ignoreCase = true))
        }
    }
}
