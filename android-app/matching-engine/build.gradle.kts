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

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}

tasks.test {
    useJUnitPlatform()
}
