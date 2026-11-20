plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
}

android {
    // Sinkron dengan folder app/src/main/java/com/ujiannet/app/
    // (Ubah ke "com.ujiannet.id" jika Anda memindahkan file Java ke folder com/ujiannet/id/)
    namespace = "com.ujiannet.id"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ujiannet.id"
        minSdk = 24
        targetSdk = 36
        versionCode = 21
        versionName = "1.0.20"

        multiDexEnabled = true
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
            val keystorePath = System.getenv("KEYSTORE_FILE_PATH") ?: "keystore.jks"
            storeFile = file(keystorePath)
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = System.getenv("KEY_ALIAS")
            keyPassword = System.getenv("KEY_PASSWORD")
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
        getByName("debug") {
            isMinifyEnabled = false
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity:1.10.1")
    implementation("androidx.core:core:1.16.0")

    // Wajib untuk AppOpenAd (ProcessLifecycleOwner & DefaultLifecycleObserver)
    implementation("androidx.lifecycle:lifecycle-process:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime:2.9.0")

    // Google Mobile Ads SDK (App Open, Rewarded, Banner, Interstitial)
    implementation("com.google.android.gms:play-services-ads:24.3.0")

    // Firebase BoM & Analytics
    implementation(platform("com.google.firebase:firebase-bom:33.12.0"))
    implementation("com.google.firebase:firebase-analytics")
}
