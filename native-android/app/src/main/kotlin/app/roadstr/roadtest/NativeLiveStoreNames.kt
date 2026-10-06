package app.roadstr.roadtest

/**
 * The names of the preferences files and Keystore keys the live stores use. The road-test APK keeps
 * `roadtest`; the real app uses `live`, so the two never share a file or a key and a name chosen
 * here is the one the user's data is found under after every update.
 */
class NativeLiveStoreNames(private val prefix: String = ROADTEST) {
    init {
        require(prefix.matches(SAFE)) { "Invalid store prefix" }
    }

    /** A SharedPreferences file name. */
    fun prefs(name: String): String = "${prefix}_$name"

    /** A Keystore key alias. */
    fun alias(name: String): String = "app.roadstr.$prefix.$name.v1"

    /** The key that protects a stored nsec; it predates [alias] and keeps its own shape. */
    val identityAlias: String get() = "${prefix}_nostr_nsec"

    companion object {
        const val ROADTEST = "roadtest"
        const val LIVE = "live"
        private val SAFE = Regex("[a-z][a-z0-9]{1,15}")
    }
}
