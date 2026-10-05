package app.roadstr.core.discovery.resolve

import java.util.Locale

/**
 * Whether two web addresses belong to the same business. A website is only evidence when it
 * is the business's own: a page on Facebook, TripAdvisor or a site builder says nothing about
 * which restaurant it describes, so those never match by address alone.
 */
object HostMatching {
    /** Sites that list many businesses; an address on them identifies the listing, not the place. */
    private val aggregatorLabels = setOf(
        "facebook", "fb", "instagram", "twitter", "x", "tiktok", "youtube", "youtu", "linkedin",
        "pinterest", "tripadvisor", "yelp", "thefork", "lafourchette", "eltenedor", "booking",
        "airbnb", "foursquare", "zomato", "ubereats", "deliveroo", "justeat", "just-eat",
        "glovoapp", "google", "wikipedia", "wikimedia", "wikidata", "openstreetmap", "amazon",
        "mapcarta", "waze", "bing", "yahoo", "duckduckgo", "pagesjaunes", "paginegialle",
        "yellowpages", "trustpilot", "restaurantguru", "opentable", "quandoo", "michelin",
    )

    /** Site builders that give each business a subdomain: the whole host is the identity. */
    private val subdomainHosts = setOf(
        "wixsite.com", "business.site", "blogspot.com", "wordpress.com", "weebly.com",
        "squarespace.com", "linktr.ee", "carrd.co", "github.io", "godaddysites.com",
        "jimdosite.com", "webnode.com", "webflow.io", "netlify.app", "vercel.app",
        "myshopify.com", "tumblr.com", "medium.com", "notion.site", "sites.google.com",
    )

    /** Second-level labels that sit under a country code: `co.uk`, `com.au`, `or.jp`. */
    private val secondLevel = setOf("co", "com", "org", "net", "ac", "gov", "edu", "ne", "or", "go", "ltd", "plc")
    private val strippedPrefixes = listOf("www.", "m.", "mobile.", "amp.")

    /** The host without a leading `www.`, lowercased, or null for an address that is not a name. */
    fun cleanHost(host: String): String? {
        var name = host.trim().trimEnd('.').lowercase(Locale.ROOT)
        if (name.isEmpty() || name.any { it.isWhitespace() }) return null
        for (prefix in strippedPrefixes) {
            if (name.startsWith(prefix) && name.count { it == '.' } >= 2) name = name.removePrefix(prefix)
        }
        val labels = name.split('.')
        if (labels.size < 2 || labels.any { it.isEmpty() }) return null
        if (labels.all { it.toIntOrNull() != null }) return null
        return name
    }

    /** `shop.example.co.uk` becomes `example.co.uk`; a name with no registrable part gives null. */
    fun registrableDomain(host: String): String? {
        val clean = cleanHost(host) ?: return null
        val labels = clean.split('.')
        val last = labels.last()
        val underCountryCode = last.length == 2 && labels.size >= 3 && labels[labels.size - 2] in secondLevel
        val keep = if (underCountryCode) 3 else 2
        return labels.takeLast(keep).joinToString(".")
    }

    fun isAggregator(host: String): Boolean {
        val registrable = registrableDomain(host) ?: return false
        val label = registrable.substringBefore('.')
        return label in aggregatorLabels
    }

    /**
     * What identifies the business behind [host], or null when the host cannot tell:
     * the registrable domain, the full host on a site builder, nothing on an aggregator.
     */
    fun siteKey(host: String): String? {
        val clean = cleanHost(host) ?: return null
        if (isAggregator(clean)) return null
        val registrable = registrableDomain(clean) ?: return null
        return if (registrable in subdomainHosts) clean else registrable
    }

    fun sameSite(first: String, second: String): Boolean {
        val a = siteKey(first) ?: return false
        return a == siteKey(second)
    }
}
