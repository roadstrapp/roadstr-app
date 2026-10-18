package app.roadstr.core.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechModelCatalogTest {
    private val spec = SpeechModelCatalog.default

    @Test
    fun theDefaultModelIsPinnedByCommitSizeAndDigest() {
        assertEquals(485_425_504L, spec.byteSize)
        assertEquals("b68557be1e3c40207fd7c4bd9d63f1d3316b963f15325bfb0cc16a8bb0ffd181", spec.sha256)
        // A branch name such as "main" would let the bytes change under a pinned digest.
        assertTrue(spec.downloadUrl.contains("/resolve/85ac09ea12fc4b1112fa76810059364bc6adc9de/"))
        assertTrue(spec.downloadUrl.startsWith("https://huggingface.co/"))
        assertTrue(spec.downloadUrl.contains(spec.fileName))
    }

    @Test
    fun theLicenceObligationsTravelWithTheModel() {
        assertEquals("CC-BY-4.0", spec.licenseName)
        assertTrue(spec.attribution.contains("NVIDIA"))
        assertTrue(spec.attribution.contains("CC-BY-4.0"))
    }

    @Test
    fun languageHintsUseThePrimarySubtagAndOnlyForKnownLanguages() {
        assertEquals("it", spec.languageHint("it-IT"))
        assertEquals("it", spec.languageHint("it"))
        assertEquals("de", spec.languageHint("de_AT"))
        assertEquals("pt", spec.languageHint("PT-br"))
        assertNull(spec.languageHint("ja-JP"))
        assertTrue(spec.supportsLanguage("fr-CA"))
        assertFalse(spec.supportsLanguage("zh-Hans"))
    }

    @Test
    fun coversTheTwentyFiveEuropeanLanguagesAndNothingElse() {
        assertEquals(25, spec.languages.size)
        assertTrue(spec.languages.all { it.length == 2 && it == it.lowercase() })
    }

    @Test
    fun aSpecCannotBeBuiltThatWeakensTheIntegrityChecks() {
        assertThrows(IllegalArgumentException::class.java) { spec.copy(downloadUrl = "http://example.org/m.gguf") }
        assertThrows(IllegalArgumentException::class.java) { spec.copy(sha256 = spec.sha256.uppercase()) }
        assertThrows(IllegalArgumentException::class.java) { spec.copy(sha256 = "abc") }
        assertThrows(IllegalArgumentException::class.java) { spec.copy(fileName = "../m.gguf") }
        assertThrows(IllegalArgumentException::class.java) { spec.copy(fileName = "m.bin") }
        assertThrows(IllegalArgumentException::class.java) { spec.copy(byteSize = 0) }
    }
}
