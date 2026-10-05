package app.roadstr.core.discovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceTagPolicyTest {
    @Test
    fun `control characters become spaces and the text is trimmed and cut`() {
        assertEquals("a b c", PlaceTagPolicy.clamp("  a\u0001b\nc  "))
        assertEquals(PlaceTagPolicy.MAX_VALUE_CHARS, PlaceTagPolicy.clamp("x".repeat(500))!!.length)
        assertNull(PlaceTagPolicy.clamp(" \n\t "))
        assertNull(PlaceTagPolicy.clamp(""))
    }

    @Test
    fun `characters that can disguise text on screen are removed`() {
        // U+202E (right-to-left override) turns "gro.elpmaxe" around on screen.
        assertEquals("example.org", PlaceTagPolicy.clamp("‮example.org‬"))
        assertEquals("ab", PlaceTagPolicy.clamp("a​b"))
        assertEquals("ab", PlaceTagPolicy.clamp("a⁦b⁩"))
        assertEquals("ab", PlaceTagPolicy.clamp("﻿a⁠b"))
        assertEquals("ab", PlaceTagPolicy.clamp("a\u007fb\u0085"))
    }

    @Test
    fun `the marks and joiners that scripts need stay`() {
        val persian = "می‌خواهم"
        assertEquals(persian, PlaceTagPolicy.clamp(persian))
        assertEquals("a‍b", PlaceTagPolicy.clamp("a‍b"))
        assertEquals("שלום‏ abc", PlaceTagPolicy.clamp("שלום‏ abc"))
    }

    @Test
    fun `only whitelisted keys with text values are kept`() {
        val kept = PlaceTagPolicy.filter(
            mapOf(
                "name" to "Bar", "amenity" to "cafe", "fixme" to "x", "addr:street" to "Via Roma",
                "website" to 5, 7 to "x", "note" to "private note", "opening_hours" to "‮Mo-Su 10:00-20:00",
            ),
        )
        assertEquals(setOf("name", "amenity", "addr:street", "opening_hours"), kept.keys)
        assertEquals("Mo-Su 10:00-20:00", kept["opening_hours"])
    }

    @Test
    fun `the number of tags is capped`() {
        val many = (1..200).associate { "name:l$it" to "v$it" }
        assertEquals(PlaceTagPolicy.MAX_TAGS, PlaceTagPolicy.filter(many).size)
        assertTrue(PlaceTagPolicy.keeps("name:de"))
        assertFalse(PlaceTagPolicy.keeps("source"))
    }
}
