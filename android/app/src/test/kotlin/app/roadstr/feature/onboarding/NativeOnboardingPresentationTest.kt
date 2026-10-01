package app.roadstr.feature.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeOnboardingPresentationTest {
    @Test
    fun `only the exact current disclosure boolean unlocks the product`() {
        assertEquals(NativeStartupGateStatus.Ready, present(disclosure = true).status)
        assertEquals(NativeStartupGateStatus.Onboarding, present(disclosure = false).status)
        assertEquals(NativeStartupGateStatus.Onboarding, present(disclosure = null).status)
        assertEquals(NativeStartupGateStatus.Onboarding, present(disclosure = "true").status)
        assertEquals(NativeStartupGateStatus.Onboarding, present(disclosure = 1).status)
        val booleanImpostor = object {
            override fun equals(other: Any?): Boolean = other == true
        }
        assertEquals(NativeStartupGateStatus.Onboarding, present(disclosure = booleanImpostor).status)
    }

    @Test
    fun `protected storage and migration failures override accepted disclosure`() {
        assertEquals(
            NativeStartupGateStatus.RecoveryRequired,
            present(disclosure = true, storageAvailable = false).status,
        )
        assertEquals(
            NativeStartupGateStatus.RecoveryRequired,
            present(
                disclosure = true,
                migrationReadiness = NativeMigrationReadiness.Failed,
            ).status,
        )
        assertEquals(
            NativeStartupGateStatus.Migrating,
            present(
                disclosure = true,
                migrationReadiness = NativeMigrationReadiness.Checking,
            ).status,
        )
    }

    @Test
    fun `completion preserves the exact three Flutter compatibility flags`() {
        val writes = NativeOnboardingPresenter.completion().writes

        assertEquals(
            linkedMapOf(
                "disclaimer_accepted" to true,
                "onboarding_v1" to true,
                "privacy_disclosure_v2" to true,
            ),
            writes,
        )
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (writes as MutableMap<String, Boolean>)["future"] = true
        }
    }

    @Test
    fun `identity labels are bounded sanitized and connected only`() {
        val connected = present(
            identityStatus = NativeOnboardingIdentityStatus.Connected,
            identityLabel = "  Alice\u0000 Driver  ",
        )
        assertEquals("Alice Driver", connected.identityLabel)

        assertThrows(IllegalArgumentException::class.java) {
            present(identityLabel = "Alice")
        }
        assertThrows(IllegalArgumentException::class.java) {
            present(
                identityStatus = NativeOnboardingIdentityStatus.Connected,
                identityLabel = "x".repeat(201),
            )
        }
    }

    @Test
    fun `voice projection normalizes terminal progress`() {
        assertEquals(
            1.0,
            present(voiceStatus = NativeOnboardingVoiceStatus.Ready).voiceProgress,
            0.0,
        )
        assertEquals(
            0.0,
            present(
                voiceStatus = NativeOnboardingVoiceStatus.NotDownloaded,
                voiceProgress = 0.7,
            ).voiceProgress,
            0.0,
        )
        assertEquals(
            0.42,
            present(
                voiceStatus = NativeOnboardingVoiceStatus.Downloading,
                voiceProgress = 0.42,
            ).voiceProgress,
            0.0,
        )
        assertThrows(IllegalArgumentException::class.java) {
            present(voiceProgress = Double.NaN)
        }
    }

    @Test
    fun `session admits only newer startup revisions`() {
        val session = NativeOnboardingSession()

        assertTrue(session.begin(4, input()))
        assertFalse(session.begin(4, input()))
        assertFalse(session.begin(3, input()))
        assertEquals(NativeStartupGateStatus.Onboarding, session.state.value.status)
    }

    @Test
    fun `page selection is revision fenced and closes stale disclosure`() {
        val session = NativeOnboardingSession()
        assertTrue(session.begin(5, input()))

        assertFalse(session.selectPage(4, NativeOnboardingPage.Ready))
        assertTrue(session.selectPage(5, NativeOnboardingPage.Ready))
        assertTrue(session.openDisclosure(5))
        assertTrue(session.state.value.disclosureVisible)
        assertTrue(session.selectPage(5, NativeOnboardingPage.Setup))
        assertFalse(session.state.value.disclosureVisible)
    }

    @Test
    fun `disclosure cannot open before ready and cannot be dismissed into product`() {
        val session = NativeOnboardingSession()
        assertTrue(session.begin(6, input()))

        assertFalse(session.openDisclosure(6))
        assertNull(session.acceptDisclosure(6))
        assertEquals(NativeStartupGateStatus.Onboarding, session.state.value.status)
    }

    @Test
    fun `explicit disclosure acceptance is the only completion transition`() {
        val session = NativeOnboardingSession()
        assertTrue(session.begin(7, input()))
        assertTrue(session.selectPage(7, NativeOnboardingPage.Ready))
        assertTrue(session.openDisclosure(7))

        val completion = session.acceptDisclosure(7)

        assertEquals(true, completion?.writes?.get("privacy_disclosure_v2"))
        assertEquals(NativeStartupGateStatus.Ready, session.state.value.status)
        assertFalse(session.state.value.disclosureVisible)
        assertNull(session.acceptDisclosure(7))
    }

    @Test
    fun `identity location visibility and voice callbacks reject stale work`() {
        val session = NativeOnboardingSession()
        assertTrue(session.begin(8, input()))

        assertFalse(
            session.updateIdentity(7, NativeOnboardingIdentityStatus.Connected, "Alice"),
        )
        assertTrue(
            session.updateIdentity(8, NativeOnboardingIdentityStatus.Connected, "Alice"),
        )
        assertTrue(session.updateProfileVisibility(8, true))
        assertTrue(session.updateLocation(8, NativeOnboardingLocationStatus.Granted))
        assertTrue(
            session.updateVoice(8, NativeOnboardingVoiceStatus.Downloading, 0.5),
        )
        assertEquals("Alice", session.state.value.identityLabel)
        assertTrue(session.state.value.profilePublic)
        assertEquals(NativeOnboardingLocationStatus.Granted, session.state.value.locationStatus)
        assertEquals(0.5, session.state.value.voiceProgress, 0.0)
    }

    @Test
    fun `non-onboarding startup states reject interactive mutation`() {
        val session = NativeOnboardingSession()
        assertTrue(session.begin(9, input(disclosure = true)))

        assertFalse(session.selectPage(9, NativeOnboardingPage.Identity))
        assertFalse(session.updateProfileVisibility(9, true))
        assertFalse(session.updateLocation(9, NativeOnboardingLocationStatus.Granted))
        assertFalse(session.updateVoice(9, NativeOnboardingVoiceStatus.Ready))
        assertFalse(session.openDisclosure(9))
    }

    @Test
    fun `hide clears projected identity and rejects stale callbacks`() {
        val session = NativeOnboardingSession()
        assertTrue(
            session.begin(
                10,
                input(
                    identityStatus = NativeOnboardingIdentityStatus.Connected,
                    identityLabel = "Alice",
                ),
            ),
        )

        assertTrue(session.hide(10))
        assertEquals(NativeStartupGateStatus.Hidden, session.state.value.status)
        assertNull(session.state.value.identityLabel)
        assertFalse(session.updateProfileVisibility(10, true))
        assertFalse(session.hide(9))
    }

    private fun present(
        disclosure: Any? = false,
        storageAvailable: Boolean = true,
        migrationReadiness: NativeMigrationReadiness = NativeMigrationReadiness.Ready,
        identityStatus: NativeOnboardingIdentityStatus =
            NativeOnboardingIdentityStatus.Disconnected,
        identityLabel: String? = null,
        voiceStatus: NativeOnboardingVoiceStatus = NativeOnboardingVoiceStatus.Checking,
        voiceProgress: Double = 0.0,
    ) = NativeOnboardingPresenter.present(
        1,
        input(
            disclosure = disclosure,
            storageAvailable = storageAvailable,
            migrationReadiness = migrationReadiness,
            identityStatus = identityStatus,
            identityLabel = identityLabel,
            voiceStatus = voiceStatus,
            voiceProgress = voiceProgress,
        ),
    )

    private fun input(
        disclosure: Any? = false,
        storageAvailable: Boolean = true,
        migrationReadiness: NativeMigrationReadiness = NativeMigrationReadiness.Ready,
        identityStatus: NativeOnboardingIdentityStatus =
            NativeOnboardingIdentityStatus.Disconnected,
        identityLabel: String? = null,
        voiceStatus: NativeOnboardingVoiceStatus = NativeOnboardingVoiceStatus.Checking,
        voiceProgress: Double = 0.0,
    ) = NativeOnboardingInput(
        protectedStorageAvailable = storageAvailable,
        migrationReadiness = migrationReadiness,
        privacyDisclosureV2 = disclosure,
        identityStatus = identityStatus,
        identityLabel = identityLabel,
        voiceStatus = voiceStatus,
        voiceProgress = voiceProgress,
    )
}
