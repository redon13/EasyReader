// All code that talks to the Calibre content server lives in this module.
// It uses the same (undocumented) JSON endpoints as Calibre's own web
// interface, so if a Calibre update changes something, this is the only
// place that needs fixing. It is plain Kotlin with no Android code, so it
// can be tested on any computer.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    api(libs.okhttp)
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
}
