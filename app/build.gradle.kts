plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "app.splitaudio"
    compileSdk = 36
    defaultConfig {
        minSdk = 31
        targetSdk = 36
        versionCode = 14
        versionName = "0.5.2"
    }
    // One code base, two builds:
    //   fdroid = Shizuku only, every feature free, accessibility volume keys (F-Droid / GitHub releases), applicationId app.splitaudio
    //   play   = Shizuku if granted, otherwise the built-in ADB client (no Shizuku needed); Pro features need a purchase or the trial; no accessibility service
    flavorDimensions += "dist"
    productFlavors {
        create("fdroid") { dimension = "dist"; applicationId = "app.splitaudio" }
        create("play") { dimension = "dist"; applicationId = "app.splitaudio.play" }
    }
    // Release builds are UNSIGNED on purpose: this file has no signing configuration and the build never reads a key file.
    // The release APK is signed afterwards, outside of gradle, with the developer's key (CI uploads the unsigned APK as an artifact).
    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isReturnDefaultValues = true }
    // The routing helper (route/AppRoute.java, default package) is compiled into the unit tests too, so its pure helpers
    // (volume step maths, who may use the control socket, file readers, multi focus flags) are tested straight from the source.
    sourceSets {
        getByName("test") { java.srcDir("../route") }
    }
}
dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    // Built-in ADB client, play flavor only (libadb-android is dual licensed GPL-3.0-or-later OR Apache-2.0; used under Apache-2.0)
    "playImplementation"("com.github.MuntashirAkon:libadb-android:3.1.1")
    "playImplementation"("org.conscrypt:conscrypt-android:2.5.3")
    testImplementation("junit:junit:4.13.2")
}
