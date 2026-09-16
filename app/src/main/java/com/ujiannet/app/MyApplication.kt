package com.ujiannet.app

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
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

    private var appOpenAdManager: AppOpenAdManager? = null
    private var currentActivity: Activity? = null

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(this)

        // Inisialisasi SDK AdMob secara asinkron sebelum memuat iklan
        MobileAds.initialize(this) {
            Log.d("AdMobAppOpen", "SDK AdMob Berhasil Diinisialisasi.")
            appOpenAdManager = AppOpenAdManager()
            ProcessLifecycleOwner.get().lifecycle.addObserver(appOpenAdManager!!)
            
            // Muat iklan saat SDK siap dan ada Activity aktif
            currentActivity?.let { activity ->
                appOpenAdManager?.loadAd(activity)
            }
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    
    override fun onActivityStarted(activity: Activity) { 
        currentActivity = activity 
        appOpenAdManager?.showAdIfAvailable(activity)
    }
    
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

        // Unit ID Iklan (Gunakan ID Tes Google saat pengujian, ganti ID Asli saat Rilis)
        private val AD_UNIT_ID = "ca-app-pub-3940256099942544/9257395168"

        override fun onStart(owner: LifecycleOwner) {
            currentActivity?.let { showAdIfAvailable(it) }
        }

        fun loadAd(activity: Activity) {
            if (isLoadingAd || isAdAvailable()) return

            isLoadingAd = true
            val request = AdRequest.Builder().build()
            AppOpenAd.load(
                activity,
                AD_UNIT_ID,
                request,
                object : AppOpenAd.AppOpenAdLoadCallback() {
                    override fun onAdLoaded(ad: AppOpenAd) {
                        appOpenAd = ad
                        isLoadingAd = false
                        loadTime = Date().time
                        Log.d("AdMobAppOpen", "Iklan App Open berhasil dimuat.")

                        // Tampilkan iklan jika Activity sedang aktif di layar
                        currentActivity?.let { activeActivity ->
                            showAdIfAvailable(activeActivity)
                        }
                    }

                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        isLoadingAd = false
                        Log.e("AdMobAppOpen", "Gagal memuat iklan: ${loadAdError.message} (Kode: ${loadAdError.code})")
                    }
                }
            )
        }

        private fun wasLoadTimeLessThanNHoursAgo(numHours: Long): Boolean {
            val dateDifference: Long = Date().time - loadTime
            val numMilliSecondsPerHour: Long = 3600000
            return dateDifference < numMilliSecondsPerHour * numHours
        }

        fun isAdAvailable(): Boolean {
            return appOpenAd != null && wasLoadTimeLessThanNHoursAgo(4)
        }

        fun showAdIfAvailable(activity: Activity) {
            if (!isShowingAd && isAdAvailable()) {
                appOpenAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        appOpenAd = null
                        isShowingAd = false
                        Log.d("AdMobAppOpen", "Iklan ditutup, memuat ulang...")
                        loadAd(activity)
                    }

                    override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                        appOpenAd = null
                        isShowingAd = false
                        Log.e("AdMobAppOpen", "Gagal menampilkan iklan: ${adError.message}")
                        loadAd(activity)
                    }

                    override fun onAdShowedFullScreenContent() {
                        isShowingAd = true
                        Log.d("AdMobAppOpen", "Iklan App Open sedang tampil.")
                    }
                }
                appOpenAd?.show(activity)
            } else {
                loadAd(activity)
            }
        }
    }
}
