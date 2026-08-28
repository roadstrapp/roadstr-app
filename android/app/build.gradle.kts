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
        versionCode = 39
        versionName = "0.5.0"
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
    // Whether the APKs share a versionCode depends on how the build is
    // invoked, not on this block. Measured with `aapt dump badging`:
    //
    //   flutter build apk --release --split-per-abi
    //     armeabi-v7a → 1000+N   arm64-v8a → 2000+N   x86_64 → 4000+N
    //   flutter build apk --release          (splits still produced here)
    //     every APK  → N
    //
    // The Flutter Gradle plugin adds the ABI offset only when that flag sets
    // the split-per-abi project property; `splits.abi` alone does not trigger
    // it. This matters for F-Droid, which fails a build whose APK versionCode
    // differs from the declared one — hence the recipe builds without the
    // flag and ships the universal APK. Check with aapt before assuming.
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
        "a53a8ce4a9f815f393d10a220772701c077a3773aad6e8c0256341671f7b6955",
    "src/main/jniLibs/armeabi-v7a/libespeak-ng.so" to
        "8097f3faf64b01ef5f76e693f1d58ea0ae518945e49a6dd555d9efabb38e582d",
    "src/main/jniLibs/x86_64/libespeak-ng.so" to
        "eaa1991e55b9194a1e97eac745d4a3d9dbab64b0382a54004fe800a1416daa5e",
    "../../assets/espeak-ng-data.tar.gz" to
        "441d5fcf375f9bd0418fda5fa772d386388ea31e95de95f7e3a96c57104b67f3",
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
