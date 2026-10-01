package app.roadstr.feature.onboarding

import java.util.Collections
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class NativeStartupGateStatus {
    Hidden,
    Migrating,
    RecoveryRequired,
    Onboarding,
    Ready,
}

enum class NativeOnboardingPage { Welcome, Identity, Setup, Ready }

enum class NativeOnboardingIdentityStatus {
    Disconnected,
    WaitingAmber,
    Connected,
    InvalidNsec,
}

enum class NativeOnboardingLocationStatus { Checking, Required, Granted }

enum class NativeOnboardingVoiceStatus { Checking, NotDownloaded, Downloading, Ready }

enum class NativeMigrationReadiness { Checking, Ready, Failed }

data class NativeOnboardingInput(
    val protectedStorageAvailable: Boolean,
    val migrationReadiness: NativeMigrationReadiness,
    val privacyDisclosureV2: Any?,
    val identityStatus: NativeOnboardingIdentityStatus =
        NativeOnboardingIdentityStatus.Disconnected,
    val identityLabel: String? = null,
    val profilePublic: Boolean = false,
    val locationStatus: NativeOnboardingLocationStatus =
        NativeOnboardingLocationStatus.Checking,
    val voiceStatus: NativeOnboardingVoiceStatus = NativeOnboardingVoiceStatus.Checking,
    val voiceProgress: Double = 0.0,
)

data class NativeOnboardingSnapshot(
    val revision: Long,
    val status: NativeStartupGateStatus,
    val page: NativeOnboardingPage,
    val identityStatus: NativeOnboardingIdentityStatus,
    val identityLabel: String?,
    val profilePublic: Boolean,
    val locationStatus: NativeOnboardingLocationStatus,
    val voiceStatus: NativeOnboardingVoiceStatus,
    val voiceProgress: Double,
    val disclosureVisible: Boolean,
) {
    companion object {
        const val NO_REVISION = -1L

        fun hidden(revision: Long = NO_REVISION) = NativeOnboardingSnapshot(
            revision = revision,
            status = NativeStartupGateStatus.Hidden,
            page = NativeOnboardingPage.Welcome,
            identityStatus = NativeOnboardingIdentityStatus.Disconnected,
            identityLabel = null,
            profilePublic = false,
            locationStatus = NativeOnboardingLocationStatus.Checking,
            voiceStatus = NativeOnboardingVoiceStatus.Checking,
            voiceProgress = 0.0,
            disclosureVisible = false,
        )
    }
}

data class NativeOnboardingCompletion(
    val writes: Map<String, Boolean>,
)

/** Pure first-frame gate. Legacy acceptance flags intentionally have no input. */
object NativeOnboardingPresenter {
    const val PRIVACY_DISCLOSURE_KEY = "privacy_disclosure_v2"
    const val LEGACY_DISCLAIMER_KEY = "disclaimer_accepted"
    const val LEGACY_ONBOARDING_KEY = "onboarding_v1"
    const val MAX_IDENTITY_LABEL_CHARS = 200
    private val CONTROLS = Regex("[\\u0000-\\u001f]")

    fun present(revision: Long, input: NativeOnboardingInput): NativeOnboardingSnapshot {
        require(revision >= 0) { "Onboarding revision must be non-negative" }
        require(input.voiceProgress.isFinite() && input.voiceProgress in 0.0..1.0) {
            "Voice progress must be between zero and one"
        }
        val status = when {
            !input.protectedStorageAvailable -> NativeStartupGateStatus.RecoveryRequired
            input.migrationReadiness == NativeMigrationReadiness.Failed ->
                NativeStartupGateStatus.RecoveryRequired
            input.migrationReadiness == NativeMigrationReadiness.Checking ->
                NativeStartupGateStatus.Migrating
            input.privacyDisclosureV2 is Boolean && input.privacyDisclosureV2 ->
                NativeStartupGateStatus.Ready
            else -> NativeStartupGateStatus.Onboarding
        }
        val identityLabel = input.identityLabel?.let(::cleanIdentityLabel)
        require(
            input.identityStatus == NativeOnboardingIdentityStatus.Connected ||
                identityLabel == null,
        ) { "Only a connected identity may expose a label" }
        return NativeOnboardingSnapshot(
            revision = revision,
            status = status,
            page = NativeOnboardingPage.Welcome,
            identityStatus = input.identityStatus,
            identityLabel = identityLabel,
            profilePublic = input.profilePublic,
            locationStatus = input.locationStatus,
            voiceStatus = input.voiceStatus,
            voiceProgress = when (input.voiceStatus) {
                NativeOnboardingVoiceStatus.Ready -> 1.0
                NativeOnboardingVoiceStatus.Downloading -> input.voiceProgress
                NativeOnboardingVoiceStatus.Checking,
                NativeOnboardingVoiceStatus.NotDownloaded,
                -> 0.0
            },
            disclosureVisible = false,
        )
    }

