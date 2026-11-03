plugins {
    alias(libs.plugins.android.library)
    `maven-publish`
}

android {
    namespace = "com.devson.nosvedmedia.media3"
    compileSdk = 35

    defaultConfig {
        minSdk = 26

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "com.devson.nosvedmedia"
            artifactId = "nosved-media3"
            version = "1.0.0"

            afterEvaluate {
                from(components["release"])
            }
        }
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