package app.roadstr.service.nostr

import app.roadstr.core.protocol.lightning.Bolt11Invoice
import app.roadstr.core.protocol.lightning.LnurlPayInfo
import app.roadstr.core.protocol.lightning.LnurlProtocol
import app.roadstr.core.protocol.lightning.Nip57Protocol
import app.roadstr.core.protocol.lightning.NwcConnection
import app.roadstr.core.protocol.lightning.NwcEncryptionScheme
import app.roadstr.core.protocol.lightning.NwcEncryptionSelection
import app.roadstr.core.protocol.lightning.NwcProtocol
import app.roadstr.core.protocol.nostr.BoundedJsonParser
import app.roadstr.core.protocol.nostr.Nip04Cipher
import app.roadstr.core.protocol.nostr.Nip44V2
import app.roadstr.core.protocol.nostr.NostrIngressRoute
import app.roadstr.core.protocol.nostr.NostrIngressRule
import app.roadstr.core.protocol.nostr.NostrRelayEoseMessage
import app.roadstr.core.protocol.nostr.NostrRelayEventMessage
import app.roadstr.core.protocol.nostr.NostrRelayIngress
import app.roadstr.core.protocol.nostr.NostrRelayMessageDecoder
import app.roadstr.core.protocol.nostr.NostrRelayWire
import app.roadstr.core.protocol.nostr.NostrSchnorr
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

/** What the shell needs from the Lightning side; [NativeZapService] is the real one. */
interface NativeZapPayments {
    suspend fun lightningAddress(pubkey: String): String?

    suspend fun payInfo(address: String): LnurlPayInfo?

    suspend fun zapRequest(
        signer: NativeNostrSigner,
        recipient: String,
        eventId: String,
        amountMsat: Long,
    ): Map<String, Any?>?

    suspend fun invoice(info: LnurlPayInfo, amountMsat: Long, zapRequest: Map<String, Any?>?): String?

    suspend fun payViaNwc(invoice: String, nwcUri: String): String?

    suspend fun zapTotalMsat(eventId: String, recipient: String): Long

    suspend fun balanceMsat(pubkey: String): Long
}

/**
 * Zaps for road reports: a Lightning address read from the reporter's Nostr
 * profile, an LNURL-pay invoice carrying a signed NIP-57 request, and payment
 * through the user's NWC wallet. A port of the Flutter ZapService.
 *
 * Everything a relay or an LNURL server says is treated as hostile: the
 * profile that names the address is verified, the invoice has to match the
 * amount and the request that was sent, and a zap receipt only counts when
 * the provider the address advertises signed it.
 */
