package com.ujiannet.app

import android.app.Activity
import android.app.Application
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

class MyApplication : Application(), Application.ActivityLifecycleCallbacks, DefaultLifecycleObserver {

    private var appOpenAdManager: AppOpenAdManager? = null
    private var currentActivity: Activity? = null

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(this)
        
        MobileAds.initialize(this) {}
        
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        appOpenAdManager = AppOpenAdManager()
    }

    override fun onStart(owner: LifecycleOwner) {
        // Hapus super.onStart(owner) untuk menghindari bentrok supertype
        currentActivity?.let {
            appOpenAdManager?.showAdIfAvailable(it)
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityStarted(activity: Activity) { currentActivity = activity }
    override fun onActivityResumed(activity: Activity) { currentActivity = activity }
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) { currentActivity = null }

    private inner class AppOpenAdManager {
        private var appOpenAd: AppOpenAd? = null
        private var isLoadingAd = false
        private var isShowingAd = false
        private var loadTime: Long = 0

        private val AD_UNIT_ID = "ca-app-pub-6983364109428063/4604136517"

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
                        loadAd(activity)
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
