import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val bundledVoiceChecksums = mapOf(
    "../../android/app/src/main/jniLibs/arm64-v8a/libespeak-ng.so" to
        "a53a8ce4a9f815f393d10a220772701c077a3773aad6e8c0256341671f7b6955",
    "../../android/app/src/main/jniLibs/armeabi-v7a/libespeak-ng.so" to
        "8097f3faf64b01ef5f76e693f1d58ea0ae518945e49a6dd555d9efabb38e582d",
    "../../android/app/src/main/jniLibs/x86_64/libespeak-ng.so" to
        "eaa1991e55b9194a1e97eac745d4a3d9dbab64b0382a54004fe800a1416daa5e",
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
                    "../../android/app/src/main/kotlin/app/roadstr/service/hazards",
                    "../../android/app/src/main/kotlin/app/roadstr/service/location",
                    "../../android/app/src/main/kotlin/app/roadstr/service/network",
                    "../../android/app/src/main/kotlin/app/roadstr/service/nostr",
                    "../../android/app/src/main/kotlin/app/roadstr/service/routing",
                    "../../android/app/src/main/kotlin/app/roadstr/service/search",
                ),
            )
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

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
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
