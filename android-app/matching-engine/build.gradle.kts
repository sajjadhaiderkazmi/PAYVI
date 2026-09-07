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

// Sets the Kotlin compiler's *output bytecode level* to 17 directly,
// without asking Gradle to locate/download an actual JDK 17 installation
// (that's what kotlin { jvmToolchain(17) } would do, and it fails on a
// machine that only has e.g. JDK 21 with toolchain auto-download
// disabled). Any JDK >= 17 running Gradle can still emit 17-level
// bytecode via this flag - this matches how :app sets the same target.
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}

tasks.test {
    useJUnitPlatform()
}
