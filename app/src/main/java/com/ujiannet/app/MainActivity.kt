package com.ujiannet.app

import android.app.DownloadManager
import android.app.ProgressDialog
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.URLUtil
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var adView: AdView

    private var rewardedAd: RewardedAd? = null
    private var progressDialog: ProgressDialog? = null

    private var pendingDownloadUrl: String? = null
    private var pendingUserAgent: String? = null
    private var pendingContentDisposition: String? = null
    private var pendingMimeType: String? = null

    private val REWARDED_AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        MobileAds.initialize(this@MainActivity) {}

        adView = findViewById(R.id.adView)
        val adRequest = AdRequest.Builder().build()
        adView.loadAd(adRequest)

        loadRewardedAd()

        progressDialog = ProgressDialog(this@MainActivity).apply {
            setMessage("Memuat iklan reward, mohon tunggu...")
            setCancelable(false)
        }

        webView = findViewById(R.id.webView)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.webViewClient = WebViewClient()

        // 1. HUBUNGKAN JAVASCRIPT BLOG DENGAN KOTLIN
        webView.addJavascriptInterface(WebAppInterface(), "AndroidApp")

        webView.setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
            pendingDownloadUrl = url
            pendingUserAgent = userAgent
            pendingContentDisposition = contentDisposition
            pendingMimeType = mimetype

            triggerRewardedAdBeforeDownload()
        }

        webView.loadUrl("https://ujiannet-id.com")
    }

    // 2. KELAS INTERFACE JAVASCRIPT
    inner class WebAppInterface {
        @JavascriptInterface
        fun triggerRewardedAd() {
            runOnUiThread {
                triggerRewardedAdBeforeDownload()
            }
        }
    }

    private fun loadRewardedAd() {
        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(
            this@MainActivity,
            REWARDED_AD_UNIT_ID,
            adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    rewardedAd = null
                }
            }
        )
    }

    private fun triggerRewardedAdBeforeDownload() {
        if (rewardedAd != null) {
            showRewardedAd()
        } else {
            progressDialog?.show()
            val adRequest = AdRequest.Builder().build()
            RewardedAd.load(
                this@MainActivity,
                REWARDED_AD_UNIT_ID,
                adRequest,
                object : RewardedAdLoadCallback() {
                    override fun onAdLoaded(ad: RewardedAd) {
                        progressDialog?.dismiss()
                        rewardedAd = ad
                        showRewardedAd()
                    }

                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        progressDialog?.dismiss()
                        rewardedAd = null
                        Toast.makeText(
                            this@MainActivity,
                            "Gagal memuat iklan, melanjutkan ke file...",
                            Toast.LENGTH_SHORT
                        ).show()
                        notifyJsRewardEarned()
                    }
                }
            )
        }
    }

    private fun showRewardedAd() {
        rewardedAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                rewardedAd = null
                loadRewardedAd()
                // Panggil fungsi JS di blog untuk membuka file setelah iklan selesai
                notifyJsRewardEarned()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                rewardedAd = null
                notifyJsRewardEarned()
            }

            override fun onAdShowedFullScreenContent() {}
        }

        rewardedAd?.show(this@MainActivity) {
            Toast.makeText(this@MainActivity, "Iklan selesai! Membuka file...", Toast.LENGTH_SHORT).show()
        }
    }

    // 3. FUNGSI UNTUK MEMANGGIL BALIK JS DI BLOG (onRewardEarned)
    private fun notifyJsRewardEarned() {
        webView.post {
            webView.evaluateJavascript("javascript:onRewardEarned();", null)
        }
    }

    private fun startFileDownload() {
        val url = pendingDownloadUrl ?: return
        try {
            val request = DownloadManager.Request(Uri.parse(url))
            request.setMimeType(pendingMimeType)

            val cookies = CookieManager.getInstance().getCookie(url)
            request.addRequestHeader("cookie", cookies)
            request.addRequestHeader("User-Agent", pendingUserAgent)

            val fileName = URLUtil.guessFileName(url, pendingContentDisposition, pendingMimeType)
            request.setTitle(fileName)
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)

            val dm = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(request)

            Toast.makeText(applicationContext, "Mengunduh file: $fileName", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(applicationContext, "Gagal mengunduh file.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
