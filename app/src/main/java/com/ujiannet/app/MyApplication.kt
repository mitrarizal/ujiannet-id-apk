package com.ujiannet.app

import android.app.Activity
import android.app.Application
import android.content.Context // Tambahkan import ini
import android.os.Bundle
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.appopen.AppOpenAd
import java.util.Date

class MyApplication : Application(), Application.ActivityLifecycleCallbacks {

    private lateinit var appOpenAdManager: AppOpenAdManager
    private var currentActivity: Activity? = null

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(this)
        MobileAds.initialize(this) {}

        appOpenAdManager = AppOpenAdManager()
        
        // PERBAIKAN 1: Muat iklan secara paksa saat aplikasi pertama kali diinisialisasi
        appOpenAdManager.loadAd(this) 
        
        ProcessLifecycleOwner.get().lifecycle.addObserver(appOpenAdManager)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityStarted(activity: Activity) { currentActivity = activity }
    override fun onActivityResumed(activity: Activity) { currentActivity = activity }
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) { currentActivity = null }

    private inner class AppOpenAdManager : DefaultLifecycleObserver {
        private var appOpenAd: AppOpenAd? = null
        private var isLoadingAd = false
        private var isShowingAd = false
        private var loadTime: Long = 0

        private val AD_UNIT_ID = "ca-app-pub-3940256099942544/9257395168" // ID Tes

        override fun onStart(owner: LifecycleOwner) {
            currentActivity?.let { showAdIfAvailable(it) }
        }

        // PERBAIKAN 2: Ubah tipe parameter dari Activity menjadi Context
        fun loadAd(context: Context) {
            if (isLoadingAd || isAdAvailable()) return

            isLoadingAd = true
            val request = AdRequest.Builder().build()
            
            // AppOpenAd.load mendukung Context, jadi aman menggunakan 'this' dari Application
            AppOpenAd.load(
                context,
                AD_UNIT_ID,
                request,
                object : AppOpenAd.AppOpenAdLoadCallback() {
                    override fun onAdLoaded(ad: AppOpenAd) {
                        appOpenAd = ad
                        isLoadingAd = false
                        loadTime = Date().time
                        
                        // Jika activity sudah siap saat iklan selesai diunduh, langsung tampilkan
                        currentActivity?.let { showAdIfAvailable(it) }
                    }

                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        isLoadingAd = false
                    }
                }
            )
        }

        private fun wasLoadTimeLessThanNHoursAgo(numHours: Long): Boolean {
            val dateDifference: Long = Date().time - loadTime
            val numMilliSecondsPerHour: Long = 3600000
            return dateDifference < numMilliSecondsPerHour * numHours
        }

        private fun isAdAvailable(): Boolean {
            return appOpenAd != null && wasLoadTimeLessThanNHoursAgo(4)
        }

        fun showAdIfAvailable(activity: Activity) {
            if (!isShowingAd && isAdAvailable()) {
                appOpenAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        appOpenAd = null
                        isShowingAd = false
                        loadAd(activity) // Activity mewarisi Context, sehingga tetap valid
                    }

                    override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                        appOpenAd = null
                        isShowingAd = false
                        loadAd(activity)
                    }

                    override fun onAdShowedFullScreenContent() {
                        isShowingAd = true
                    }
                }
                appOpenAd?.show(activity)
            } else {
                loadAd(activity)
            }
        }
    }
}
