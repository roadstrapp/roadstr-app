import java.util.Properties
import java.io.FileInputStream
import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("dev.flutter.flutter-gradle-plugin")
    id("org.jetbrains.kotlin.plugin.compose")
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
        // versionCode jumps from 44 straight to 2045 (not 45) on purpose: a
        // past ZapStore publish (app.roadstr@0.4.27) went out under this same
        // signing key with version_code=2036 by mistake — comfortably above
        // every real release since — so ZapStore's "highest version_code
        // wins" update logic kept treating that phantom 0.4.27 as newer than
        // any real 0.5.x release. This has to clear 2036, not just increment.
        versionCode = 2057
        versionName = "0.6.0"

        // Which activity answers the launcher. Since 0.6.0 the Kotlin app is the product: it answers
        // the launcher and imports the Flutter app's data on first start. `-Pnative_launcher=false`
        // builds the old Flutter launcher instead, for comparing the two.
        val nativeLauncher = providers.gradleProperty("native_launcher").orNull != "false"
        manifestPlaceholders["flutterLauncherEnabled"] = (!nativeLauncher).toString()
        manifestPlaceholders["nativeLauncherEnabled"] = nativeLauncher.toString()

        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
            }
        }
    }

    // The Kotlin app is compiled from the shared trees above plus the stores, gateways and launcher
    // of the road-test module, so the road-test APK and the real app run the same code.
    sourceSets {
        getByName("main") {
            kotlin.directories.addAll(
                listOf(
                    "../../native-android/app/src/main/kotlin",
                    "../../native-android/app/src/system/kotlin",
                ),
            )
            // The Kotlin launcher reads Roadstr's icon, cursor skins, bundled
            // phrases and eSpeak data directly through AssetManager. Flutter
            // also packages these below flutter_assets/, but that private
            // prefix is not where the native runtime looks (and AAPT must
            // expand the eSpeak .gz entry to the .tar name it opens).
            assets.directories.add("../../assets")
        }
    }

    externalNativeBuild {
        cmake {
            path = file("../../native-android/app/src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildFeatures {
        compose = true
    }

    // The app changes language at runtime. Keep every locale in app bundles;
    // otherwise a language selected in Settings may have been split out by
    // the store and silently fall back to English.
    bundle {
        language {
            enableSplit = false
        }
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

// A test build can carry another version without touching the literals above.
android {
    defaultConfig {
        providers.gradleProperty("roadstrVersionCode").orNull?.let { versionCode = it.toInt() }
        providers.gradleProperty("roadstrVersionName").orNull?.let { versionName = it }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")
    implementation("org.bouncycastle:bcprov-jdk18on:1.86")
    // Match the versions already selected transitively by MapLibre/AndroidX.
    // Pinning them makes the native adapter reproducible without upgrading the
    // existing Flutter runtime's network or coroutine stack.
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    // The Kotlin app's own voice runtime (the Flutter plugin brings its copy for the legacy engine).
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.23.0")

    // 2026.06.01 is the last stable BOM line whose Compose UI artifacts keep
    // minCompileSdk <= 36. Compose 1.12 requires compileSdk 37, while Roadstr's
    // controlled rewrite intentionally preserves the current SDK 36 baseline.
    val composeBom = platform("androidx.compose:compose-bom:2026.06.01")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // maplibre_android 0.3.6 currently requests 13.5.+. Keep the same SDK the
    // Flutter renderer resolves, but make native builds reproducible and stop a
    // future 13.5.x publication from changing the binary without review.
    implementation("org.maplibre.gl:android-sdk-opengl") {
        version {
            strictly("13.5.2")
        }
        because("Roadstr audits one BSD-2-Clause MapLibre Native binary")
    }

    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
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
        "92882558288c48340e73947f80b01f62dae25911e30ebb1b3942b9bec4054cbf",
    "src/main/jniLibs/armeabi-v7a/libespeak-ng.so" to
        "ee42b95d2e91dc8be72df2711007a6fb711c0cde3dc25e88ec2b300e38d6c65b",
    "src/main/jniLibs/x86_64/libespeak-ng.so" to
        "0078c57e5a0e15b5fcde9235f84a93ed9e4e49348851d07dadb157eee0edffef",
    "../../assets/espeak-ng-data.tar.gz" to
        "ecf9859bd233a19830c0cb048dc2c2f5162a2e4ab2de637cd2f2fabeb63ad84b",
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
