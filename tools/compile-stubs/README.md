# Compile-only API stubs

These minimal declarations are used only by `tools/manual_build.py` when a full
Gradle/Fabric toolchain is unavailable. They are never packaged in the mod JAR.
Normal development and release builds should use `./gradlew build` with Java 25.
