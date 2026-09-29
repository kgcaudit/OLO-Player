// Top-level build file: the plugins are declared here (without applying them)
// so the versions live in one place, and each module applies the ones it needs.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    // Declared here so the Kotlin/JVM plugin version is pinned once for :core-ftp.
    alias(libs.plugins.kotlin.jvm) apply false
}
