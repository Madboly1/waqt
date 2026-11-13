plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.glassproductivity"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.glassproductivity"
        minSdk = 26
        targetSdk = 34
        versionCode = 60
        versionName = "5.6.34"
    }

    signingConfigs {
        create("release") {
            val storePath = System.getenv("WAQT_KEYSTORE_PATH")
            if (!storePath.isNullOrBlank()) {
                storeFile = file(storePath)
                storePassword = System.getenv("WAQT_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("WAQT_KEY_ALIAS")
                keyPassword = System.getenv("WAQT_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
}
