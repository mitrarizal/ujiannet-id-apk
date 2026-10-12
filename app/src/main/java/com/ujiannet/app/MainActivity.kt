package com.ujiannet.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowInsets
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
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
    private var pendingDownloadUrl: String? = null

    private val REWARDED_AD_UNIT_ID = "ca-app-pub-6983364109428063/5725646492"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        MobileAds.initialize(this@MainActivity) {}

        // Inisialisasi AdView dengan Anchored Adaptive Banner Size
        adView = findViewById(R.id.adView)
        adView.setAdSize(getAdSize()) // <--- Menyetel ukuran banner adaptif
        val adRequest = AdRequest.Builder().build()
        adView.loadAd(adRequest)

        loadRewardedAd()

        webView = findViewById(R.id.webView)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true

        // Custom WebViewClient untuk menangani kondisi offline/error
        webView.webViewClient = object : WebViewClient() {
            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                if (request?.isForMainFrame == true) {
                    val html = """
                        <!DOCTYPE html>
                        <html>
                        <head>
                            <meta name="viewport" content="width=device-width, initial-scale=1.0">
                            <style>
                                body { display: flex; justify-content: center; align-items: center; height: 100vh; margin: 0; background-color: #ffffff; }
                                img { max-width: 80%; height: auto; }
                            </style>
                        </head>
                        <body>
                            <img src="file:///android_asset/no_internet.png">
                        </body>
                        </html>
                    """.trimIndent()

                    view?.loadDataWithBaseURL("file:///android_asset/", html, "text/html", "UTF-8", null)
                }
            }
        }

        // Daftarkan Interface JavaScript
        webView.addJavascriptInterface(WebAppInterface(), "AndroidApp")

        webView.loadUrl("https://ujiannet-id.com")

        // Penanganan tombol kembali modern (Android 13+)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    finish()
                }
            }
        })
    }

    // Fungsi untuk menghitung lebar layar HP secara presisi untuk Adaptive Banner
    private fun getAdSize(): AdSize {
        val displayMetrics = resources.displayMetrics
        val adWidthPixels = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics = windowManager.currentWindowMetrics
            val insets = windowMetrics.windowInsets
                .getInsetsIgnoringVisibility(WindowInsets.Type.systemBars())
            windowMetrics.bounds.width() - insets.left - insets.right
        } else {
            @Suppress("DEPRECATION")
            displayMetrics.widthPixels
        }

        val density = displayMetrics.density
        val adWidth = (adWidthPixels / density).toInt()

        return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(this, adWidth)
    }

    inner class WebAppInterface {
        @JavascriptInterface
        fun startDownloadProcess(url: String) {
            runOnUiThread {
                pendingDownloadUrl = url
                triggerRewardedAdBeforeDownload()
            }
        }

        @JavascriptInterface
        fun openDownloadLink(url: String) {
            runOnUiThread {
                executeOpenLink(url)
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
            Toast.makeText(this@MainActivity, "Memuat iklan, mohon tunggu...", Toast.LENGTH_SHORT).show()
            val adRequest = AdRequest.Builder().build()
            RewardedAd.load(
                this@MainActivity,
                REWARDED_AD_UNIT_ID,
                adRequest,
                object : RewardedAdLoadCallback() {
                    override fun onAdLoaded(ad: RewardedAd) {
                        rewardedAd = ad
                        showRewardedAd()
                    }

                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        rewardedAd = null
                        Toast.makeText(this@MainActivity, "Gagal memuat iklan, langsung membuka file...", Toast.LENGTH_SHORT).show()
                        pendingDownloadUrl?.let { executeOpenLink(it) }
                    }
                }
            )
        }
    }

    private fun showRewardedAd() {
        var hasEarnedReward = false

        rewardedAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                rewardedAd = null
                loadRewardedAd()

                if (hasEarnedReward) {
                    pendingDownloadUrl?.let { executeOpenLink(it) }
                    notifyJsRewardEarned()
                } else {
                    Toast.makeText(this@MainActivity, "Tonton iklan sampai selesai untuk mengunduh.", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                rewardedAd = null
                pendingDownloadUrl?.let { executeOpenLink(it) }
                notifyJsRewardEarned()
            }

            override fun onAdShowedFullScreenContent() {}
        }

        rewardedAd?.show(this@MainActivity) { rewardItem ->
            hasEarnedReward = true
        }
    }

    private fun notifyJsRewardEarned() {
        webView.post {
            webView.evaluateJavascript("javascript:onRewardEarned();", null)
        }
    }

    private fun executeOpenLink(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
            pendingDownloadUrl = null
        } catch (e: Exception) {
            Toast.makeText(applicationContext, "Gagal membuka tautan file.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onPause() {
        adView.pause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        adView.resume()
    }

    override fun onDestroy() {
        adView.destroy()
        webView.destroy()
        super.onDestroy()
    }
}
