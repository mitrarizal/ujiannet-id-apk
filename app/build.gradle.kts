plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
id("com.google.gms.google-services")
}

android {
    namespace = "com.ujiannet.id"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ujiannet.id"
        minSdk = 24
        targetSdk = 36
        versionCode = 12 // Dinaikkan dari 7 agar Play Console menerima file rilis baru
        versionName = "1.0.11"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    signingConfigs {
        create("release") {
            storeFile = file("release.jks")
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = System.getenv("KEY_ALIAS")
            keyPassword = System.getenv("KEY_PASSWORD")
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true // Mengaktifkan optimasi R8 & obfuscation
            isShrinkResources = true // Mengurangi ukuran file resource yang tidak terpakai
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("com.google.android.gms:play-services-ads:23.0.0")
}
// Firebase BoM & Analytics (Disesuaikan ke format Kotlin DSL)
    implementation(platform("com.google.firebase:firebase-bom:32.8.1"))
    implementation("com.google.firebase:firebase-analytics")
}
