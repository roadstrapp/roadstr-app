import java.util.Properties
import java.io.FileInputStream
import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("dev.flutter.flutter-gradle-plugin")
}

// ── Release signing ───────────────────────────────────────────────────────────
// Create android/key.properties with your keystore credentials (never commit it).
// See android/key.properties.template for the expected format.
val keyPropertiesFile = rootProject.file("key.properties")
val keyProperties = Properties()
if (keyPropertiesFile.exists()) {
    keyProperties.load(FileInputStream(keyPropertiesFile))
}
fun signingProperty(name: String): String =
    (keyProperties[name] as String?)?.takeIf(String::isNotBlank)
        ?: throw GradleException("Missing signing property '$name' in ${keyPropertiesFile.path}")

val hasReleaseSigningConfig = keyPropertiesFile.exists()
val configuredStorePath = if (hasReleaseSigningConfig) signingProperty("storeFile") else ""
val releaseStoreFile = configuredStorePath.takeIf(String::isNotBlank)?.let { path ->
    // build_release.sh interprets storeFile from the repository root. Keep
    // compatibility with older app-relative configurations, but prefer the
    // same repository-root interpretation when that file exists.
    val repositoryRelative = rootProject.projectDir.parentFile.resolve(path)
    val appRelative = file(path)
    if (repositoryRelative.isFile) repositoryRelative else appRelative
}
if (hasReleaseSigningConfig && releaseStoreFile?.isFile != true) {
    throw GradleException("Release keystore not found: $configuredStorePath")
}

android {
    namespace = "app.roadstr"
    compileSdk = 36
    ndkVersion = flutter.ndkVersion

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        applicationId = "app.roadstr"
        minSdk = flutter.minSdkVersion
        targetSdk = flutter.targetSdkVersion
        // ── Version ───────────────────────────────────────────────────────────
        // Literal values, not flutter.versionCode/flutter.versionName.
        //
        // Flutter resolves those from pubspec.yaml at build time, through
        // local.properties, so nothing in the checked-out tree states the
        // version. F-Droid's `checkupdates` reads this file with a regex
        // against a bare clone — it never runs Gradle — so with the dynamic
        // form it found nothing, walked back through every tag looking for a
        // readable one, and failed with "Couldn't find any version
        // information". That is what blocks automatic update detection.
        //
        // These two lines are the single source of truth for the Android
        // build and MUST match `version:` in pubspec.yaml. That is enforced by
        // test/version_consistency_test.dart rather than by memory.
        versionCode = 23
        versionName = "0.4.14"
    }

    // ── Signing ───────────────────────────────────────────────────────────────
    signingConfigs {
        create("release") {
            if (hasReleaseSigningConfig) {
                keyAlias      = signingProperty("keyAlias")
                keyPassword   = signingProperty("keyPassword")
                storeFile     = releaseStoreFile
                storePassword = signingProperty("storePassword")
            }
        }
    }

    buildTypes {
        release {
            // Use release signing only when BOTH key.properties exists AND the
            // referenced keystore file actually exists on disk.
            // Never disguise a debug-signed artifact as a release. Without a
            // project keystore Gradle emits an unsigned release, which stores
            // such as F-Droid can sign with their own controlled key.
            signingConfig = if (hasReleaseSigningConfig)
                signingConfigs.getByName("release")
            else
                null

            // R8 full-mode: removes unused code + resources, obfuscates identifiers
            isMinifyEnabled   = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // ── ABI splits — separate smaller APKs per CPU architecture ──────────────
    // arm64-v8a  → ~95% of phones sold since 2016 (~30% smaller than universal)
    // armeabi-v7a → older 32-bit devices
    // x86_64     → emulators and rare x86 tablets
    // universal  → fat fallback (use when ABI is unknown)
    //
    // NB: the APKs do NOT share a versionCode, whatever this comment used to
    // claim. With splits enabled the Flutter Gradle plugin multiplies an ABI
    // offset into it, so a versionCode of N ships as:
    //
    //   armeabi-v7a → 1000+N     arm64-v8a → 2000+N     x86_64 → 4000+N
    //   universal   →      N
    //
    // Verify with `aapt dump badging <apk>` before assuming otherwise. This
    // matters for F-Droid, which fails a build whose APK versionCode differs
    // from the one declared in the metadata — which is why the F-Droid recipe
    // builds the universal APK rather than a split one.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")
}

flutter {
    source = "../.."
}

// The native phonemizer is committed so normal Flutter builds stay offline,
// but every byte must match the reproducible, pinned-source build in
// tools/build_espeak_android.sh. This catches accidental or malicious blob
// replacement even when developers invoke `flutter build` directly.
val bundledNativeChecksums = mapOf(
    "src/main/jniLibs/arm64-v8a/libespeak-ng.so" to
        "6e301b195c241c6ae9c8b6b67f79eac6c7cd0cea0c575dc9f47bc73aea44189b",
    "src/main/jniLibs/armeabi-v7a/libespeak-ng.so" to
        "e4ebee2962a1364db285576d2645ed291c5d4681528fa96fc29c0d20cca7580f",
    "src/main/jniLibs/x86_64/libespeak-ng.so" to
        "33cc7a1432a31e401ceba081e7e207cec22e07e0317d347a355a9b1b6ade6cff",
    "../../assets/espeak-ng-data.tar.gz" to
        "e245635cf3a0042f8d2081fec0a4c578e58b2f545ec4313823be9862a02f98bb",
)

val verifyBundledNativeAssets by tasks.registering {
    val checkedFiles = bundledNativeChecksums.keys.map(::file)
    inputs.files(checkedFiles)
    doLast {
        for ((path, expected) in bundledNativeChecksums) {
            val artifact = file(path)
            check(artifact.isFile) { "Missing bundled native artifact: $path" }
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(artifact.readBytes())
                .joinToString("") { "%02x".format(it) }
            check(digest == expected) {
                "Bundled native artifact failed SHA-256 verification: $path"
            }
        }
    }
}

tasks.named("preBuild").configure {
    dependsOn(verifyBundledNativeAssets)
}