    fun completion(): NativeOnboardingCompletion = NativeOnboardingCompletion(
        Collections.unmodifiableMap(
            linkedMapOf(
                LEGACY_DISCLAIMER_KEY to true,
                LEGACY_ONBOARDING_KEY to true,
                PRIVACY_DISCLOSURE_KEY to true,
            ),
        ),
    )

    private fun cleanIdentityLabel(value: String): String {
        val cleaned = value.replace(CONTROLS, " ").trim().replace(Regex(" +"), " ")
        require(cleaned.isNotEmpty() && cleaned.length <= MAX_IDENTITY_LABEL_CHARS) {
            "Invalid onboarding identity label"
        }
        return cleaned
    }
}

/**
 * Revision-fenced, in-memory onboarding coordinator.
 *
 * It owns no persistence, migration, identity, permission, download or network adapter.
 */
class NativeOnboardingSession {
    private val mutableState = MutableStateFlow(NativeOnboardingSnapshot.hidden())
    val state: StateFlow<NativeOnboardingSnapshot> = mutableState.asStateFlow()

    fun begin(revision: Long, input: NativeOnboardingInput): Boolean {
        if (revision <= mutableState.value.revision) return false
        mutableState.value = NativeOnboardingPresenter.present(revision, input)
        return true
    }

    fun selectPage(revision: Long, page: NativeOnboardingPage): Boolean = mutate(revision) {
        if (status != NativeStartupGateStatus.Onboarding || this.page == page) return@mutate null
        copy(page = page, disclosureVisible = false)
    }

    fun updateIdentity(
        revision: Long,
        status: NativeOnboardingIdentityStatus,
        label: String? = null,
    ): Boolean = mutate(revision) {
        if (this.status != NativeStartupGateStatus.Onboarding) return@mutate null
        val projected = NativeOnboardingPresenter.present(
            revision,
            NativeOnboardingInput(
                protectedStorageAvailable = true,
                migrationReadiness = NativeMigrationReadiness.Ready,
                privacyDisclosureV2 = false,
                identityStatus = status,
                identityLabel = label,
                profilePublic = profilePublic,
                locationStatus = locationStatus,
                voiceStatus = voiceStatus,
                voiceProgress = voiceProgress,
            ),
        )
        if (identityStatus == projected.identityStatus && identityLabel == projected.identityLabel) {
            null
        } else {
            copy(identityStatus = projected.identityStatus, identityLabel = projected.identityLabel)
        }
    }

    fun updateProfileVisibility(revision: Long, value: Boolean): Boolean = mutate(revision) {
        if (status != NativeStartupGateStatus.Onboarding || profilePublic == value) null
        else copy(profilePublic = value)
    }

    fun updateLocation(
        revision: Long,
        value: NativeOnboardingLocationStatus,
    ): Boolean = mutate(revision) {
        if (status != NativeStartupGateStatus.Onboarding || locationStatus == value) null
        else copy(locationStatus = value)
    }

    fun updateVoice(
        revision: Long,
        value: NativeOnboardingVoiceStatus,
        progress: Double = 0.0,
    ): Boolean = mutate(revision) {
        if (status != NativeStartupGateStatus.Onboarding) return@mutate null
        val projected = NativeOnboardingPresenter.present(
            revision,
            NativeOnboardingInput(
                protectedStorageAvailable = true,
                migrationReadiness = NativeMigrationReadiness.Ready,
                privacyDisclosureV2 = false,
                identityStatus = identityStatus,
                identityLabel = identityLabel,
                profilePublic = profilePublic,
                locationStatus = locationStatus,
                voiceStatus = value,
                voiceProgress = progress,
            ),
        )
        if (voiceStatus == projected.voiceStatus && voiceProgress == projected.voiceProgress) null
        else copy(voiceStatus = projected.voiceStatus, voiceProgress = projected.voiceProgress)
    }

    fun openDisclosure(revision: Long): Boolean = mutate(revision) {
        if (
            status != NativeStartupGateStatus.Onboarding ||
            page != NativeOnboardingPage.Ready ||
            disclosureVisible
        ) {
            null
        } else {
            copy(disclosureVisible = true)
        }
    }

    fun acceptDisclosure(revision: Long): NativeOnboardingCompletion? {
        val current = mutableState.value
        if (
            current.revision != revision ||
            current.status != NativeStartupGateStatus.Onboarding ||
            current.page != NativeOnboardingPage.Ready ||
            !current.disclosureVisible
        ) {
            return null
        }
        mutableState.value = current.copy(
            status = NativeStartupGateStatus.Ready,
            disclosureVisible = false,
        )
        return NativeOnboardingPresenter.completion()
    }

    fun hide(revision: Long): Boolean {
        val current = mutableState.value
        if (current.revision != revision || current.status == NativeStartupGateStatus.Hidden) {
            return false
        }
        mutableState.value = NativeOnboardingSnapshot.hidden(revision)
        return true
    }

    private inline fun mutate(
        revision: Long,
        transform: NativeOnboardingSnapshot.() -> NativeOnboardingSnapshot?,
    ): Boolean {
        val current = mutableState.value
        if (current.revision != revision) return false
        val next = current.transform() ?: return false
        mutableState.value = next
        return true
    }
}
