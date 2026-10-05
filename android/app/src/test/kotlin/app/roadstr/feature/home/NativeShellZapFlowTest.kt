package app.roadstr.feature.home

import app.roadstr.core.protocol.lightning.LnurlPayInfo
import app.roadstr.core.protocol.nostr.RoadCategoryWire
import app.roadstr.service.nostr.NativeNostrSigner
import app.roadstr.service.nostr.NativeRoadEvent
import app.roadstr.service.nostr.NativeZapPayments
import app.roadstr.service.nostr.FakeConnector
import app.roadstr.service.nostr.FakeRelayNetwork
import app.roadstr.service.nostr.ManualScheduler
import app.roadstr.service.nostr.NativeFavoritesSyncService
import app.roadstr.service.nostr.NativeFavoritesSyncStore
import app.roadstr.service.nostr.NativeProfileVisibilityService
import app.roadstr.service.nostr.NativeRelayFetcher
import app.roadstr.service.nostr.NativeRelayPublisher
import app.roadstr.service.nostr.NativeRoadEventService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeShellZapFlowTest {
    private val event = NativeRoadEvent(
        id = "a".repeat(64), pubkey = "b".repeat(64), category = RoadCategoryWire.POLICE,
        latitude = 1.0, longitude = 2.0, comment = "", createdAt = 1L, expiresAt = null,
    )
    private val info = LnurlPayInfo("https://pay.example/cb", 1_000, 1_000_000_000, "[]", "c".repeat(64), true)

    private class FakePayments(
        var address: String? = "reporter@pay.example",
        var info: LnurlPayInfo? = null,
        var invoice: String? = "lnbc1invoice",
        var preimage: String? = "ab".repeat(32),
    ) : NativeZapPayments {
        var lastAmountMsat = 0L
        var requestSigned = false
        var paidWith: String? = null

        override suspend fun lightningAddress(pubkey: String) = address
        override suspend fun payInfo(address: String) = info
        override suspend fun zapRequest(signer: NativeNostrSigner, recipient: String, eventId: String, amountMsat: Long): Map<String, Any?>? {
            requestSigned = true
            return mapOf("kind" to 9734)
        }
        override suspend fun invoice(info: LnurlPayInfo, amountMsat: Long, zapRequest: Map<String, Any?>?): String? {
            lastAmountMsat = amountMsat
            return invoice
        }
        override suspend fun payViaNwc(invoice: String, nwcUri: String): String? {
            paidWith = nwcUri
            return preimage
        }
        override suspend fun zapTotalMsat(eventId: String, recipient: String) = 0L
        override suspend fun balanceMsat(pubkey: String) = 0L
    }

    private fun flow(payments: FakePayments, nwc: String? = "nostr+walletconnect://x", opened: MutableList<String> = mutableListOf()): NativeShellZapFlow {
        val signer = object : NativeNostrSigner {
            override val pubkeyHex: String? = "d".repeat(64)
            override val signsSilently = true
            override suspend fun sign(draft: app.roadstr.core.protocol.nostr.NostrEventDraft): Map<String, Any?>? = null
            override suspend fun nip44Encrypt(peerPubkeyHex: String, plaintext: String): String? = null
            override suspend fun nip44Decrypt(peerPubkeyHex: String, payload: String): String? = null
        }
        return NativeShellZapFlow(nostrBridge(signer, payments, nwc, opened))
    }

    @Test
    fun `a wallet confirmation is a paid zap in satoshi`() = runBlocking {
        val payments = FakePayments(info = info)
        val steps = mutableListOf<NativeZapStatus>()

        val outcome = flow(payments).run(event, 21) { steps += it }

        assertEquals(NativeZapOutcome.Paid(21), outcome)
        assertEquals(21_000L, payments.lastAmountMsat)
        assertTrue(payments.requestSigned)
        assertEquals(
            listOf(NativeZapStatus.FetchingAddress, NativeZapStatus.RequestingInvoice, NativeZapStatus.OpeningWallet, NativeZapStatus.PayingViaNwc),
            steps,
        )
    }

    @Test
    fun `without a confirmed payment the invoice goes to a wallet app and nothing is claimed`() = runBlocking {
        val opened = mutableListOf<String>()
        val payments = FakePayments(info = info, preimage = null)

        val outcome = flow(payments, opened = opened).run(event, 100) {}

        assertEquals(NativeZapOutcome.WalletOpened, outcome)
        assertEquals(listOf("lightning:lnbc1invoice"), opened)
    }

    @Test
    fun `no saved wallet means the deep link straight away`() = runBlocking {
        val opened = mutableListOf<String>()
        val payments = FakePayments(info = info)

        val outcome = flow(payments, nwc = null, opened = opened).run(event, 100) {}

        assertEquals(NativeZapOutcome.WalletOpened, outcome)
        assertEquals(null, payments.paidWith)
    }

    @Test
    fun `each missing step stops the zap with its own message`() = runBlocking {
        assertEquals(
            NativeZapOutcome.Failed(NativeZapStatus.NoAddress),
            flow(FakePayments(address = null)).run(event, 21) {},
        )
        assertEquals(
            NativeZapOutcome.Failed(NativeZapStatus.LnurlUnavailable),
            flow(FakePayments(info = null)).run(event, 21) {},
        )
        assertEquals(
            NativeZapOutcome.Failed(NativeZapStatus.InvoiceFailed),
            flow(FakePayments(info = info, invoice = null)).run(event, 21) {},
        )
    }

    @Test
    fun `a zero amount is refused before anything is asked`() = runBlocking {
        val payments = FakePayments(info = info)
        assertEquals(NativeZapOutcome.Failed(NativeZapStatus.InvoiceFailed), flow(payments).run(event, 0) {})
    }

    private fun nostrBridge(
        signer: NativeNostrSigner,
        payments: NativeZapPayments,
        nwc: String?,
        opened: MutableList<String>,
    ): NativeShellNostr {
        val network = FakeRelayNetwork()
        val relays = listOf("wss://a.example")
        return NativeShellNostr(
            signer = signer,
            roadEvents = NativeRoadEventService(
                connector = FakeConnector(),
                publisher = NativeRelayPublisher(network, relays),
                scheduler = ManualScheduler(),
                relays = relays,
                scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            ),
            favoritesSync = NativeFavoritesSyncService(signer, network, object : NativeFavoritesSyncStore {
                override var lastCreatedAt: Long? = null
                override var legacyCleaned: Boolean = false
            }, { null }, { null }),
            visibility = NativeProfileVisibilityService(signer, NativeRelayPublisher(network, relays), NativeRelayFetcher(network), relays),
            syncSecrets = object : NativeShellSyncSecrets {
                override fun passphraseConfigured() = false
                override fun setPassphrase(value: String) = true
                override fun customRelay(): String? = null
                override fun setCustomRelay(value: String) = true
                override fun lastSyncMillis(): Long? = null
                override fun markSynced(epochMillis: Long) = Unit
            },
            files = object : NativeShellFavoriteFiles {
                override fun export(fileName: String, content: String) = Unit
                override fun requestImport() = Unit
                override val imports = MutableSharedFlow<String>()
            },
            profileLookup = { null },
            reportPrivacyAcknowledged = { true },
            acknowledgeReportPrivacy = {},
            zaps = payments,
            nwcUri = { nwc },
            openWallet = { opened += it },
        )
    }
}
