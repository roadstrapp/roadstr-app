package app.roadstr.feature.settings

import app.roadstr.core.discovery.web.ConnectionTest
import app.roadstr.core.discovery.web.EndpointCheck
import app.roadstr.core.discovery.web.EndpointRejection
import app.roadstr.core.discovery.web.SearxngCapability
import app.roadstr.core.discovery.web.SearxngEndpointPolicy
import app.roadstr.core.discovery.web.WebDiscoveryMode
import app.roadstr.core.discovery.web.WebDiscoverySettings
import app.roadstr.feature.search.NativeWebProblem
import app.roadstr.feature.search.toWebProblem
import kotlinx.coroutines.CancellationException

/** What the "test connection" line under the web search settings says. */
sealed interface NativeWebSearchStatus {
    data object Idle : NativeWebSearchStatus

    data object Testing : NativeWebSearchStatus

    data object Works : NativeWebSearchStatus

    data object NotConfigured : NativeWebSearchStatus

    data class Failed(val problem: NativeWebProblem) : NativeWebSearchStatus

    data class Rejected(val reason: EndpointRejection) : NativeWebSearchStatus
}

/** The settings after an edit, and why an address was refused, if it was. */
data class NativeWebSearchEdit(val settings: WebDiscoverySettings, val rejection: EndpointRejection? = null)

/**
 * The rules for editing the web search settings, apart from the screen: an address is only
 * kept when it can be used, so the saved value never holds something the provider would
 * refuse later, with one exception that the screen explains (plain http on a local network
 * waits for the "this is my own instance" box).
 */
object NativeWebSearchEditor {
    fun setMode(settings: WebDiscoverySettings, mode: WebDiscoveryMode): WebDiscoverySettings =
        settings.copy(mode = mode)

    fun setEndpoint(settings: WebDiscoverySettings, text: String): NativeWebSearchEdit {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return NativeWebSearchEdit(settings.copy(endpointText = ""))
        return when (val check = SearxngEndpointPolicy.check(trimmed, settings.ownInstanceConfirmed)) {
            is EndpointCheck.Accepted -> keep(settings, trimmed)
            is EndpointCheck.Rejected -> when (check.reason) {
                EndpointRejection.LOCAL_HTTP_NOT_CONFIRMED -> keep(settings, trimmed)
                else -> NativeWebSearchEdit(settings, check.reason)
            }
        }
    }

    private fun keep(settings: WebDiscoverySettings, text: String): NativeWebSearchEdit {
        val stored = settings.copy(endpointText = text)
        return NativeWebSearchEdit(stored, rejectionOf(stored))
    }

    fun setOwnInstance(settings: WebDiscoverySettings, confirmed: Boolean): WebDiscoverySettings =
        settings.copy(ownInstanceConfirmed = confirmed)

    /** What is wrong with the saved address right now, or null when it is usable or empty. */
    fun rejectionOf(settings: WebDiscoverySettings): EndpointRejection? {
        if (settings.endpointText.isBlank()) return null
        return (SearxngEndpointPolicy.check(settings.endpointText, settings.ownInstanceConfirmed) as? EndpointCheck.Rejected)
            ?.reason
    }

    /** The host of the saved address, which is all the screen shows of it. */
    fun hostOf(settings: WebDiscoverySettings): String? =
        (SearxngEndpointPolicy.check(settings.endpointText, settings.ownInstanceConfirmed) as? EndpointCheck.Accepted)
            ?.endpoint?.host

    /** Whether web search can run at all: switched on and pointed at a usable instance. */
    fun isActive(settings: WebDiscoverySettings): Boolean =
        settings.mode != WebDiscoveryMode.OFF && hostOf(settings) != null
}

fun ConnectionTest.toStatus(): NativeWebSearchStatus = when (this) {
    is ConnectionTest.Rejected -> NativeWebSearchStatus.Rejected(reason)
    ConnectionTest.NotConfigured -> NativeWebSearchStatus.NotConfigured
    is ConnectionTest.Tested -> when (val found = capability) {
        is SearxngCapability.Compatible -> NativeWebSearchStatus.Works
        else -> NativeWebSearchStatus.Failed(found.toWebProblem() ?: NativeWebProblem.Unreachable)
    }
}

/** Runs the connection test; whatever goes wrong inside it reads as "could not be reached". */
suspend fun runWebConnectionTest(test: suspend () -> ConnectionTest): NativeWebSearchStatus = try {
    test().toStatus()
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    NativeWebSearchStatus.Failed(NativeWebProblem.Unreachable)
}
