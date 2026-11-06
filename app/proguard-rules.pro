# R8 shrinks and optimizes the release build. Names stay as they are, so a crash report
# points at the same classes and lines as this source.
-dontobfuscate
-keepattributes SourceFile,LineNumberTable

# ShizukuBackend reaches this private method by name, so nothing else marks it as used.
-keepclassmembers class rikka.shizuku.Shizuku {
    static *** newProcess(java.lang.String[], java.lang.String[], java.lang.String);
}
