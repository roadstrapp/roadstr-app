plugins { id("com.android.application") }
android {
    namespace = "test.routing.probe"
    compileSdk = 36
    defaultConfig {
        applicationId = "test.routing.probe"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1"
        ndk { abiFilters += "arm64-v8a" }
    }
    buildTypes { debug { isDebuggable = true } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
dependencies {
    implementation("io.github.rallista:valhalla-mobile:0.6.4")
    implementation("io.github.rallista:valhalla-models-config:0.6.0")
    implementation("io.github.rallista:valhalla-models:0.6.0")
    implementation("org.maplibre.gl:android-sdk-opengl:13.5.2")
    implementation(files("libs/brouter-all.jar"))
}
