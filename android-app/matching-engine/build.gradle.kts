// Pure-Kotlin/JVM module - no Android dependencies. This is deliberate:
// the field-parsing and matching logic is the most bug-prone part of the
// app, so it lives here where it can be unit tested with plain JUnit on
// the JVM (no emulator/device needed) and reused as-is by both the OCR
// pipeline and the SMS pipeline in the :app module.
plugins {
    id("org.jetbrains.kotlin.jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    // Pins Kotlin's compile target to the same 17 as the `java {}` block
    // above, regardless of which JDK Gradle itself runs on. Without this,
    // Kotlin defaults to the JDK running Gradle (e.g. 21), which then
    // mismatches Java's target and fails the build.
    jvmToolchain(17)
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}

tasks.test {
    useJUnitPlatform()
}
