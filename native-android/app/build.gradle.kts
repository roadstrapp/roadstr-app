plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "app.roadstr"
    compileSdk = 36

    defaultConfig {
        // Deliberately distinct so this incomplete road-test shell can coexist
        // with the installed production Roadstr package.
        applicationId = "app.roadstr.roadtest"
        minSdk = 24
        targetSdk = 36
        versionCode = 2050
        versionName = "0.5.11-native-roadtest"
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
        }
    }

    sourceSets {
        getByName("main") {
            kotlin.directories.addAll(
                listOf(
                    "src/main/kotlin",
                    "../../android/app/src/main/kotlin/app/roadstr/core",
                    "../../android/app/src/main/kotlin/app/roadstr/feature",
                    "../../android/app/src/main/kotlin/app/roadstr/service/location",
                    "../../android/app/src/main/kotlin/app/roadstr/service/network",
                    "../../android/app/src/main/kotlin/app/roadstr/service/routing",
                    "../../android/app/src/main/kotlin/app/roadstr/service/search",
                ),
            )
            res.directories.add("../../android/app/src/main/res")
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

    val composeBom = platform("androidx.compose:compose-bom:2026.06.01")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.13.0")
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
