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
        versionCode = 9
        versionName = "0.5.0-dev"
    }
    // One code base, two builds:
    //   fdroid = Shizuku only, every feature free, accessibility volume keys (F-Droid / GitHub releases), applicationId app.splitaudio
    //   play   = Shizuku if granted, otherwise the built-in ADB client (no Shizuku needed); Pro features need a purchase or the trial; no accessibility service
    flavorDimensions += "dist"
    productFlavors {
        create("fdroid") { dimension = "dist"; applicationId = "app.splitaudio" }
        create("play") { dimension = "dist"; applicationId = "app.splitaudio.play" }
    }
    // Release signing: only active when RELEASE_SIGNING_JSON points to an existing file
    // (local release builds). Without it (e.g. CI) the build behaves exactly as before.
    val releaseSigningFile = System.getenv("RELEASE_SIGNING_JSON")?.let { file(it) }?.takeIf { it.exists() }
    if (releaseSigningFile != null) {
        @Suppress("UNCHECKED_CAST")
        val sj = groovy.json.JsonSlurper().parse(releaseSigningFile) as Map<String, Any?>
        signingConfigs {
            create("release") {
                storeFile = file(sj["storeFile"] as String)
                storePassword = sj["storePassword"] as String
                keyAlias = "splitaudio"
                keyPassword = sj["keyPassword"] as String
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            if (releaseSigningFile != null) signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isReturnDefaultValues = true }
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
