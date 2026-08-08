allprojects {
    repositories {
        google()
        mavenCentral()
    }
}

val newBuildDir: Directory =
    rootProject.layout.buildDirectory
        .dir("../../build")
        .get()
rootProject.layout.buildDirectory.value(newBuildDir)

// ── No proprietary Google libraries, anywhere in the build ───────────────────
// Roadstr reads location through Android's own LocationManager and never calls
// Play Services (see GpsService: every entry point sets forceLocationManager).
// The library still arrived as a transitive dependency of geolocator_android,
// putting fifteen com.google.android.gms.* classes in the APK that nothing can
// execute on a device without Google services — dead weight that also made the
// app look dependent on something it deliberately avoids.
//
// Excluded here, at the root, rather than in :app: the dependency belongs to
// the plugin's own configurations, so excluding it downstream would not stop
// it being compiled and packaged.
allprojects {
    configurations.all {
        exclude(group = "com.google.android.gms")
        exclude(group = "com.google.android.play")
    }
}

subprojects {
    val newSubprojectBuildDir: Directory = newBuildDir.dir(project.name)
    project.layout.buildDirectory.value(newSubprojectBuildDir)
    // Forza compileSdk >= 36 su tutti i plugin (es. amberflutter fermo a 33)
    afterEvaluate {
        extensions.findByType(com.android.build.gradle.BaseExtension::class)
            ?.compileSdkVersion(36)
    }
}
subprojects {
    project.evaluationDependsOn(":app")
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
