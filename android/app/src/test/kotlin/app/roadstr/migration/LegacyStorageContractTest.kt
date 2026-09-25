package app.roadstr.migration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyStorageContractTest {
    @Test
    fun `contract contains every protected key from the audited Flutter app`() {
        val expected = setOf(
            "hive_settings_key",
            "nostr_priv_hex",
            "nostr_pub_hex",
            "nostr_flavor",
            "nostr_picture",
            "nostr_name",
            "routing_api_key",
            "nwc_uri",
            "favorites_sync_passphrase",
        )

        assertEquals(expected, LegacyStorageContract.secureKeys)
        assertTrue("autoCenterOnLaunch" in LegacyStorageContract.hiveKeys)
    }

    @Test
    fun `dynamic identity keys are recognized without enumerating pubkeys`() {
        assertTrue(LegacyStorageContract.isDynamicKey("activity_inbox_" + "a".repeat(64)))
        assertTrue(LegacyStorageContract.isDynamicKey("activity_zap_cursor_" + "b".repeat(64)))
        assertTrue(
            LegacyStorageContract.isDynamicKey(
                "activity_confirmation_cursor_" + "c".repeat(64),
            ),
        )
        assertFalse(LegacyStorageContract.isDynamicKey("activity_inbox_"))
        assertFalse(LegacyStorageContract.isDynamicKey("activity_inbox_not-a-pubkey"))
    }
}
