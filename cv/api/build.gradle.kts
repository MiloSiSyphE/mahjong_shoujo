plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(libs.coroutines.core)
    // No TFLite, no Hilt, no model-specific deps — interfaces only.
}
