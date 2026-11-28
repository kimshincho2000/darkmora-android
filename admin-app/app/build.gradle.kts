plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "uz.darkmora.admin"
    compileSdk = 35

    defaultConfig {
        applicationId = "uz.darkmora.admin"
        minSdk = 24
        targetSdk = 35
        versionCode = 5
        versionName = "1.1.1"
    }

    signingConfigs {
        create("release") {
            val ksPath = System.getenv("DARKMORA_STORE_FILE") ?: "darkmora-admin.jks"
            val ksFile = file(ksPath)
            if (ksFile.exists() && !System.getenv("DARKMORA_STORE_PASSWORD").isNullOrBlank()) {
                storeFile = ksFile
                storePassword = System.getenv("DARKMORA_STORE_PASSWORD")
                keyAlias = System.getenv("DARKMORA_KEY_ALIAS") ?: "darkmora"
                keyPassword = System.getenv("DARKMORA_KEY_PASSWORD") ?: System.getenv("DARKMORA_STORE_PASSWORD")
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (signingConfigs.getByName("release").storeFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            if (signingConfigs.getByName("release").storeFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.11.0")
}
