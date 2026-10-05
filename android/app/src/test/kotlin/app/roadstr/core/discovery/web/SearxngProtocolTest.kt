package app.roadstr.core.discovery.web

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearxngProtocolTest {
    private fun accepted(text: String, own: Boolean = false): SearxngEndpoint =
        (SearxngEndpointPolicy.check(text, own) as EndpointCheck.Accepted).endpoint

    private fun rejection(text: String, own: Boolean = false): EndpointRejection =
        (SearxngEndpointPolicy.check(text, own) as EndpointCheck.Rejected).reason

    // Endpoint policy

    @Test
    fun `an https instance is accepted and a pasted search url is trimmed to its base`() {
        assertEquals("https://search.example", accepted("https://search.example").base.toString())
        assertEquals("https://search.example", accepted("https://search.example/").base.toString())
        assertEquals("https://search.example", accepted("  https://search.example/search  ").base.toString())
        assertEquals("https://search.example:8443/searx", accepted("https://search.example:8443/searx/").base.toString())
        assertFalse(accepted("https://search.example").cleartext)
    }

    @Test
    fun `cleartext is allowed to the device itself`() {
        for (host in listOf("http://localhost:8888", "http://127.0.0.1:8080", "http://10.0.2.2:8888", "http://[::1]:8888")) {
            val endpoint = accepted(host)
            assertTrue(host, endpoint.cleartext)
            assertTrue(host, endpoint.loopback)
        }
    }

    @Test
    fun `cleartext on a local network needs the user to say it is their own`() {
        for (host in listOf("http://192.168.1.20:8888", "http://10.1.2.3", "http://172.16.0.9:80", "http://100.64.0.5:8080", "http://searx.local", "http://nas:8888", "http://box.home.arpa")) {
            assertEquals(host, EndpointRejection.LOCAL_HTTP_NOT_CONFIRMED, rejection(host))
            val endpoint = accepted(host, own = true)
            assertTrue(host, endpoint.cleartext)
            assertFalse(host, endpoint.loopback)
        }
    }

    @Test
    fun `cleartext on the public internet is never allowed`() {
        for (host in listOf("http://search.example", "http://8.8.8.8", "http://172.15.0.1", "http://192.169.0.1", "http://172.32.0.1")) {
            assertEquals(host, EndpointRejection.NOT_HTTPS, rejection(host, own = true))
        }
    }

    @Test
    fun `credentials, queries and odd schemes are refused`() {
        assertEquals(EndpointRejection.CREDENTIALS, rejection("https://user:pass@search.example"))
        assertEquals(EndpointRejection.QUERY_OR_FRAGMENT, rejection("https://search.example/?q=x"))
        assertEquals(EndpointRejection.QUERY_OR_FRAGMENT, rejection("https://search.example/#a"))
        for (bad in listOf("ftp://search.example", "javascript:alert(1)", "file:///etc/passwd", "search.example", "", "   ", "https://", "https://a b.example")) {
            assertEquals(bad, EndpointRejection.INVALID, rejection(bad))
        }
        assertEquals(EndpointRejection.TOO_LONG, rejection("https://" + "a".repeat(250) + ".example"))
    }

    @Test
    fun `an endpoint never prints its address`() {
        assertEquals("SearxngEndpoint(cleartext=false)", accepted("https://secret.example").toString())
    }

    // Requests

    private val endpoint = accepted("https://s.example")

    @Test
    fun `a search carries only text, language and safe search`() {
        val request = SearxngRequests.search(endpoint, "vegan pizza Verona", "it", SafeSearch.MODERATE)!!
        assertEquals(
            "https://s.example/search?q=vegan%20pizza%20Verona&format=json&categories=general" +
                "&language=it&safesearch=1&pageno=1",
            request.uri,
        )
        assertNull(request.body)
        for (leak in listOf("lat", "lon", "geo", "coord")) {
            assertFalse(leak, request.uri.contains("$leak="))
        }
    }

    @Test
    fun `an allowlist is sent as the engines parameter`() {
        val request = SearxngRequests.search(
            endpoint, "x", "en", SafeSearch.STRICT, listOf("duckduckgo", "brave", "bad<name", "wikipedia"),
        )!!
        assertTrue(request.uri.contains("&engines=duckduckgo,brave,wikipedia&"))
        assertFalse(request.uri.contains("categories="))
        assertTrue(request.uri.contains("safesearch=2"))
    }

    @Test
    fun `the headers pass a bot limiter without lying about the client`() {
        val headers = SearxngRequests.search(endpoint, "x", "de", SafeSearch.OFF)!!.headers
        assertTrue(headers.getValue("Accept").contains("text/html"))
        assertEquals("de", headers.getValue("Accept-Language"))
        val agent = headers.getValue("User-Agent")
        assertTrue(agent.startsWith("Roadstr/"))
        val bots = Regex("(unknown|curl|wget|Scrapy|python-requests|Go-http-client|Java|okhttp|HttpClient|Python)")
        assertFalse(bots.containsMatchIn(agent.take(12)))
        assertFalse(headers.keys.any { it.equals("Cookie", true) || it.equals("Referer", true) })
        assertFalse(headers.keys.any { it.equals("Connection", true) })
    }

    @Test
    fun `odd input is cleaned or refused`() {
        assertNull(SearxngRequests.search(endpoint, "   ", "en", SafeSearch.OFF))
        assertNull(SearxngRequests.search(endpoint, "\n\t", "en", SafeSearch.OFF))
        val long = SearxngRequests.search(endpoint, "a".repeat(500), "en", SafeSearch.OFF)!!
        assertTrue(long.uri.length < 400)
        assertTrue(SearxngRequests.search(endpoint, "x", "../etc", SafeSearch.OFF)!!.uri.contains("language=en"))
        assertTrue(SearxngRequests.search(endpoint, "a\nb", "en", SafeSearch.OFF)!!.uri.contains("q=a%20b"))
    }

    @Test
    fun `the config and probe requests are fixed paths below the base`() {
        assertEquals("https://s.example/config", SearxngRequests.config(endpoint).uri)
        assertTrue(SearxngRequests.probe(endpoint).uri.startsWith("https://s.example/search?q=roadstr&format=json"))
        val withPath = accepted("https://s.example/searx")
        assertEquals("https://s.example/searx/config", SearxngRequests.config(withPath).uri)
    }

    // Responses

    private val searchBody = """
        {"query":"x","results":[
          {"url":"https://a.example/menu#top","title":"<b>A</b> &amp; B","content":"Steak <em>menu</em> &lt;3","engines":["Brave","duckduckgo"],"engine":"brave"},
          {"url":"javascript:alert(1)","title":"bad","content":"x"},
          {"url":"https://user:pw@c.example/x","title":"creds","content":"x"},
          {"url":"https://a.example/menu","title":"dup","content":"x"},
          {"url":"http://d.example:8080/p?a=1","title":"","content":"","engines":["google"]},
          {"url":"ftp://e.example","title":"ftp"},
          {"title":"no url"},
          "junk"
        ],"answers":[],"unresponsive_engines":[["qwant","timeout"],["bing","captcha"]]}
    """.trimIndent()

    @Test
    fun `results are cleaned, deduplicated and limited to web links`() {
        val parsed = SearxngResponseParser.parseSearch(searchBody)!!
        assertEquals(listOf("https://a.example/menu", "http://d.example:8080/p?a=1"), parsed.results.map { it.url.toString() })
        val first = parsed.results.first()
        assertEquals("A & B", first.title)
        assertEquals("Steak menu <3", first.snippet)
        assertEquals("a.example", first.host)
        assertEquals(listOf("brave", "duckduckgo"), first.engines)
        assertEquals(1, first.rank)
        assertEquals("d.example", parsed.results[1].title)
        assertEquals(listOf("qwant", "bing"), parsed.unresponsiveEngines)
    }

    @Test
    fun `hostile result text cannot disguise or inject anything`() {
        val body = """{"results":[
          {"url":"https://evil.example/x","title":"\u202eerom ot kcilC\u202c <script>alert(1)</script> Menu\u200b",
           "content":"<img src=x onerror=alert(1)> &lt;b&gt;bold&lt;/b&gt;\u0000\u2066hidden\u2069 text"}
        ]}"""
        val row = SearxngResponseParser.parseSearch(body)!!.results.single()
        for (text in listOf(row.title, row.snippet)) {
            assertTrue(text, text.none { it in "\u202a\u202b\u202c\u202d\u202e\u2066\u2067\u2068\u2069\u200b\u0000" })
            assertTrue(text, !text.contains("<script") && !text.contains("<img"))
        }
        assertEquals("evil.example", row.host)
        assertTrue(row.snippet.contains("hidden"))
    }

    @Test
    fun `an address inside the local network is kept as data and refused when it is opened`() {
        val body = """{"results":[{"url":"https://192.168.1.1/admin","title":"Router"}]}"""
        val row = SearxngResponseParser.parseSearch(body)!!.results.single()
        assertEquals("192.168.1.1", row.host)
        assertEquals(
            app.roadstr.core.web.NavigationDecision.Block(app.roadstr.core.web.BlockReason.LOCAL_HOST),
            app.roadstr.core.web.WebNavigationPolicy.decide(row.url.toString()),
        )
    }

    @Test
    fun `the number of results is capped`() {
        val rows = (1..50).joinToString(",") { """{"url":"https://h$it.example","title":"t$it"}""" }
        val parsed = SearxngResponseParser.parseSearch("""{"results":[$rows]}""")!!
        assertEquals(SearxngResponseParser.MAX_RESULTS, parsed.results.size)
    }

    @Test
    fun `anything that is not a searxng answer reads as null`() {
        assertNull(SearxngResponseParser.parseSearch("<html>nope</html>"))
        assertNull(SearxngResponseParser.parseSearch("{}"))
        assertNull(SearxngResponseParser.parseSearch("""{"results":"x"}"""))
        assertNull(SearxngResponseParser.parseSearch(""))
        assertNotNull(SearxngResponseParser.parseSearch("""{"results":[]}"""))
    }

    private val configBody = """
        {"version":"2026.10.2","categories":["general","images"],"limiter":{"enabled":true},"public_instance":false,
         "engines":[
           {"name":"duckduckgo","categories":["general"],"enabled":true},
           {"name":"google","categories":["general"],"enabled":true},
           {"name":"google images","categories":["images"],"enabled":true},
           {"name":"startpage","categories":["general"],"enabled":true},
           {"name":"brave","categories":["general","web"],"enabled":true},
           {"name":"wikipedia","categories":["general"],"enabled":false},
           {"name":"openstreetmap","categories":["map"],"enabled":true}]}
    """.trimIndent()

    @Test
    fun `the instance config is read`() {
        val info = SearxngResponseParser.parseConfig(configBody)!!
        assertEquals("2026.10.2", info.version)
        assertEquals(true, info.limiterEnabled)
        assertEquals(false, info.publicInstance)
        assertEquals(7, info.engines.size)
        assertNull(SearxngResponseParser.parseConfig("nope"))
    }

    // Source policy

    @Test
    fun `google and startpage are never put on the allowlist`() {
        val policy = SearchSourcePolicy()
        val allow = policy.allowlist(SearxngResponseParser.parseConfig(configBody))!!
        assertEquals(listOf("duckduckgo", "brave"), allow)
        assertTrue(policy.isBlocked("Google News"))
        assertTrue(policy.isBlocked("startpage"))
        assertFalse(policy.isBlocked("duckduckgo"))
        assertNull(policy.allowlist(null))
        assertNull(policy.allowlist(SearxngInstanceInfo(null, emptyList(), emptyList(), null, null)))
    }

    @Test
    fun `results that only a blocked engine produced are dropped`() {
        val parsed = SearxngResponseParser.parseSearch(
            """{"results":[
              {"url":"https://a.example","title":"a","engines":["google","brave"]},
              {"url":"https://b.example","title":"b","engines":["google"]},
              {"url":"https://c.example","title":"c"}]}""",
        )!!
        assertEquals(listOf("a", "c"), SearchSourcePolicy().filter(parsed.results).map { it.title })
        assertEquals(listOf("a"), SearchSourcePolicy(allowUnknownEngines = false).filter(parsed.results).map { it.title })
    }

    // Capability

    private fun classify(
        status: Int?,
        body: String? = searchBody,
        retry: String? = null,
        config: String? = configBody,
        policy: SearchSourcePolicy = SearchSourcePolicy(),
    ) = SearxngCapabilityClassifier.classify(status, body, retry, config, policy, Instant.parse("2026-10-05T12:00:00Z"))

    @Test
    fun `a json answer makes the instance compatible with an allowlist`() {
        val capability = classify(200) as SearxngCapability.Compatible
        assertEquals(listOf("duckduckgo", "brave"), capability.allowlist)
        assertNotNull(capability.info)
    }

    @Test
    fun `an instance without a config is still usable unless the policy is strict`() {
        val loose = classify(200, config = null) as SearxngCapability.Compatible
        assertNull(loose.allowlist)
        assertEquals(SearxngCapability.NoEngineList, classify(200, config = null, policy = SearchSourcePolicy(strict = true)))
    }

    @Test
    fun `the answers that mean something are told apart`() {
        assertEquals(SearxngCapability.JsonDisabled, classify(403))
        assertEquals(SearxngCapability.NotSearxng, classify(404))
        assertEquals(SearxngCapability.NotSearxng, classify(200, body = "<html></html>"))
        assertEquals(SearxngCapability.Unreachable, classify(null))
        assertEquals(SearxngCapability.Unreachable, classify(500))
        assertEquals(SearxngCapability.RateLimited(30), classify(429, retry = "30"))
        assertEquals(SearxngCapability.RateLimited(null), classify(429))
    }

    @Test
    fun `retry after reads both forms and is capped`() {
        val now = Instant.parse("2026-10-05T12:00:00Z")
        assertEquals(120L, RetryAfter.seconds("120", now))
        assertEquals(RetryAfter.MAX_SECONDS, RetryAfter.seconds("999999", now))
        assertEquals(90L, RetryAfter.seconds("Mon, 05 Oct 2026 12:01:30 GMT", now))
        assertEquals(0L, RetryAfter.seconds("Mon, 05 Oct 2026 11:00:00 GMT", now))
        assertNull(RetryAfter.seconds("soon", now))
        assertNull(RetryAfter.seconds(null, now))
        assertNull(RetryAfter.seconds("  ", now))
    }
}
