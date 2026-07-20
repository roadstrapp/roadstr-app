package app.roadstr

import android.os.Build
import android.os.Bundle
import io.flutter.embedding.android.FlutterFragmentActivity

// FlutterFragmentActivity is required by amberflutter (NIP-55 startActivityForResult).
class MainActivity : FlutterFragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // PRIVACY — hide only the recents/app-switcher thumbnail, not screenshots.
        //
        // A blanket FLAG_SECURE was overkill: it also blocked users from taking a
        // normal screenshot (e.g. to share a route), which is a legitimate need.
        // The genuinely valuable, zero-downside protection is hiding the live
        // thumbnail Android snapshots for the task switcher — otherwise minimizing
        // the app leaves a picture of the map centered on the user's exact location
        // visible to anyone who opens recents (a fully involuntary "where is home"
        // leak). setRecentsScreenshotEnabled(false) suppresses exactly that while
        // leaving manual screenshots and screen recording enabled. API 33+ only;
        // on older versions there is no thumbnail-only API, so screenshots simply
        // stay fully enabled.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setRecentsScreenshotEnabled(false)
        }
    }
}
