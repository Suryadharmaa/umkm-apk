plugins {
    alias(libs.plugins.android)
    alias(libs.plugins.kotlin)
    alias(libs.plugins.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
}
android {
    namespace = "id.umkmku.pos"
    compileSdk = 36
    defaultConfig {
        applicationId = "id.umkmku.pos"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.2.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures.compose = true
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
kotlin.compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
room.schemaDirectory("$projectDir/schemas")
dependencies {
    implementation(platform(libs.compose.bom))
    androidTestImplementation(platform(libs.compose.bom))
    implementation(libs.activity)
    implementation(libs.lifecycle.compose)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.navigation)
    implementation(libs.material3)
    implementation(libs.icons)
    implementation(libs.preview)
    implementation(libs.room.runtime)
    implementation(libs.zxing)
    implementation(libs.tink)
    implementation(libs.hilt)
    ksp(libs.hilt.compiler)
    ksp(libs.room.compiler)
    debugImplementation(libs.tooling)
    debugImplementation(libs.compose.test.manifest)
    testImplementation(libs.junit)
    androidTestImplementation(libs.android.junit)
    androidTestImplementation(libs.test.runner)
    androidTestImplementation(libs.compose.test)
}
