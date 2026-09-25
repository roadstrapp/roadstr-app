package app.roadstr.migration

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyHeadlessSnapshotReaderTest {
    @Test
    fun `channel contract exactly matches the shared Dart fixture`() {
        val fixture = resourceText("parity/legacy_bridge_protocol.tsv")
            .lineSequence()
            .filter(String::isNotBlank)
            .associate { line ->
                val fields = line.split('\t')
                require(fields.size == 2)
                fields[0] to fields[1]
            }

        assertEquals(
            mapOf(
                "protocol_version" to LegacyMigrationChannelContract.protocolVersion.toString(),
                "channel" to LegacyMigrationChannelContract.channelName,
                "ready_method" to LegacyMigrationChannelContract.readyMethod,
                "read_method" to LegacyMigrationChannelContract.readMethod,
                "entrypoint_library" to LegacyMigrationChannelContract.entrypointLibrary,
                "entrypoint_function" to LegacyMigrationChannelContract.entrypointFunction,
                "failure_code" to LegacyMigrationChannelContract.failureCode,
            ),
            fixture,
        )
    }

    @Test
    fun `available envelope is decoded only after transport closes`() {
        val envelope = Base64.getDecoder().decode(
            resourceText("parity/legacy_snapshot_v1.b64").trim(),
        )
        lateinit var transport: FakeTransport
        transport = FakeTransport { callback ->
            val reply = LegacyEnvelopeTransportResult.Available(envelope)
            envelope.fill(0)
            callback(reply)
        }
        val reader = readerFor(transport)

        val snapshot = reader.read()

        assertTrue(transport.closed)
        assertEquals(42 + 3, snapshot?.ordinaryValues?.size)
        assertEquals(9, snapshot?.secureValues?.size)
        assertEquals(6, snapshot?.assets?.size)
        assertEquals("nsec", snapshot?.identity?.flavor)
    }

    @Test
    fun `not-needed reply returns null and closes transport`() {
        val transport = FakeTransport { callback ->
            callback(LegacyEnvelopeTransportResult.NotNeeded)
        }

        assertNull(readerFor(transport).read())
        assertTrue(transport.closed)
    }

    @Test
    fun `transport failure is value-free and closes transport`() {
        val transport = FakeTransport { callback ->
            callback(LegacyEnvelopeTransportResult.Failed)
        }

        val failure = assertThrows(LegacyHeadlessBridgeException::class.java) {
            readerFor(transport).read()
        }
        assertFalse(failure.toString().contains(SECRET))
        assertTrue(transport.closed)
    }

    @Test
    fun `request exception and malformed envelope are reduced to neutral errors`() {
        val throwing = FakeTransport { throw IllegalStateException(SECRET) }
        val malformed = FakeTransport { callback ->
            callback(LegacyEnvelopeTransportResult.Available(SECRET.toByteArray()))
        }
        val factoryFailure = LegacyHeadlessSnapshotReader(
            transportFactory = LegacyEnvelopeTransportFactory {
                throw IllegalStateException(SECRET)
            },
            timeoutMillis = 1_000,
            isMainThread = { false },
        )

        for (reader in listOf(
            readerFor(throwing),
            readerFor(malformed),
            factoryFailure,
        )) {
            val failure = assertThrows(LegacyHeadlessBridgeException::class.java) {
                reader.read()
            }
            assertFalse(failure.toString().contains(SECRET))
        }
        assertTrue(throwing.closed)
        assertTrue(malformed.closed)
    }

    @Test
    fun `timeout closes a silent transport`() {
        val transport = FakeTransport { }
        val reader = LegacyHeadlessSnapshotReader(
            transportFactory = LegacyEnvelopeTransportFactory { transport },
            timeoutMillis = 10,
            isMainThread = { false },
        )

        val failure = assertThrows(LegacyHeadlessBridgeException::class.java) {
            reader.read()
        }
        assertEquals("Legacy bridge timed out", failure.message)
        assertTrue(transport.closed)
    }

    @Test
    fun `first callback wins and later replies are ignored`() {
        val transport = FakeTransport { callback ->
            callback(LegacyEnvelopeTransportResult.NotNeeded)
            callback(LegacyEnvelopeTransportResult.Failed)
        }

        assertNull(readerFor(transport).read())
        assertTrue(transport.closed)
    }

    @Test
    fun `main-thread reads fail before an engine can be created`() {
        var factoryCalled = false
        val reader = LegacyHeadlessSnapshotReader(
            transportFactory = LegacyEnvelopeTransportFactory {
                factoryCalled = true
                FakeTransport { }
            },
            timeoutMillis = 1_000,
            isMainThread = { true },
        )

        assertThrows(LegacyHeadlessBridgeException::class.java) {
            reader.read()
        }
        assertFalse(factoryCalled)
    }

    private fun readerFor(transport: FakeTransport) = LegacyHeadlessSnapshotReader(
        transportFactory = LegacyEnvelopeTransportFactory { transport },
        timeoutMillis = 1_000,
        isMainThread = { false },
    )

    private fun resourceText(path: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream(path)) {
            "Missing test resource: $path"
        }.bufferedReader().use { it.readText() }

    private class FakeTransport(
        private val action: ((LegacyEnvelopeTransportResult) -> Unit) -> Unit,
    ) : LegacyEnvelopeTransport {
        var closed = false
            private set

        override fun request(callback: (LegacyEnvelopeTransportResult) -> Unit) {
            action(callback)
        }

        override fun close() {
            closed = true
        }
    }

    private companion object {
        const val SECRET = "must-not-appear-in-native-bridge-errors"
    }
}
