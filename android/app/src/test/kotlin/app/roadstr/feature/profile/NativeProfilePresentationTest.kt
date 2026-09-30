package app.roadstr.feature.profile

import app.roadstr.core.protocol.nostr.RoadCategoryWire
import app.roadstr.feature.report.NativeRoadEventAgeUnit
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeProfilePresentationTest {
    @Test
    fun `identity flavors accept only the two persisted wire values`() {
        assertEquals(NativeProfileIdentityFlavor.Amber, NativeProfileIdentityFlavor.fromWire(" AMBER "))
        assertEquals(NativeProfileIdentityFlavor.Nsec, NativeProfileIdentityFlavor.fromWire("nsec"))
        assertNull(NativeProfileIdentityFlavor.fromWire("extension"))
        assertNull(NativeProfileIdentityFlavor.fromWire(null))
    }

    @Test
    fun `own profile derives npub and prefers bounded display name`() {
        val snapshot = present(
            input = ownInput(
                displayName = "  Alice\u0000Driver  ",
                name = "fallback",
                pictureUrl = "https://images.example/alice.png",
            ),
        )

        assertTrue(requireNotNull(snapshot.npub).startsWith("npub1"))
        assertEquals("Alice Driver", snapshot.displayName)
        assertEquals("https://images.example/alice.png", snapshot.pictureUrl)
        assertTrue(snapshot.showPublicProfile)
        assertEquals(NativeProfileIdentityFlavor.Amber, snapshot.flavor)
    }

    @Test
    fun `avatar admission is HTTPS only and strips no unsafe authority`() {
        assertNull(present(input = ownInput(pictureUrl = "http://images.example/a.png")).pictureUrl)
        assertNull(present(input = ownInput(pictureUrl = "https://user:pass@images.example/a")).pictureUrl)
        assertNull(present(input = ownInput(pictureUrl = "not a URI")).pictureUrl)
    }

    @Test
    fun `hidden remote profile suppresses identity activity reputation and balance`() {
        val snapshot = present(
            input = NativeProfileInput(
                pubkeyHex = PUBKEY,
                ownProfile = false,
                profilePublic = false,
                displayName = "Private Alice",
                pictureUrl = "https://images.example/private.png",
                reports = listOf(report(1, confirmations = 1)),
                balanceMsat = 90_000,
            ),
        )

        assertEquals(NativeProfileStatus.Ready, snapshot.status)
        assertFalse(snapshot.showPublicProfile)
        assertNull(snapshot.npub)
        assertNull(snapshot.displayName)
        assertNull(snapshot.pictureUrl)
        assertNull(snapshot.reputationPercent)
        assertNull(snapshot.balanceSats)
        assertTrue(snapshot.reports.isEmpty())
    }

    @Test
    fun `public remote profile never invents a local login flavor`() {
        val snapshot = present(
            input = NativeProfileInput(
                pubkeyHex = PUBKEY,
                ownProfile = false,
                profilePublic = true,
                displayName = "Alice",
            ),
        )

        assertEquals("Alice", snapshot.displayName)
        assertNull(snapshot.flavor)
        assertTrue(snapshot.showPublicProfile)
    }

    @Test
    fun `reports are sorted newest first and capped at one hundred`() {
        val reports = List(105) { index -> report(index + 1, createdAt = index.toLong()) }

        val snapshot = present(input = ownInput(reports = reports), nowSeconds = 1_000)

        assertEquals(NativeProfilePresenter.MAX_REPORTS, snapshot.reports.size)
        assertEquals(104L, snapshot.reports.first().createdAtSeconds)
        assertEquals(5L, snapshot.reports.last().createdAtSeconds)
    }

    @Test
    fun `report projection bounds text computes age reliability and millisatoshi floor`() {
        val snapshot = present(
            input = ownInput(
                reports = listOf(
                    report(
                        7,
                        createdAt = 3_600,
                        confirmations = 2,
                        denials = 1,
                        zapMsat = 4_999,
                        address = "  Main\u0000Street  ",
                        comment = "  Watch out  ",
                    ),
                ),
            ),
            nowSeconds = 7_200,
        )
        val row = snapshot.reports.single()

        assertEquals(NativeRoadEventAgeUnit.Hours, row.age.unit)
        assertEquals(1L, row.age.value)
        assertEquals("Main Street", row.address)
        assertEquals("Watch out", row.comment)
        assertEquals(67, row.reliabilityPercent)
        assertEquals(4L, row.zapSats)
    }

    @Test
    fun `aggregate reputation matches Flutter score thresholds before display rounding`() {
        assertEquals(NativeProfileReputationLevel.High, NativeProfilePresenter.reputationLevel(0.67))
        assertEquals(NativeProfileReputationLevel.Medium, NativeProfilePresenter.reputationLevel(0.665))
        assertEquals(NativeProfileReputationLevel.Medium, NativeProfilePresenter.reputationLevel(0.34))
        assertEquals(NativeProfileReputationLevel.Low, NativeProfilePresenter.reputationLevel(0.339))

        val snapshot = present(
            input = ownInput(reports = listOf(report(1, confirmations = 2, denials = 1))),
        )
        assertEquals(67, snapshot.reputationPercent)
        assertEquals(NativeProfileReputationLevel.Medium, snapshot.reputationLevel)
    }

    @Test
    fun `zero votes omit reputation and balance floors millisatoshi`() {
        val snapshot = present(
            input = ownInput(reports = listOf(report(1)), balanceMsat = 12_999),
        )

        assertNull(snapshot.reputationPercent)
        assertNull(snapshot.reputationLevel)
        assertEquals(12L, snapshot.balanceSats)
    }

    @Test
    fun `presenter rejects malformed identity and ownership combinations`() {
        assertThrows(IllegalArgumentException::class.java) {
            present(input = ownInput(pubkeyHex = PUBKEY.uppercase(Locale.ROOT)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            present(input = ownInput(flavor = null))
        }
        assertThrows(IllegalArgumentException::class.java) {
            present(
                input = NativeProfileInput(
                    pubkeyHex = PUBKEY,
                    ownProfile = false,
                    profilePublic = true,
                    flavor = NativeProfileIdentityFlavor.Nsec,
                ),
            )
        }
    }

    @Test
    fun `presenter rejects malformed report counters time and zap totals`() {
        assertThrows(IllegalArgumentException::class.java) {
            present(input = ownInput(reports = listOf(report(1, confirmations = -1))))
        }
        assertThrows(IllegalArgumentException::class.java) {
            present(input = ownInput(reports = listOf(report(1, createdAt = 10_301))), nowSeconds = 10_000)
        }
        assertThrows(IllegalArgumentException::class.java) {
            present(input = ownInput(reports = listOf(report(1, zapMsat = -1))))
        }
        assertThrows(IllegalArgumentException::class.java) {
            present(input = ownInput(reports = listOf(report(1), report(1))))
        }
    }

    @Test
    fun `session opens only newer revisions and models Amber waiting while logged out`() {
        val session = NativeProfileSession()

        assertTrue(session.begin(2, ownProfile = true))
        assertFalse(session.begin(2, ownProfile = true))
        assertFalse(session.begin(1, ownProfile = true))
        assertTrue(session.showLoggedOut(2))
        assertTrue(session.setWaitingAmber(2, true))
        assertTrue(session.state.value.waitingAmber)
        assertFalse(session.setWaitingAmber(1, false))
    }

    @Test
    fun `session fences profile callbacks and ownership changes`() {
        val session = NativeProfileSession()
        assertTrue(session.begin(5, ownProfile = false))
        assertFalse(session.showProfile(4, remoteInput(), NOW))
        assertThrows(IllegalArgumentException::class.java) {
            session.showProfile(5, ownInput(), NOW)
        }
        assertTrue(session.showProfile(5, remoteInput(), NOW))
        assertEquals(NativeProfileStatus.Ready, session.state.value.status)
    }

    @Test
    fun `visibility is mutable only for the active own profile`() {
        val session = NativeProfileSession()
        assertTrue(session.begin(8, ownProfile = true))
        assertTrue(session.showProfile(8, ownInput(profilePublic = false), NOW))
        assertTrue(session.updateVisibility(8, true))
        assertTrue(session.state.value.profilePublic)
        assertFalse(session.updateVisibility(8, true))
        assertFalse(session.updateVisibility(7, false))

        val remote = NativeProfileSession()
        assertTrue(remote.begin(1, ownProfile = false))
        assertTrue(remote.showProfile(1, remoteInput(), NOW))
        assertFalse(remote.updateVisibility(1, false))
    }

    @Test
    fun `hide clears projected user data and rejects stale work`() {
        val session = NativeProfileSession()
        assertTrue(session.begin(9, ownProfile = true))
        assertTrue(session.showProfile(9, ownInput(displayName = "Alice"), NOW))

        assertTrue(session.hide(9))
        assertEquals(NativeProfileStatus.Hidden, session.state.value.status)
        assertNull(session.state.value.npub)
        assertNull(session.state.value.displayName)
        assertTrue(session.state.value.reports.isEmpty())
        assertFalse(session.showProfile(9, ownInput(), NOW))
        assertFalse(session.hide(8))
    }

    private fun present(
        input: NativeProfileInput,
        nowSeconds: Long = NOW,
    ) = NativeProfilePresenter.present(1, input, nowSeconds)

    private fun ownInput(
        pubkeyHex: String = PUBKEY,
        profilePublic: Boolean = false,
        flavor: NativeProfileIdentityFlavor? = NativeProfileIdentityFlavor.Amber,
        displayName: String? = null,
        name: String? = null,
        pictureUrl: String? = null,
        reports: List<NativeProfileReportInput> = emptyList(),
        balanceMsat: Long? = null,
    ) = NativeProfileInput(
        pubkeyHex = pubkeyHex,
        ownProfile = true,
        profilePublic = profilePublic,
        flavor = flavor,
        displayName = displayName,
        name = name,
        pictureUrl = pictureUrl,
        reports = reports,
        balanceMsat = balanceMsat,
    )

    private fun remoteInput() = NativeProfileInput(
        pubkeyHex = PUBKEY,
        ownProfile = false,
        profilePublic = true,
    )

    private fun report(
        index: Int,
        createdAt: Long = 1_000,
        confirmations: Int = 0,
        denials: Int = 0,
        zapMsat: Long = 0,
        address: String? = null,
        comment: String = "",
    ) = NativeProfileReportInput(
        id = index.toString(16).padStart(64, '0'),
        category = RoadCategoryWire.HAZARD,
        createdAtSeconds = createdAt,
        address = address,
        comment = comment,
        confirmations = confirmations,
        denials = denials,
        zapMsat = zapMsat,
    )

    companion object {
        private const val PUBKEY = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        private const val NOW = 10_000L
    }
}
