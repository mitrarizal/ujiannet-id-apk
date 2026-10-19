Build kt

plugins {
    id 'com.android.application'
    id 'com.google.gms.google-services'
}

android {
    namespace 'com.ujiannet.app'
    compileSdk 34

    defaultConfig {
        applicationId "com.ujiannet.app"
        minSdk 24
        targetSdk 34
        versionCode 1
        versionName "1.0"
    }

    signingConfigs {
        release {
            // Membaca file keystore dan kredensial dari Environment Variables (GitHub Secrets)
            storeFile file(System.getenv("KEYSTORE_FILE_PATH") ?: "keystore.jks")
            storePassword System.getenv("KEYSTORE_PASSWORD")
            keyAlias System.getenv("KEY_ALIAS")
            keyPassword System.getenv("KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            signingConfig signingConfigs.release
            minifyEnabled false
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }

    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }
}

dependencies {
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'com.google.android.material:material:1.11.0'
    implementation 'com.google.android.gms:play-services-ads:23.0.0'

    // Menggunakan Firebase BoM versi stabil (v32.8.1) yang aman untuk Java & tanpa DataStore
    implementation platform('com.google.firebase:firebase-bom:32.8.1')
    implementation 'com.google.firebase:firebase-analytics'
}
