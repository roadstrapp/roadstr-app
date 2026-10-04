val nativeBuildDirectory = rootProject.layout.projectDirectory.dir("../build/native-roadtest")
rootProject.layout.buildDirectory.value(nativeBuildDirectory)

subprojects {
    project.layout.buildDirectory.value(nativeBuildDirectory.dir(project.name))
}

tasks.register<Delete>("clean") {
    delete(nativeBuildDirectory)
}

// ── No proprietary Google libraries, anywhere in this build ──────────────────
// This module is a separate Gradle build, so the exclusions that protect the
// Flutter app (android/build.gradle.kts) do not reach it. The runtime audit of
// the APK is clean today; this makes Gradle refuse a future dependency that
// would bring Play Services in, instead of quietly packaging it.
allprojects {
    configurations.all {
        exclude(group = "com.google.android.gms")
        exclude(group = "com.google.android.play")
        exclude(group = "com.google.firebase")
        exclude(group = "com.google.mlkit")
    }
}
