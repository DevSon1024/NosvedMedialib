plugins {
    alias(libs.plugins.android.library)
    `maven-publish`
}

android {
    namespace = "com.nosved.nosvedmedia.media3"
    compileSdk = 36

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

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "com.nosved.nosvedmedia"
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