pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        // JitPack serves only libadb-android and its spake2 dependency (play flavor); the fdroid flavor never resolves anything from it
        exclusiveContent {
            forRepository { maven { url = uri("https://jitpack.io") } }
            filter { includeGroupAndSubgroups("com.github.MuntashirAkon") }   // also com.github.MuntashirAkon.spake2-java
        }
    }
}
rootProject.name = "splitaudio"
include(":app")
