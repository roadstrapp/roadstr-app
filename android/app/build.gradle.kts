import java.util.Properties
import java.io.FileInputStream

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
        versionCode = flutter.versionCode
        versionName = flutter.versionName
    }

    // ── Signing ───────────────────────────────────────────────────────────────
    signingConfigs {
        create("release") {
            if (keyPropertiesFile.exists()) {
                keyAlias      = keyProperties["keyAlias"]     as String
                keyPassword   = keyProperties["keyPassword"]  as String
                storeFile     = file(keyProperties["storeFile"] as String)
                storePassword = keyProperties["storePassword"] as String
            }
        }
    }

    buildTypes {
        release {
            // Use release signing only when BOTH key.properties exists AND the
            // referenced keystore file actually exists on disk.
            val storeFilePath = keyProperties["storeFile"] as String? ?: ""
            val storeFileExists = storeFilePath.isNotEmpty() && file(storeFilePath).exists()
            signingConfig = if (keyPropertiesFile.exists() && storeFileExists)
                signingConfigs.getByName("release")
            else
                signingConfigs.getByName("debug")   // fallback when keystore is missing

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
    // Note: ZapStore and F-Droid do NOT require unique versionCodes per ABI
    // (that is only needed for Google Play Store). All APKs share the same
    // versionCode, which is simpler and fully compatible with both stores.
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
