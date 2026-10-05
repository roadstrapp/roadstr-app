pluginManagement {
    // `geckoview=false` in gradle.properties; `-Pgeckoview=true` builds the optional browser variant.
    val geckoview: String by settings
    resolutionStrategy {
        eachPlugin {
            // GeckoView 157 pulls androidx.core 1.19 and lifecycle 2.11, which need Android Gradle
            // plugin 9.1.0 and compileSdk 37; the default build keeps the audited 9.0.1 and 36.
            if (geckoview == "true" && requested.id.id == "com.android.application") useVersion("9.1.0")
        }
    }
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

// The optional in-app browser variant (-Pgeckoview=true) is the only build that talks to
// Mozilla's repository, and only for GeckoView's own artifacts.
val geckoViewVariant = providers.gradleProperty("geckoview").orNull == "true"

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        if (geckoViewVariant) {
            maven("https://maven.mozilla.org/maven2") {
                content { includeGroup("org.mozilla.geckoview") }
            }
        }
    }
}

plugins {
    id("com.android.application") version "9.0.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
}

rootProject.name = "roadstr-native-roadtest"
include(":app")
