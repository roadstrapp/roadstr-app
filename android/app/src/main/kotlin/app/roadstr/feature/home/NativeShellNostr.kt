package app.roadstr.feature.home

import app.roadstr.feature.profile.NativeProfileMetadata
import app.roadstr.service.nostr.NativeActivityService
import app.roadstr.service.nostr.NativeActivityStore
import app.roadstr.service.nostr.NativeFavoritesSyncService
import app.roadstr.service.nostr.NativeNostrSigner
import app.roadstr.service.nostr.NativeProfileVisibilityService
import app.roadstr.service.nostr.NativeRoadEventService
import app.roadstr.service.nostr.NativeUserReportsService
import app.roadstr.service.nostr.NativeZapPayments
import kotlinx.coroutines.flow.Flow

/** Secrets of favourites sync; they live in protected storage, never in the UI model. */
interface NativeShellSyncSecrets {
    fun passphraseConfigured(): Boolean

    /** An empty value removes the passphrase. */
    fun setPassphrase(value: String): Boolean

    fun customRelay(): String?

    /** An empty value removes the relay; false when [value] is not a valid wss:// address. */
    fun setCustomRelay(value: String): Boolean

    fun lastSyncMillis(): Long?

    fun markSynced(epochMillis: Long)
}

/** Export and import of the favourites file through the system file picker. */
interface NativeShellFavoriteFiles {
    /** Offers [content] to the user as a file to save. */
    fun export(fileName: String, content: String)

    /** Opens the picker; the chosen file's text arrives on [imports]. */
    fun requestImport()

    val imports: Flow<String>
}

/**
 * Everything Nostr-shaped the shell can use. All of it is optional: with no
 * bridge the shell keeps working as a map without community reports or sync.
 */
class NativeShellNostr(
    val signer: NativeNostrSigner,
    val roadEvents: NativeRoadEventService,
    val favoritesSync: NativeFavoritesSyncService,
    val visibility: NativeProfileVisibilityService,
    val syncSecrets: NativeShellSyncSecrets,
    val files: NativeShellFavoriteFiles,
    /** Profile name/avatar of a public reporter. */
    val profileLookup: suspend (String) -> NativeProfileMetadata?,
    val reportPrivacyAcknowledged: () -> Boolean,
    val acknowledgeReportPrivacy: () -> Unit,
    /** Zaps, zap totals and the profile balance; without it those controls stay hidden. */
    val zaps: NativeZapPayments? = null,
    /** The account's own reports and the edit requests waiting on them. */
    val userReports: NativeUserReportsService? = null,
    /** Polls the account's own activity into the inbox. */
    val activity: NativeActivityService? = null,
    val activityStore: NativeActivityStore? = null,
    /** The saved `nostr+walletconnect://` URI, read when a zap is paid. */
    val nwcUri: () -> String? = { null },
    /** Hands a `lightning:` link to a wallet app. */
    val openWallet: (String) -> Unit = {},
)
