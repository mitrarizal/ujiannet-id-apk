package com.ujiannet.id

import android.app.Application
import com.google.android.gms.ads.MobileAds

class MyApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        
        // Inisialisasi SDK Google Mobile Ads untuk Banner & Interstitial
        MobileAds.initialize(this) { initializationStatus ->
            // SDK siap digunakan
        }
    }
}
