package app.roadstr.feature.report

import app.roadstr.core.protocol.nostr.RoadCategoryWire
import app.roadstr.feature.map.NativeMapPointOverlayKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeRoadEventPresentationTest {
    @Test
    fun `all fourteen categories reuse the existing map marker vocabulary`() {
        val kinds = RoadCategoryWire.entries.map(NativeRoadEventPresenter::markerKind)

        assertEquals(14, kinds.size)
        assertEquals(14, kinds.toSet().size)
        assertTrue(kinds.all(NativeMapPointOverlayKind::opensRoadEventDetail))
        assertEquals(NativeMapPointOverlayKind.RoadPolice, kinds.first())
        assertEquals(NativeMapPointOverlayKind.RoadOther, kinds.last())
    }

    @Test
    fun `detail preserves Flutter age thresholds and received comment bound`() {
        assertEquals(
            NativeRoadEventAge(59, NativeRoadEventAgeUnit.Minutes),
            NativeRoadEventPresenter.age(1_000, 1_000 + 59 * 60),
        )
        assertEquals(
            NativeRoadEventAge(1, NativeRoadEventAgeUnit.Hours),
            NativeRoadEventPresenter.age(1_000, 1_000 + 60 * 60),
        )
        assertEquals(
            NativeRoadEventAge(1, NativeRoadEventAgeUnit.Days),
            NativeRoadEventPresenter.age(1_000, 1_000 + 1_440 * 60),
        )

        val detail = NativeRoadEventPresenter.detail(
            input(comment = "x".repeat(501)),
            nowSeconds = NOW,
            imperial = false,
        )
        assertEquals(501, detail.comment.length)
        assertTrue(detail.comment.endsWith("…"))
    }

    @Test
    fun `detail rejects hostile identity coordinate time and counter values`() {
        assertFails { project(input(id = "a")) }
        assertFails { project(input(pubkey = "A".repeat(64))) }
        assertFails { project(input(latitude = Double.NaN)) }
        assertFails { project(input(longitude = 181.0)) }
        assertFails { project(input(createdAtSeconds = NOW + 301)) }
        assertFails { project(input(confirmations = -1)) }
        assertFails { project(input(speedLimitKmh = 301)) }
        assertFails {
            project(
                input(
                    editRequests = List(NativeRoadEventPresenter.MAX_EDIT_REQUESTS + 1) {
                        request(it)
                    },
                ),
            )
        }
    }

    @Test
    fun `client and relay expiration each reject the detail`() {
        val ttl = RoadCategoryWire.TRAFFIC_JAM.ttlSeconds.toLong()
        assertFails {
            project(
                input(
                    category = RoadCategoryWire.TRAFFIC_JAM,
                    createdAtSeconds = NOW - ttl,
                ),
            )
        }
        assertFails { project(input(expiresAtSeconds = NOW)) }
        assertTrue(
            project(input(expiresAtSeconds = NOW + 1)).category == RoadCategoryWire.SPEED_CAMERA,
        )
    }

    @Test
    fun `imperial detail projects speed owner identity and public reporter`() {
        val detail = NativeRoadEventPresenter.detail(
            input(
                speedLimitKmh = 100,
                viewerPubkey = PUBKEY,
                reporterPublic = true,
                reporterLabel = "  Alice\u0000 Roadstr  ",
                zapSats = 21,
            ),
            NOW,
            imperial = true,
        )

        assertEquals(62, detail.speedLimit)
        assertEquals("mph", detail.speedUnit)
        assertTrue(detail.owner)
        assertTrue(detail.loggedIn)
        assertEquals("Alice  Roadstr", detail.reporterLabel)
        assertTrue(detail.reporterNpub.startsWith("npub1"))
        assertEquals(21, detail.zapSats)
    }

    @Test
    fun `edit suggestions are owner-only filtered and bounded`() {
        val valid = request(1)
        val wrongEvent = request(2).copy(eventId = "c".repeat(64))
        val lowSpeed = request(3).copy(speedLimitKmh = 4)
        val owner = project(
            input(
                viewerPubkey = PUBKEY,
                editRequests = listOf(valid, wrongEvent, lowSpeed),
            ),
        )
        val visitor = project(
            input(
                viewerPubkey = "d".repeat(64),
                editRequests = listOf(valid),
            ),
        )

        assertEquals(1, owner.editRequests.size)
        assertEquals("${REQUESTER.take(8)}…", owner.editRequests.single().requesterLabel)
        assertTrue(visitor.editRequests.isEmpty())
        assertFalse(visitor.owner)
    }

    @Test
    fun `metric submission preserves trim category TTL and optional speed`() {
        val draft = NativeRoadEventPresenter.newDraft(45.46, 9.19, imperial = false).copy(
            category = RoadCategoryWire.SPEED_CAMERA,
            comment = "  camera ahead  ",
            speedInput = "90",
        )

        val submission = NativeRoadEventPresenter.submission(4, draft, NOW)!!
        assertEquals("camera ahead", submission.comment)
        assertEquals(90, submission.speedLimitKmh)
        assertEquals(
            NOW + RoadCategoryWire.SPEED_CAMERA.ttlSeconds,
            submission.expiresAtSeconds,
        )
    }

    @Test
    fun `imperial speed mirrors Flutter conversion and non-camera ignores it`() {
        val base = NativeRoadEventPresenter.newDraft(45.46, 9.19, imperial = true)
        val camera = NativeRoadEventPresenter.submission(
            5,
            base.copy(category = RoadCategoryWire.SPEED_CAMERA, speedInput = "60"),
            NOW,
        )!!
        val hazard = NativeRoadEventPresenter.submission(
            6,
            base.copy(category = RoadCategoryWire.HAZARD, speedInput = "60"),
            NOW,
        )!!
        val invalidOptional = NativeRoadEventPresenter.submission(
            7,
            base.copy(category = RoadCategoryWire.SPEED_CAMERA, speedInput = "bad"),
            NOW,
        )!!

        assertEquals(97, camera.speedLimitKmh)
        assertNull(hazard.speedLimitKmh)
        assertNull(invalidOptional.speedLimitKmh)
    }

    @Test
    fun `draft input bounds match Flutter text fields`() {
        assertEquals(
            "x".repeat(NativeRoadEventPresenter.MAX_REPORT_COMMENT),
            NativeRoadEventPresenter.cleanDraftComment("x".repeat(250)),
        )
        assertEquals("123", NativeRoadEventPresenter.cleanSpeedInput("12345"))
        assertFails { NativeRoadEventPresenter.newDraft(91.0, 0.0, false) }
    }

    @Test
    fun `session fences privacy acceptance and stale revisions`() {
        val session = NativeRoadEventSession()
        assertTrue(session.showComposer(10, 45.0, 9.0, privacyAcknowledged = false))
        assertEquals(NativeRoadEventSurface.PrivacyNotice, session.state.value.surface)
        assertFalse(session.acceptPrivacy(9))
        assertTrue(session.acceptPrivacy(10))
        assertEquals(NativeRoadEventSurface.Composer, session.state.value.surface)
        assertFalse(session.showComposer(10, 0.0, 0.0, privacyAcknowledged = true))
    }

    @Test
    fun `composer mutations are revision safe bounded and clear stale speed`() {
        val session = NativeRoadEventSession()
        assertTrue(session.showComposer(11, 45.0, 9.0, privacyAcknowledged = true))
        assertFalse(session.updateComment(10, "old"))
        assertTrue(session.selectCategory(11, RoadCategoryWire.SPEED_CAMERA))
        assertTrue(session.updateSpeed(11, "12345"))
        assertTrue(session.updateComment(11, "x".repeat(250)))
        assertEquals("123", session.state.value.draft?.speedInput)
        assertEquals(200, session.state.value.draft?.comment?.length)
        assertTrue(session.selectCategory(11, RoadCategoryWire.HAZARD))
        assertEquals("", session.state.value.draft?.speedInput)
        assertFalse(session.updateSpeed(11, "90"))
    }

    @Test
    fun `submission is single-flight and failure permits retry`() {
        val session = NativeRoadEventSession()
        session.showComposer(12, 45.0, 9.0, privacyAcknowledged = true)
        assertNull(session.beginSubmission(12, NOW))
        session.selectCategory(12, RoadCategoryWire.ACCIDENT)
        val first = session.beginSubmission(12, NOW)
        assertEquals(RoadCategoryWire.ACCIDENT, first?.category)
        assertTrue(session.state.value.draft?.submitting == true)
        assertNull(session.beginSubmission(12, NOW))
        assertFalse(session.updateUnits(imperial = true))
        assertTrue(session.submissionFailed(12))
        assertTrue(session.state.value.draft?.submitting == false)
        assertTrue(session.beginSubmission(12, NOW) != null)
        assertTrue(session.submissionAccepted(12))
        assertEquals(NativeRoadEventSurface.Hidden, session.state.value.surface)
    }

    @Test
    fun `detail and hide reject late callbacks`() {
        val session = NativeRoadEventSession()
        assertTrue(session.showDetail(13, input(), NOW))
        assertEquals(NativeRoadEventSurface.Detail, session.state.value.surface)
        assertFalse(session.hide(12))
        assertTrue(session.hide(13))
        assertFalse(session.hide(13))
        assertFalse(session.updateComment(13, "late"))
    }

    @Test
    fun `unit changes reproject an open detail without losing raw speed`() {
        val session = NativeRoadEventSession()
        session.showDetail(
            14,
            input(viewerPubkey = PUBKEY, editRequests = listOf(request(1))),
            NOW,
        )

        assertTrue(session.updateUnits(imperial = true))
        val detail = session.state.value.detail!!
        assertEquals(56, detail.speedLimit)
        assertEquals(43, detail.editRequests.single().speedLimit)
        assertEquals("mph", detail.speedUnit)
        assertEquals(90, detail.speedLimitKmh)
    }

    private fun project(value: NativeRoadEventInput) =
        NativeRoadEventPresenter.detail(value, NOW, imperial = false)

    private fun input(
        id: String = EVENT_ID,
        pubkey: String = PUBKEY,
        category: RoadCategoryWire = RoadCategoryWire.SPEED_CAMERA,
        latitude: Double = 45.46,
        longitude: Double = 9.19,
        comment: String = "Visible camera",
        createdAtSeconds: Long = NOW - 120,
        expiresAtSeconds: Long? = NOW + 600,
        speedLimitKmh: Int? = 90,
        confirmations: Int = 2,
        denials: Int = 1,
        zapSats: Long = 0,
        viewerPubkey: String? = null,
        reporterPublic: Boolean = false,
        reporterLabel: String? = null,
        editRequests: List<NativeRoadEventEditRequestInput> = emptyList(),
    ) = NativeRoadEventInput(
        id,
        pubkey,
        category,
        latitude,
        longitude,
        comment,
        createdAtSeconds,
        expiresAtSeconds,
        speedLimitKmh,
        confirmations,
        denials,
        zapSats,
        viewerPubkey,
        reporterPublic,
        reporterLabel,
        editRequests,
    )

    private fun request(index: Int) = NativeRoadEventEditRequestInput(
        id = index.toString(16).padStart(64, '0'),
        eventId = EVENT_ID,
        requesterPubkey = REQUESTER,
        speedLimitKmh = 70,
        createdAtSeconds = NOW - 60,
    )

    private fun assertFails(block: () -> Unit) {
        assertTrue(runCatching(block).isFailure)
    }

    private companion object {
        const val NOW = 2_000_000_000L
        val EVENT_ID = "a".repeat(64)
        val PUBKEY = "b".repeat(64)
        val REQUESTER = "c".repeat(64)
    }
}
