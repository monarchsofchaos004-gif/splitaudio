plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "app.splitaudio"
    compileSdk = 34
    defaultConfig {
        applicationId = "app.splitaudio"
        minSdk = 29
        targetSdk = 34
        versionCode = 7
        versionName = "0.3.1"
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
}
dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
}
