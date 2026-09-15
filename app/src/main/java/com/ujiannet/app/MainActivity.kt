package com.ujiannet.app

import android.app.DownloadManager
import android.app.ProgressDialog
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.webkit.CookieManager
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

    // Variable untuk menyimpan URL file yang akan diunduh dari WebView
    private var pendingDownloadUrl: String? = null
    private var pendingUserAgent: String? = null
    private var pendingContentDisposition: String? = null
    private var pendingMimeType: String? = null

    // ID UNIT IKLAN REWARD
    private val REWARDED_AD_UNIT_ID = "ca-app-pub-6983364109428063/5725646492"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate()
        setContentView(R.layout.activity_main)

        // 1. Inisialisasi Mobile Ads SDK
        MobileAds.initialize(this) {}

        // 2. Inisialisasi Iklan Banner (AdView)
        adView = findViewById(R.id.adView)
        val adRequest = AdRequest.Builder().build()
        adView.loadAd(adRequest)

        // 3. Preload Iklan Reward
        loadRewardedAd()

        // 4. Inisialisasi Dialog Loading saat Iklan Reward diproses
        progressDialog = ProgressDialog(this).apply {
            setMessage("Memuat iklan reward, mohon tunggu...")
            setCancelable(false)
        }

        // 5. Inisialisasi WebView
        webView = findViewById(R.id.webView)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.webViewClient = WebViewClient()

        // 6. DETEKSI UNDUHAN DI WEBVIEW:
        // Ketika pengguna menekan tombol unduh file di website, iklan reward akan dimunculkan lebih dulu
        webView.setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
            pendingDownloadUrl = url
            pendingUserAgent = userAgent
            pendingContentDisposition = contentDisposition
            pendingMimeType = mimetype

            triggerRewardedAdBeforeDownload()
        }

        // Buka domain utama Anda
        webView.loadUrl("https://ujiannet-id.com")
    }

    /** Memuat Iklan Reward di Latar Belakang */
    private fun loadRewardedAd() {
        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(
            this,
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

    /** Memicu Iklan Reward Sebelum Pengunduhan */
    private fun triggerRewardedAdBeforeDownload() {
        if (rewardedAd != null) {
            showRewardedAd()
        } else {
            progressDialog?.show()
            val adRequest = AdRequest.Builder().build()
            RewardedAd.load(
                this,
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
                        // Jika iklan gagal dimuat, tetap jalankan pengunduhan file
                        startFileDownload()
                    }
                }
            )
        }
    }

    /** Menampilkan Iklan Reward */
    private fun showRewardedAd() {
        rewardedAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                rewardedAd = null
                loadRewardedAd() // Muat ulang untuk pengunduhan berikutnya
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                rewardedAd = null
                startFileDownload()
            }

            override fun onAdShowedFullScreenContent() {}
        }

        rewardedAd?.show(this) { rewardItem ->
            Toast.makeText(this, "Iklan selesai! Memulai pengunduhan...", Toast.LENGTH_SHORT).show()
            startFileDownload()
        }
    }

    /** Eksekusi Pengunduhan File Asli Melalui Android DownloadManager */
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

    /** Fitur Tombol Kembali (Back Navigation) WebView */
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
