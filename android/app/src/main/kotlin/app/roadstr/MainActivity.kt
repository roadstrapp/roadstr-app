package app.roadstr

import android.os.Bundle
import android.view.WindowManager
import io.flutter.embedding.android.FlutterFragmentActivity

// FlutterFragmentActivity is required by amberflutter (NIP-55 startActivityForResult).
class MainActivity : FlutterFragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // PRIVACY — FLAG_SECURE. This app holds the user's Nostr private key and
        // their home/work addresses (favorites, map centered on live GPS). Without
        // this flag Android saves a live screenshot of the current screen as the
        // recents/app-switcher thumbnail: minimizing the app leaves a picture of
        // the map centered on the user's exact location visible to anyone who
        // opens the task switcher — a fully involuntary "where is home" leak. The
        // flag also blocks manual screenshots and screen-recording malware from
        // capturing the map or the nsec-entry screen.
        //
        // Trade-off: this disables ALL in-app screenshots (users can't screenshot
        // a route to share). For a wallet-adjacent app holding an nsec this is the
        // expected default (Signal, banking apps do the same). To allow manual
        // screenshots while still hiding only the recents thumbnail on Android 13+,
        // replace the setFlags call below with `setRecentsScreenshotEnabled(false)`.
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE,
        )
    }
}
