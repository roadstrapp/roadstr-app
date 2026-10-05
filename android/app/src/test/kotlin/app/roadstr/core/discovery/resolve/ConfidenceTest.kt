package app.roadstr.core.discovery.resolve

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConfidenceTest {
    private fun of(vararg kinds: EvidenceKind) = Confidence.combine(kinds.map { Evidence(it) })

    @Test
    fun `evidence combines as one minus the product of the doubts`() {
        assertEquals(0.6, of(EvidenceKind.NAME_LOCALITY), 1e-9)
        assertEquals(1 - 0.15 * 0.4, of(EvidenceKind.WEBSITE_HOST, EvidenceKind.NAME_LOCALITY), 1e-9)
        assertEquals(0.0, Confidence.combine(emptyList()), 0.0)
    }

    @Test
    fun `without an osm id the confidence never reaches certainty`() {
        val many = of(EvidenceKind.WEBSITE_HOST, EvidenceKind.PHONE, EvidenceKind.ADDRESS_EXACT, EvidenceKind.NAME_LOCALITY)
        assertEquals(0.99, many, 0.0)
        assertEquals(1.0, of(EvidenceKind.OSM_ID, EvidenceKind.NAME_LOCALITY), 0.0)
    }

    @Test
    fun `the thresholds split linked, candidate and web only`() {
        assertEquals(MatchClass.LINKED, Confidence.classify(0.8))
        assertEquals(MatchClass.CANDIDATE, Confidence.classify(0.79))
        assertEquals(MatchClass.CANDIDATE, Confidence.classify(0.5))
        assertEquals(MatchClass.WEB_ONLY, Confidence.classify(0.49))
    }

    @Test
    fun `a name and a category alone are only a candidate`() {
        assertEquals(MatchClass.CANDIDATE, Confidence.classify(of(EvidenceKind.NAME_LOCALITY, EvidenceKind.CATEGORY)))
    }

    @Test
    fun `being close adds a little, being far adds nothing`() {
        assertEquals(0.3, Confidence.proximity(10.0)!!.weight, 0.0)
        assertEquals(0.2, Confidence.proximity(80.0)!!.weight, 0.0)
        assertEquals(0.1, Confidence.proximity(250.0)!!.weight, 0.0)
        assertNull(Confidence.proximity(900.0))
    }
}
