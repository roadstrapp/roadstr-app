package app.roadstr.feature.home

import androidx.annotation.StringRes
import app.roadstr.R
import app.roadstr.service.nostr.NativeRoadEvent
import kotlinx.coroutines.launch

/** Where a zap is, in the words the dialog shows the driver. */
enum class NativeZapStatus(@StringRes val text: Int) {
    FetchingAddress(R.string.native_zap_status_fetching_address),
    NoAddress(R.string.native_zap_status_no_address),
    RequestingInvoice(R.string.native_zap_status_requesting_invoice),
    LnurlUnavailable(R.string.native_zap_status_lnurl_unavailable),
    InvoiceFailed(R.string.native_zap_status_invoice_failed),
    OpeningWallet(R.string.native_zap_status_opening_wallet),
    PayingViaNwc(R.string.native_zap_status_paying_nwc),
    NoWallet(R.string.native_zap_status_no_wallet),
}

sealed interface NativeZapOutcome {
    /** The wallet confirmed the payment with a verified preimage. */
    data class Paid(val sats: Int) : NativeZapOutcome

    /** The invoice was handed to a wallet app; whether it was paid is unknown. */
    data object WalletOpened : NativeZapOutcome

    data class Failed(val status: NativeZapStatus) : NativeZapOutcome
}

/**
 * The zap steps in order, as the Flutter sheet runs them: the reporter's
 * Lightning address, the LNURL-pay endpoint, a signed zap request, the
 * invoice, then payment through NWC with a deep link to a wallet app as the
 * fallback. Plain logic over [NativeShellNostr], so it runs without Compose.
 */
class NativeShellZapFlow(private val nostr: NativeShellNostr) {
    suspend fun run(
        event: NativeRoadEvent,
        sats: Int,
        report: (NativeZapStatus) -> Unit,
    ): NativeZapOutcome {
        val zaps = nostr.zaps ?: return NativeZapOutcome.Failed(NativeZapStatus.NoWallet)
        if (sats <= 0) return NativeZapOutcome.Failed(NativeZapStatus.InvoiceFailed)
        val amountMsat = sats * MSAT_PER_SAT

        report(NativeZapStatus.FetchingAddress)
        val address = zaps.lightningAddress(event.pubkey)
            ?: return NativeZapOutcome.Failed(NativeZapStatus.NoAddress)

        report(NativeZapStatus.RequestingInvoice)
        val info = zaps.payInfo(address)
            ?: return NativeZapOutcome.Failed(NativeZapStatus.LnurlUnavailable)
        // Without a signed request the zap still pays, it just earns no receipt.
        val request = zaps.zapRequest(nostr.signer, event.pubkey, event.id, amountMsat)
        val invoice = zaps.invoice(info, amountMsat, request)
            ?: return NativeZapOutcome.Failed(NativeZapStatus.InvoiceFailed)

        report(NativeZapStatus.OpeningWallet)
        val nwc = nostr.nwcUri()?.trim().orEmpty()
        if (nwc.isNotEmpty()) {
            report(NativeZapStatus.PayingViaNwc)
            val preimage = zaps.payViaNwc(invoice, nwc)
            if (!preimage.isNullOrEmpty()) return NativeZapOutcome.Paid(sats)
        }
        nostr.openWallet("lightning:$invoice")
        return NativeZapOutcome.WalletOpened
    }

    private companion object {
        const val MSAT_PER_SAT = 1_000L
    }
}

/** The zap dialog's state: which report, what the flow is doing, and whether it is busy. */
class NativeShellZapUi(
    val event: NativeRoadEvent,
    val status: NativeZapStatus? = null,
    val sending: Boolean = false,
)

/** Owns the zap dialog and runs [NativeShellZapFlow] from it. */
class NativeShellZapController(
    private val nostr: NativeShellNostr,
    private val scope: kotlinx.coroutines.CoroutineScope,
    private val onPaid: (Int) -> Unit,
    private val message: (NativeShellMessage, Int) -> Unit,
) {
    private val flow = NativeShellZapFlow(nostr)
    val state = androidx.compose.runtime.mutableStateOf<NativeShellZapUi?>(null)

    val available: Boolean get() = nostr.zaps != null

    fun open(event: NativeRoadEvent) {
        if (!available) return
        state.value = NativeShellZapUi(event)
    }

    fun send(sats: Int) {
        val ui = state.value ?: return
        if (ui.sending || sats <= 0) return
        state.value = NativeShellZapUi(ui.event, status = null, sending = true)
        scope.launch {
            val outcome = flow.run(ui.event, sats) { status ->
                state.value = state.value?.let { NativeShellZapUi(it.event, status, sending = true) }
            }
            when (outcome) {
                is NativeZapOutcome.Paid -> {
                    state.value = null
                    onPaid(outcome.sats)
                    message(NativeShellMessage.ZapSent, outcome.sats)
                }

                NativeZapOutcome.WalletOpened -> state.value = null
                is NativeZapOutcome.Failed ->
                    state.value = state.value?.let { NativeShellZapUi(it.event, outcome.status, sending = false) }
            }
        }
    }

    fun dismiss() {
        if (state.value?.sending != true) state.value = null
    }
}
