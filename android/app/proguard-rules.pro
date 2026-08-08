# ── Roadstr ProGuard / R8 rules ───────────────────────────────────────────────

# Flutter engine — keep all reflection-based classes.
#
# Everything EXCEPT the Play Store deferred-component manager. Roadstr has no
# deferred components, so that class is never reached — but a blanket keep of
# io.flutter.** told R8 to preserve it anyway, and it carries references to
# com.google.android.play.core.*. Those references end up in the dex without
# the library behind them: dead symbols that would throw if anything ever
# reached them, and that F-Droid's scanner reports as Google dependencies.
# Excluding it lets R8 drop the class and the references with it.
# FlutterPlayStoreSplitApplication is excluded on the same grounds: it is the
# Application class for Play Store split installs, and this APK declares plain
# android.app.Application (verified with `aapt dump xmltree`), so nothing can
# reach it either.
-keep class !io.flutter.embedding.engine.deferredcomponents.**,!io.flutter.embedding.android.FlutterPlayStoreSplitApplication,io.flutter.** { *; }
-dontwarn io.flutter.**
-dontwarn com.google.android.play.core.**

# Kotlin coroutines & serialization
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# Hive (uses reflection for Box/TypeAdapter)
-keep class com.hivedb.** { *; }
-keep @com.hivedb.hive.annotations.HiveType class * { *; }

# Flutter plugins that use JNI / reflection
-keep class com.baseflow.geolocator.** { *; }
-keep class dev.fluttercommunity.plus.sensors.** { *; }
-keep class com.dexterous.flutterlocalnotifications.** { *; }
-keep class com.it_nomads.fluttersecurestorage.** { *; }

# ── ONNX Runtime (flutter_onnxruntime) ───────────────────────────────────────
# R8 full-mode strips JNI-bound classes by default.  The plugin's Kotlin wrapper
# (com.masicai.flutteronnxruntime) calls into the ONNX Runtime Java API
# (ai.onnxruntime.*) which in turn loads the native libonnxruntime.so via JNI.
# Both layers must be kept or createSession() crashes in release builds.
-keep class com.masicai.flutteronnxruntime.** { *; }
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# ── just_audio (ExoPlayer / Media3 backend) ──────────────────────────────────
-keep class com.ryanheise.just_audio.** { *; }
-dontwarn com.ryanheise.just_audio.**
-keep class com.google.android.exoplayer2.** { *; }
-dontwarn com.google.android.exoplayer2.**
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Amber / Nostr signer
-keep class rustlib.** { *; }
-dontwarn rustlib.**

# Keep all classes that are serialised/deserialised from/to JSON
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses
