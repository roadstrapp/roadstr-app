package app.roadstr.core.protocol.nostr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CustomRelayPolicyTest {
    @Test
    fun `a public relay over wss is accepted, a default one included`() {
        assertEquals("wss://relay.damus.io", CustomRelayPolicy.normalise("wss://relay.damus.io"))
        assertEquals("wss://relay.damus.io", CustomRelayPolicy.normalise("  WSS://Relay.Damus.IO/  "))
        assertEquals("wss://relay.example.com:8443/nostr", CustomRelayPolicy.normalise("wss://relay.example.com:8443/nostr"))
        assertEquals("wss://nos.lol", CustomRelayPolicy.normalise("wss://nos.lol/"))
    }

    @Test
    fun `a relay on the home network may use plain ws`() {
        assertEquals("ws://192.168.1.20:4848", CustomRelayPolicy.normalise("ws://192.168.1.20:4848"))
        assertEquals("ws://10.0.0.5", CustomRelayPolicy.normalise("ws://10.0.0.5"))
        assertEquals("ws://172.16.5.4:7777/relay", CustomRelayPolicy.normalise("ws://172.16.5.4:7777/relay"))
        assertEquals("ws://172.31.255.254:1", CustomRelayPolicy.normalise("ws://172.31.255.254:1"))
        assertEquals("ws://127.0.0.1:7000", CustomRelayPolicy.normalise("ws://127.0.0.1:7000"))
        assertEquals("ws://169.254.10.10:80", CustomRelayPolicy.normalise("ws://169.254.10.10:80"))
        assertEquals("ws://localhost:7000", CustomRelayPolicy.normalise("ws://localhost:7000"))
        assertEquals("ws://relay.local:4848", CustomRelayPolicy.normalise("ws://relay.local:4848"))
        assertEquals("ws://umbrel.lan", CustomRelayPolicy.normalise("ws://umbrel.lan"))
        assertEquals("ws://nas.home.arpa:9", CustomRelayPolicy.normalise("ws://nas.home.arpa:9"))
        assertEquals("ws://[fd00::1]:8080", CustomRelayPolicy.normalise("ws://[fd00::1]:8080"))
        assertEquals("ws://[fe80::1]", CustomRelayPolicy.normalise("ws://[fe80::1]"))
        assertEquals("ws://[::1]:4848", CustomRelayPolicy.normalise("ws://[::1]:4848"))
    }

    @Test
    fun `plain ws to anything that is not local is refused`() {
        assertNull(CustomRelayPolicy.normalise("ws://relay.damus.io"))
        assertNull(CustomRelayPolicy.normalise("ws://8.8.8.8:80"))
        assertNull(CustomRelayPolicy.normalise("ws://172.32.0.1"))
        assertNull(CustomRelayPolicy.normalise("ws://172.15.0.1"))
        assertNull(CustomRelayPolicy.normalise("ws://192.169.1.1"))
        assertNull(CustomRelayPolicy.normalise("ws://100.64.0.1"))
        assertNull(CustomRelayPolicy.normalise("ws://0.0.0.0:80"))
        assertNull(CustomRelayPolicy.normalise("ws://224.0.0.1"))
        assertNull(CustomRelayPolicy.normalise("ws://[2001:db8::1]"))
        assertNull(CustomRelayPolicy.normalise("ws://local"))
        assertNull(CustomRelayPolicy.normalise("ws://.local"))
        assertNull(CustomRelayPolicy.normalise("ws://evil.com.local.example.org"))
    }

    @Test
    fun `malformed or sneaky addresses are refused`() {
        assertNull(CustomRelayPolicy.normalise(""))
        assertNull(CustomRelayPolicy.normalise("   "))
        assertNull(CustomRelayPolicy.normalise("https://relay.example.com"))
        assertNull(CustomRelayPolicy.normalise("http://192.168.1.2"))
        assertNull(CustomRelayPolicy.normalise("relay.example.com"))
        assertNull(CustomRelayPolicy.normalise("wss://nodots"))
        assertNull(CustomRelayPolicy.normalise("wss://user:pass@relay.example.com"))
        assertNull(CustomRelayPolicy.normalise("ws://192.168.1.1@evil.example.com"))
        assertNull(CustomRelayPolicy.normalise("wss://relay.example.com?token=1"))
        assertNull(CustomRelayPolicy.normalise("wss://relay.example.com/#frag"))
        assertNull(CustomRelayPolicy.normalise("ws://192.168.1.2:99999"))
        assertNull(CustomRelayPolicy.normalise("ws://999.168.1.2"))
        assertNull(CustomRelayPolicy.normalise("wss://" + "a".repeat(250) + ".com"))
    }
}
