import java.security.MessageDigest
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Optional in-app browser (see docs/world-discovery, D-11): `-Pgeckoview=true` builds a variant
// with GeckoView, one ABI and minSdk 26. Without the property nothing here changes.
val geckoView = providers.gradleProperty("geckoview").orNull == "true"
val geckoViewVersion = "157.0.20260924084938"
// Keep in step with the Kotlin plugin version in settings.gradle.kts.
val projectKotlin = "2.2.10"

val bundledVoiceChecksums = mapOf(
    "../../android/app/src/main/jniLibs/arm64-v8a/libespeak-ng.so" to
        "92882558288c48340e73947f80b01f62dae25911e30ebb1b3942b9bec4054cbf",
    "../../android/app/src/main/jniLibs/armeabi-v7a/libespeak-ng.so" to
        "ee42b95d2e91dc8be72df2711007a6fb711c0cde3dc25e88ec2b300e38d6c65b",
    "../../android/app/src/main/jniLibs/x86_64/libespeak-ng.so" to
        "0078c57e5a0e15b5fcde9235f84a93ed9e4e49348851d07dadb157eee0edffef",
    "../../assets/espeak-ng-data.tar.gz" to
        "ecf9859bd233a19830c0cb048dc2c2f5162a2e4ab2de637cd2f2fabeb63ad84b",
)

val verifyBundledVoiceAssets by tasks.registering {
    inputs.files(bundledVoiceChecksums.keys.map(::file))
    doLast {
        bundledVoiceChecksums.forEach { (path, expected) ->
            val artifact = file(path)
            check(artifact.isFile) { "Missing bundled voice artifact: $path" }
            val actual = MessageDigest.getInstance("SHA-256")
                .digest(artifact.readBytes())
                .joinToString("") { "%02x".format(it) }
            check(actual == expected) { "Bundled voice artifact failed SHA-256: $path" }
        }
    }
}

tasks.named("preBuild").configure {
    dependsOn(verifyBundledVoiceAssets)
}

android {
    namespace = "app.roadstr"
    compileSdk = 36
    ndkVersion = "27.1.12297006"

    defaultConfig {
        // Deliberately distinct so this incomplete road-test shell can coexist
        // with the installed production Roadstr package.
        applicationId = "app.roadstr.roadtest"
        minSdk = 24
        targetSdk = 36
        versionCode = 2050
        versionName = "0.5.11-native-roadtest"
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
            }
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    buildTypes {
        release {
            // This module is an internal road-test harness, never an official
            // update artifact. Release hardening stays owned by android/app.
            isMinifyEnabled = false
            // Signed with the local debug key so a release build installs on a
            // test phone. The real release key is never read here, and this
            // package (app.roadstr.roadtest) cannot replace the real app.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    sourceSets {
        getByName("main") {
            kotlin.directories.addAll(
                listOf(
                    "src/main/kotlin",
                    "../../android/app/src/main/kotlin/app/roadstr/core",
                    "../../android/app/src/main/kotlin/app/roadstr/feature",
                    "../../android/app/src/main/kotlin/app/roadstr/service/discovery",
                    "../../android/app/src/main/kotlin/app/roadstr/service/hazards",
                    "../../android/app/src/main/kotlin/app/roadstr/service/location",
                    "../../android/app/src/main/kotlin/app/roadstr/service/network",
                    "../../android/app/src/main/kotlin/app/roadstr/service/nostr",
                    "../../android/app/src/main/kotlin/app/roadstr/service/routing",
                    "../../android/app/src/main/kotlin/app/roadstr/service/search",
                ),
            )
            kotlin.directories.add("src/system/kotlin")
            res.directories.add("../../android/app/src/main/res")
            assets.directories.add("../../assets")
            jniLibs.directories.add("../../android/app/src/main/jniLibs")
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

// The in-app browser variant, built with ./gradlew-geckoview. Everything it changes is here, so the
// default build above reads exactly as before.
if (geckoView) {
    android {
        // GeckoView 157 pulls androidx.core 1.19, which needs compileSdk 37; its AAR needs minSdk 26.
        compileSdk = 37
        defaultConfig {
            // A different package, so the variant and the default build can be installed side by side.
            applicationId = "app.roadstr.roadtest.gecko"
            minSdk = 26
            ndk {
                // One ABI keeps the variant near 250 MiB instead of 500.
                abiFilters.clear()
                abiFilters += "arm64-v8a"
            }
        }
        sourceSets {
            getByName("main") {
                kotlin.directories.remove("src/system/kotlin")
                kotlin.directories.add("src/gecko/kotlin")
                // The bundled page extension (see docs/world-discovery, Phase 6).
                assets.directories.add("src/gecko/assets")
            }
        }
    }
}

// Update-path candidate (docs/kotlin-rewrite/RELEASE_COMPATIBILITY.md): `-Pcandidate=true` builds this
// app under the production identity, signed with the official release key read from
// ../android/key.properties at build time (the key and its passwords are never in the repository),
// so that Android's in-place update of the shipped app can be tested. Without the property nothing here applies.
val candidate = providers.gradleProperty("candidate").orNull == "true"
if (candidate) {
    check(!geckoView) { "The update candidate and the GeckoView variant are separate builds" }
    val keyProperties = Properties().apply {
        file("../../android/key.properties").inputStream().use { load(it) }
    }
    val storePath = keyProperties.getProperty("storeFile")
    android {
        signingConfigs {
            create("candidate") {
                storeFile = if (storePath.startsWith("/")) file(storePath) else file("../../android/$storePath")
                storePassword = keyProperties.getProperty("storePassword")
                keyAlias = keyProperties.getProperty("keyAlias")
                keyPassword = keyProperties.getProperty("keyPassword")
            }
        }
        defaultConfig {
            applicationId = "app.roadstr"
            versionCode = 2051
            versionName = "0.5.12-kotlin-candidate"
        }
        buildTypes {
            getByName("release") { signingConfig = signingConfigs.getByName("candidate") }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

if (geckoView) {
    configurations.configureEach {
        // Roadstr ships no Google Play Services class. GeckoView's POM pulls play-services-fido
        // for WebAuthn, which the browser keeps switched off.
        exclude(group = "com.google.android.gms")
        // GeckoView's POM asks for a newer Kotlin standard library than this project's compiler reads.
        resolutionStrategy.force("org.jetbrains.kotlin:kotlin-stdlib:$projectKotlin")
    }
}

dependencies {
    if (geckoView) {
        implementation("org.mozilla.geckoview:geckoview-arm64-v8a:$geckoViewVersion")
    }
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")
    implementation("org.bouncycastle:bcprov-jdk18on:1.86")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.23.0")

    val composeBom = platform("androidx.compose:compose-bom:2026.06.01")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("org.maplibre.gl:android-sdk-opengl") {
        version {
            strictly("13.5.2")
        }
        because("Roadstr audits one BSD-2-Clause MapLibre Native binary")
    }
}
