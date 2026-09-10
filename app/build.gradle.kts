plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.appenza.smsapi"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.appenza.smsapi"
        minSdk = 24
        targetSdk = 36
        versionCode = 32
        versionName = "0.19.2-filter-sheet"
    }

    signingConfigs {
        create("stableRelease") {
            // Values exist only in GitHub Actions Secrets. The key never enters this repository.
            storeFile = file(System.getenv("SMS_API_KEYSTORE_PATH") ?: "missing-signing-key")
            storePassword = System.getenv("SMS_API_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("SMS_API_KEY_ALIAS")
            keyPassword = System.getenv("SMS_API_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("stableRelease")
        }
        debug {
            isMinifyEnabled = false
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
    implementation("androidx.drawerlayout:drawerlayout:1.2.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("com.google.android.material:material:1.12.0")
}