class NativeZapService(
    private val connector: NativeRelayConnector,
    private val http: NativeLnurlHttp = OkHttpLnurlHttp(),
    private val relays: List<String> = NativeRoadEventService.DEFAULT_RELAYS,
    private val nowSeconds: () -> Long = NativeNostrWire::nowSeconds,
    private val diagnostics: (String) -> Unit = {},
) : NativeZapPayments {
    private val fetcher = NativeRelayFetcher(connector)
    private val receiptFetcher = NativeRelayFetcher(connector, timeoutMillis = RECEIPT_TIMEOUT_MILLIS)
    private val addressCache = ConcurrentHashMap<String, Pair<String, Long>>()

    /** `lud16` (or, failing that, `lud06`) of [pubkey]'s newest verified profile. */
    override suspend fun lightningAddress(pubkey: String): String? {
        if (!NativeNostrWire.isHex32(pubkey)) return null
        addressCache[pubkey]?.let { (address, until) -> if (nowSeconds() < until) return address }
        val perRelay = coroutineScope {
            relays.map { url ->
                async {
                    fetcher.fetch(
                        url = url,
                        subscriptionId = NativeNostrWire.randomSubscriptionId(),
                        filter = linkedMapOf("kinds" to listOf(0), "authors" to listOf(pubkey), "limit" to 1),
                        kind = 0,
                        route = NostrIngressRoute.LIGHTNING_ADDRESS,
                        maxEvents = 10,
                        accept = { event -> event["pubkey"] == pubkey && NativeNostrWire.verify(event) },
                    )
                }
            }.awaitAll()
        }
        // The newest profile wins: a relay holding a stale one must not send a
        // zap to an address the user has since changed.
        val newest = perRelay.flatten()
            .mapNotNull { event -> addressOf(event)?.let { it to (NativeNostrWire.integral(event["created_at"]) ?: 0L) } }
            .maxByOrNull { it.second }
            ?.first ?: return null
        if (addressCache.size > MAX_CACHE) addressCache.clear()
        addressCache[pubkey] = newest to (nowSeconds() + CACHE_SECONDS)
        return newest
    }

    private fun addressOf(event: Map<String, Any?>): String? {
        val content = event["content"] as? String ?: return null
        val profile = runCatching { BoundedJsonParser(content).parse() as? Map<*, *> }.getOrNull() ?: return null
        val lud16 = (profile["lud16"] as? String)?.trim().orEmpty()
        if (lud16.isNotEmpty()) return lud16.take(LnurlProtocol.MAX_ADDRESS_LENGTH)
        val lud06 = (profile["lud06"] as? String)?.trim().orEmpty()
        return lud06.takeIf { it.startsWith("lnurl1", ignoreCase = true) && it.length <= MAX_LNURL_CHARS }
    }

    override suspend fun payInfo(address: String): LnurlPayInfo? {
        val url = LnurlProtocol.resolveMetadataUrl(address) ?: return null
        val data = http.getJson(url, METADATA_TIMEOUT_MILLIS) ?: return null
        return LnurlProtocol.parsePayInfo(data)
    }

    /** A signed NIP-57 kind-9734 request for [eventId], or null if the signer declines. */
    override suspend fun zapRequest(
        signer: NativeNostrSigner,
        recipient: String,
        eventId: String,
        amountMsat: Long,
    ): Map<String, Any?>? {
        val sender = signer.pubkeyHex ?: return null
        val draft = runCatching {
            Nip57Protocol.zapRequestDraft(sender, nowSeconds(), recipient, eventId, amountMsat, relays)
        }.getOrNull() ?: return null
        return signer.sign(draft)
    }

    /** A BOLT-11 invoice for exactly [amountMsat], validated against the request. */
    override suspend fun invoice(info: LnurlPayInfo, amountMsat: Long, zapRequest: Map<String, Any?>?): String? {
        val request = LnurlProtocol.buildInvoiceRequest(info, amountMsat, zapRequest) ?: return null
        val data = http.getJson(request.url, INVOICE_TIMEOUT_MILLIS) ?: return null
        return LnurlProtocol.validateInvoiceResponse(data, request, amountMsat, nowSeconds())
    }

    /**
     * Pays [invoice] through NIP-47 and returns the verified preimage, or null
     * on a timeout, a refusal, a forged or mismatching response.
     *
     * One socket carries the capability lookup (NIP-44 when the wallet says
     * so, NIP-04 for wallets that predate discovery), the request and the
     * answer; the answer subscription opens before the request is sent so a
     * fast wallet cannot be missed.
     */
    override suspend fun payViaNwc(invoice: String, nwcUri: String): String? {
        val connection = NwcConnection.parseOrNull(nwcUri, NWC_FALLBACK_RELAY) ?: return null
        val decoded = Bolt11Invoice.parseOrNull(invoice) ?: return null
        if (decoded.isExpiredAt(nowSeconds())) return null
        val wallet = connection.walletPubkey
        val secret = connection.secret
        val ourPubkey = runCatching { NostrSchnorr.publicKey(secret) }.getOrNull() ?: return null

        val infoSub = NativeNostrWire.randomSubscriptionId()
        val responseSub = NativeNostrWire.randomSubscriptionId()
        val ingress = NostrRelayIngress(
            listOf(
                NostrIngressRule("nwc-info", infoSub, mapOf(13194 to NostrIngressRoute.NWC_INFO), maxEvents = 2),
                NostrIngressRule("nwc-response", responseSub, mapOf(23195 to NostrIngressRoute.NWC_RESPONSE)),
            ),
        )
        val opened = CompletableDeferred<Boolean>()
        val info = CompletableDeferred<InfoOutcome>()
        val answer = CompletableDeferred<String?>()
        val state = NwcState()

        val socket = try {
            connector.connect(
                connection.relayUri.toString(),
                object : NativeRelayEvents {
                    override fun onOpen() {
                        opened.complete(true)
                    }

                    override fun onMessage(text: String) {
                        runCatching {
                            handleNwcMessage(text, ingress, infoSub, wallet, secret, ourPubkey, decoded, state, info, answer)
                        }
                    }

                    override fun onEnded() {
                        opened.complete(false)
                        info.complete(InfoOutcome(null))
                        answer.complete(null)
                    }
                },
            )
        } catch (_: Exception) {
            return null
        }
        try {
            if (withTimeoutOrNull(CONNECT_TIMEOUT_MILLIS) { opened.await() } != true) return null
            socket.send(NostrRelayWire.encode(NwcProtocol.infoRequest(infoSub, wallet)))
            // A wallet whose info event never shows up predates capability
            // discovery, and every such wallet speaks NIP-04.
            val selection = (withTimeoutOrNull(INFO_TIMEOUT_MILLIS) { info.await() }
                ?: InfoOutcome(NwcEncryptionSelection.legacyNip04())).selection ?: return null
            socket.send(NostrRelayWire.encode(listOf("CLOSE", infoSub)))

            val command = NwcProtocol.payInvoiceCommand(invoice)
            val encrypted = when (selection.scheme) {
                NwcEncryptionScheme.NIP44_V2 -> Nip44V2.encrypt(secret, wallet, command)
                NwcEncryptionScheme.NIP04 -> Nip04Cipher.encrypt(secret, wallet, command)
            }
            val draft = NwcProtocol.requestDraft(ourPubkey, nowSeconds(), wallet, encrypted, selection)
            val request = NostrSchnorr.signEvent(draft, secret)
            val requestId = request["id"] as String
            state.requestId = requestId
            state.selection = selection
            socket.send(NostrRelayWire.encode(NwcProtocol.responseRequest(responseSub, wallet, requestId)))
            socket.send(NostrRelayWire.encode(NostrRelayWire.publish(request)))
            return withTimeoutOrNull(RESPONSE_TIMEOUT_MILLIS) { answer.await() }
        } finally {
            socket.close()
        }
    }

    private class InfoOutcome(val selection: NwcEncryptionSelection?)

    private class NwcState {
        @Volatile var requestId: String? = null
        @Volatile var selection: NwcEncryptionSelection? = null
    }

    private fun handleNwcMessage(
        text: String,
        ingress: NostrRelayIngress,
        infoSub: String,
        wallet: String,
        secret: String,
        ourPubkey: String,
        invoice: Bolt11Invoice,
        state: NwcState,
        info: CompletableDeferred<InfoOutcome>,
        answer: CompletableDeferred<String?>,
    ) {
        when (val message = NostrRelayMessageDecoder.decode(text).message) {
            is NostrRelayEventMessage -> {
                val event = message.event
                val decision = ingress.inspect(message.subscriptionId, event["kind"])
                if (!decision.shouldVerify) return
                if (message.subscriptionId == infoSub) {
                    if (info.isCompleted) return
                    val outcome = NwcProtocol.inspectInfoEvent(event, wallet) { NativeNostrWire.verify(event) }
                    if (outcome.shouldComplete) info.complete(InfoOutcome(outcome.selection))
                    return
                }
                val requestId = state.requestId ?: return
                val selection = state.selection ?: return
                if (answer.isCompleted) return
                if (!NwcProtocol.responseEventIsBound(event, wallet, requestId, ourPubkey) ||
                    !NativeNostrWire.verify(event)
                ) {
                    return
                }
                val content = event["content"] as? String ?: return
                val plain = when (selection.scheme) {
                    NwcEncryptionScheme.NIP44_V2 -> Nip44V2.decrypt(secret, wallet, content)
                    NwcEncryptionScheme.NIP04 -> Nip04Cipher.decrypt(secret, wallet, content)
                }
                val response = BoundedJsonParser(plain).parse() as? Map<*, *> ?: return
                @Suppress("UNCHECKED_CAST")
                val decision2 = NwcProtocol.inspectResponse(response as Map<String, Any?>, invoice)
                if (decision2.shouldComplete) answer.complete(decision2.preimage)
            }

            is NostrRelayEoseMessage ->
                if (message.subscriptionId == infoSub && !info.isCompleted) {
                    info.complete(InfoOutcome(NwcEncryptionSelection.legacyNip04()))
                }

            else -> Unit
        }
    }

    // ── receipts ─────────────────────────────────────────────────────────────

    /** The key the recipient's LNURL provider signs zap receipts with, if it supports Nostr. */
    suspend fun zapSigner(recipient: String): String? {
        val address = lightningAddress(recipient) ?: return null
        val info = payInfo(address) ?: return null
        return if (info.allowsNostr) info.nostrPubkey else null
    }

    /** Millisatoshi zapped to [eventId], counting each verified receipt once. */
    override suspend fun zapTotalMsat(eventId: String, recipient: String): Long {
        if (!NativeNostrWire.isHex32(eventId)) return 0
        val signer = zapSigner(recipient) ?: return 0
        return sumReceipts(
            filter = linkedMapOf("kinds" to listOf(9735), "#e" to listOf(eventId), "limit" to RECEIPTS_PER_EVENT),
            limit = RECEIPTS_PER_EVENT,
            eventId = eventId,
            recipient = recipient,
            signer = signer,
            fetcher = fetcher,
        )
    }

    /** Lifetime millisatoshi zapped to [pubkey] across all their reports. */
    override suspend fun balanceMsat(pubkey: String): Long {
        if (!NativeNostrWire.isHex32(pubkey)) return 0
        val signer = zapSigner(pubkey) ?: return 0
        return sumReceipts(
            filter = linkedMapOf("kinds" to listOf(9735), "#p" to listOf(pubkey), "limit" to RECEIPTS_FOR_BALANCE),
            limit = RECEIPTS_FOR_BALANCE,
            eventId = null,
            recipient = pubkey,
            signer = signer,
            fetcher = receiptFetcher,
        )
    }

    private suspend fun sumReceipts(
        filter: Map<String, Any?>,
        limit: Int,
        eventId: String?,
        recipient: String,
        signer: String,
        fetcher: NativeRelayFetcher,
    ): Long {
        // One map for every relay: a receipt held by several is verified and counted once.
        val receipts = ConcurrentHashMap<String, Long>()
        coroutineScope {
            relays.map { url ->
                async {
                    fetcher.fetch(
                        url = url,
                        subscriptionId = NativeNostrWire.randomSubscriptionId(),
                        filter = filter,
                        kind = 9735,
                        route = NostrIngressRoute.ZAP_RECEIPT_QUERY,
                        maxEvents = limit,
                        accept = { event ->
                            val id = event["id"] as? String
                            if (id != null && !receipts.containsKey(id)) {
                                verifiedReceiptAmount(event, eventId, recipient, signer)?.let { receipts[id] = it }
                            }
                            false
                        },
                    )
                }
            }.awaitAll()
        }
        return receipts.values.sum()
    }

    /**
     * A relay is not proof of payment, so a receipt counts only when the
     * provider the address advertises signed it and the invoice, the request
     * and the amount all agree.
     */
    fun verifiedReceiptAmount(
        receipt: Map<String, Any?>,
        eventId: String?,
        recipient: String?,
        receiptSigner: String,
    ): Long? = runCatching {
        val envelope = Nip57Protocol.inspectReceipt(receipt, receiptSigner) { NativeNostrWire.verify(receipt) }
            ?: return null
        val invoice = Bolt11Invoice.parseOrNull(envelope.bolt11) ?: return null
        val request = (BoundedJsonParser(envelope.description).parse() as? Map<*, *>)
            ?.entries?.associate { (key, value) -> key.toString() to value } ?: return null
        Nip57Protocol.boundReceiptAmount(
            envelope = envelope,
            invoice = invoice,
            request = request,
            verifyRequestSignature = { NativeNostrWire.verify(request) },
            eventId = eventId,
            recipientPubkey = recipient,
        )
    }.getOrNull()

    companion object {
        const val NWC_FALLBACK_RELAY = "wss://relay.damus.io"
        private const val METADATA_TIMEOUT_MILLIS = 6_000L
        private const val INVOICE_TIMEOUT_MILLIS = 10_000L
        private const val CONNECT_TIMEOUT_MILLIS = 5_000L
        private const val INFO_TIMEOUT_MILLIS = 5_000L
        private const val RESPONSE_TIMEOUT_MILLIS = 30_000L
        private const val RECEIPT_TIMEOUT_MILLIS = 8_000L
        private const val RECEIPTS_PER_EVENT = 500
        private const val RECEIPTS_FOR_BALANCE = 1_000
        private const val CACHE_SECONDS = 600L
        private const val MAX_CACHE = 64
        private const val MAX_LNURL_CHARS = 2_048
    }
}
