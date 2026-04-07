// :cv:baseline — wraps the mahjong-utils-app-style TFLite detector.
//
// ISOLATION RULE: Nothing outside this module should import TFLite classes,
// YOLO post-processing logic, or any knowledge of the Majsoul class mapping.
// If a class lives here, it is BASELINE-SPECIFIC and REPLACEABLE.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "dev.mahjong.shoujo.cv.baseline"
    compileSdk = 35
    defaultConfig {
        minSdk = 26
        // TFLite models are bundled as assets — do NOT compress them.
        aaptOptions { noCompress("tflite") }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    api(project(":cv:api"))

    // TFLite — contained within this module only
    implementation(libs.tflite.task.vision)
    implementation(libs.tflite.gpu.delegate)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.coroutines.android)
    implementation(libs.timber)
}
