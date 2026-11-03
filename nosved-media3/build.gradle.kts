plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.devson.nosvedmedia.media3"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 26

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

}

dependencies {
    api(project(":native-codecs"))
    api(libs.androidx.media3.exoplayer)
    api(libs.androidx.media3.common)
    api(libs.androidx.media3.decoder)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}