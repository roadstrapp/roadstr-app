package app.roadstr.feature.home

/** Stable manifest boundary for the dormant native shell. */
object NativeShellContract {
    const val CONTRACT_VERSION = 1
    const val FLUTTER_LAUNCHER_ACTIVITY = ".MainActivity"
    const val NATIVE_CANARY_ACTIVITY = ".feature.home.NativeCanaryActivity"
    const val NATIVE_CANARY_EXPORTED = false
}
