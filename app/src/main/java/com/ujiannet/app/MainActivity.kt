package com.ujiannet.id

import android.annotation.TargetApi
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var adContainerView: FrameLayout

    private var currentNativeAd: NativeAd? = null
    private var rewardedAd: RewardedAd? = null
    private var interstitialAd: InterstitialAd? = null
    private var pendingDownloadUrl: String? = null

    // Variabel pemantau jaringan & pemulihan halaman otomatis
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var networkCallback: ConnectivityManager.NetworkCallback
    private var offlineDialog: AlertDialog? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var offlineRunnable: Runnable? = null
    private var isShowingOfflinePage = false
    private var lastValidUrl = "https://ujiannet-id.blogspot.com/?m=1"

    // Status Loading Iklan (Mencegah multiple request)
    private var isInterstitialLoading = false
    private var isRewardedLoading = false

    // Penghitung Klik & Jeda Waktu Klik Fisik
    private var postClickCount = 0
    private var lastClickTime: Long = 0

    private val HOME_URL = "https://ujiannet-id.blogspot.com/?m=1"
    private val NATIVE_AD_UNIT_ID = "ca-app-pub-6983364109428063/2345007949"
    private val REWARDED_AD_UNIT_ID = "ca-app-pub-6983364109428063/1834373062"
    private val INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-6983364109428063/6079819961"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        adContainerView = findViewById(R.id.adContainerView)
        webView = findViewById(R.id.webView)
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        // Sembunyikan wadah iklan sampai iklan native benar-benar siap ditampilkan
        adContainerView.visibility = View.GONE

        // Pengaturan WebView dengan Cache Aktif agar Halaman Cepat Terbuka di Semua Tipe HP
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            loadsImagesAutomatically = true
            setSupportZoom(true)
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(false)
        }

        // Izinkan cookie pihak ketiga untuk iframe & sesi login Apps Script
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(webView, true)
        }

        webView.webChromeClient = WebChromeClient()

        webView.webViewClient = object : WebViewClient() {

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                if (url != null && url.startsWith("http")) {
                    if (!url.contains("googleusercontent.com") && !url.contains("accounts.google.com")) {
                        lastValidUrl = url
                    }
                    isShowingOfflinePage = false

                    // Sembunyikan iklan native saat masuk ke lembar ujian CBT agar bebas gangguan & salah klik
                    val isExamPage = url.contains("script.google.com") || url.contains("script.googleusercontent.com")
                    if (isExamPage) {
                        adContainerView.visibility = View.GONE
                    } else if (currentNativeAd != null) {
                        adContainerView.visibility = View.VISIBLE
                    }
                }
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false

                // Abaikan subframe/iframe dan redirect internal (?m=1) agar tidak menahan navigasi
                if (!request.isForMainFrame) {
                    return false
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && request.isRedirect) {
                    return false
                }

                // 1. Halaman ujian Google Apps Script & sesi login Google berjalan murni di dalam aplikasi
                if (url.contains("script.google.com") ||
                    url.contains("script.googleusercontent.com") ||
                    url.contains("accounts.google.com")
                ) {
                    return false
                }

                // 2. Navigasi artikel/menu di blog utama
                if (url.contains("ujiannet-id.blogspot.com")) {
                    val currentTime = System.currentTimeMillis()

                    // Menghindari double tap / spam klik cepat (< 1 detik)
                    if (currentTime - lastClickTime > 1000) {
                        lastClickTime = currentTime
                        postClickCount++

                        if (postClickCount % 2 == 0) {
                            if (interstitialAd != null) {
                                view?.loadUrl(url)
                                showInterstitialAd {}
                                return true
                            } else {
                                loadInterstitialAd()
                            }
                        }
                    }
                    return false
                }

                // 3. Tautan eksternal (misal download file)
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    startActivity(intent)
                    return true
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                return false
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                if (request?.isForMainFrame == true && !isInternetAvailable()) {
                    showOfflinePage(view)
                }
            }

            // Membangun ulang Activity jika proses renderer WebView dimatikan sistem pada HP RAM kecil
            @TargetApi(Build.VERSION_CODES.O)
            override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                if (!isFinishing) {
                    recreate()
                }
                return true
            }
        }

        webView.addJavascriptInterface(WebAppInterface(), "AndroidApp")

        // Pemantau jaringan dengan jeda 2.5 detik agar tidak terganggu kedipan sinyal di kelas
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                offlineRunnable?.let { mainHandler.removeCallbacks(it) }
                runOnUiThread {
                    offlineDialog?.dismiss()
                    if (isShowingOfflinePage) {
                        isShowingOfflinePage = false
                        webView.loadUrl(lastValidUrl)
                    }
                }
            }

            override fun onLost(network: Network) {
                offlineRunnable?.let { mainHandler.removeCallbacks(it) }
                offlineRunnable = Runnable {
                    if (!isInternetAvailable() && !isFinishing) {
                        showOfflinePage(webView)
                        showOfflineDialog()
                    }
                }
                mainHandler.postDelayed(offlineRunnable!!, 2500)
            }
        }

        // 1. Prioritaskan memuat halaman web lebih dulu
        if (!isInternetAvailable()) {
            showOfflinePage(webView)
            showOfflineDialog()
        } else {
            webView.loadUrl(HOME_URL)
        }

        // 2. Muat SDK AdMob setelah WebView mulai merender halaman awal (jeda 800 ms)
        mainHandler.postDelayed({
            if (!isFinishing) {
                MobileAds.initialize(this@MainActivity) {
                    loadNativeAd()
                    loadRewardedAd()
                    loadInterstitialAd()
                }
            }
        }, 800)

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

    private fun showOfflinePage(view: WebView?) {
        isShowingOfflinePage = true
        adContainerView.visibility = View.GONE
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

    private fun showOfflineDialog() {
        if (offlineDialog?.isShowing == true || isFinishing) return

        val builder = AlertDialog.Builder(this)
        builder.setTitle("Koneksi Internet Terputus")
        builder.setMessage("Aplikasi ini memerlukan koneksi internet aktif untuk dapat digunakan.")
        builder.setCancelable(false)
        builder.setPositiveButton("Coba Lagi") { dialog, _ ->
            if (!isInternetAvailable()) {
                showOfflineDialog()
            } else {
                dialog.dismiss()
                isShowingOfflinePage = false
                webView.loadUrl(lastValidUrl)
            }
        }
        builder.setNegativeButton("Keluar") { _, _ -> finish() }

        offlineDialog = builder.create()
        offlineDialog?.setCanceledOnTouchOutside(false)
        offlineDialog?.show()
    }

    private fun isInternetAvailable(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    // LOGIKA NATIVE ADVANCED AD
    private fun loadNativeAd() {
        val adLoader = AdLoader.Builder(this, NATIVE_AD_UNIT_ID)
            .forNativeAd { nativeAd ->
                if (isDestroyed) {
                    nativeAd.destroy()
                    return@forNativeAd
                }
                currentNativeAd?.destroy()
                currentNativeAd = nativeAd

                // Gunakan adContainerView sebagai parent agar margin & pembatas XML tetap terjaga
                val adView = layoutInflater.inflate(R.layout.my_native_ad_view, adContainerView, false) as NativeAdView
                populateNativeAdView(nativeAd, adView)
                adContainerView.removeAllViews()
                adContainerView.addView(adView)

                val isExamPage = lastValidUrl.contains("script.google.com") || lastValidUrl.contains("script.googleusercontent.com")
                if (!isShowingOfflinePage && !isExamPage) {
                    adContainerView.visibility = View.VISIBLE
                }
            }
            .withAdListener(object : com.google.android.gms.ads.AdListener() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    adContainerView.removeAllViews()
                    adContainerView.visibility = View.GONE
                }
            })
            .build()

        adLoader.loadAd(AdRequest.Builder().build())
    }

    private fun populateNativeAdView(nativeAd: NativeAd, adView: NativeAdView) {
        adView.headlineView = adView.findViewById(R.id.ad_headline)
        adView.bodyView = adView.findViewById(R.id.ad_body)
        adView.callToActionView = adView.findViewById(R.id.ad_call_to_action)
        adView.iconView = adView.findViewById(R.id.ad_app_icon)

        (adView.headlineView as TextView).text = nativeAd.headline

        if (nativeAd.body == null) {
            adView.bodyView?.visibility = View.GONE
        } else {
            adView.bodyView?.visibility = View.VISIBLE
            (adView.bodyView as TextView).text = nativeAd.body
        }

        if (nativeAd.callToAction == null) {
            adView.callToActionView?.visibility = View.GONE
        } else {
            adView.callToActionView?.visibility = View.VISIBLE
            (adView.callToActionView as Button).text = nativeAd.callToAction
        }

        if (nativeAd.icon == null) {
            adView.iconView?.visibility = View.GONE
        } else {
            (adView.iconView as ImageView).setImageDrawable(nativeAd.icon?.drawable)
            adView.iconView?.visibility = View.VISIBLE
        }

        adView.setNativeAd(nativeAd)
    }

    // LOGIKA IKLAN INTERSTITIAL
    private fun loadInterstitialAd() {
        if (isInterstitialLoading || interstitialAd != null) return
        isInterstitialLoading = true

        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(
            this,
            INTERSTITIAL_AD_UNIT_ID,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    isInterstitialLoading = false
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    interstitialAd = null
                    isInterstitialLoading = false
                }
            }
        )
    }

    private fun showInterstitialAd(onAdDismissed: () -> Unit) {
        val ad = interstitialAd
        if (ad != null) {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    interstitialAd = null
                    loadInterstitialAd()
                    onAdDismissed()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    interstitialAd = null
                    loadInterstitialAd()
                    onAdDismissed()
                }
            }
            ad.show(this@MainActivity)
        } else {
            loadInterstitialAd()
            onAdDismissed()
        }
    }

    // INTERFACE JAVASCRIPT
    inner class WebAppInterface {

        @JavascriptInterface
        fun showInterstitial() {
            runOnUiThread {
                if (interstitialAd != null) {
                    showInterstitialAd {}
                } else {
                    loadInterstitialAd()
                }
            }
        }

        @JavascriptInterface
        fun panggilIklanInterstisial() {
            runOnUiThread {
                if (interstitialAd != null) {
                    showInterstitialAd {}
                } else {
                    loadInterstitialAd()
                }
            }
        }

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

    // LOGIKA REWARDED AD
    private fun loadRewardedAd() {
        if (isRewardedLoading || rewardedAd != null) return
        isRewardedLoading = true

        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(
            this@MainActivity,
            REWARDED_AD_UNIT_ID,
            adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    isRewardedLoading = false
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    rewardedAd = null
                    isRewardedLoading = false
                }
            }
        )
    }

    private fun triggerRewardedAdBeforeDownload() {
        if (rewardedAd != null) {
            showRewardedAd()
        } else if (!isRewardedLoading) {
            Toast.makeText(this@MainActivity, "Memuat iklan, mohon tunggu...", Toast.LENGTH_SHORT).show()
            isRewardedLoading = true
            val adRequest = AdRequest.Builder().build()
            RewardedAd.load(
                this@MainActivity,
                REWARDED_AD_UNIT_ID,
                adRequest,
                object : RewardedAdLoadCallback() {
                    override fun onAdLoaded(ad: RewardedAd) {
                        rewardedAd = ad
                        isRewardedLoading = false
                        showRewardedAd()
                    }

                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        rewardedAd = null
                        isRewardedLoading = false
                        Toast.makeText(this@MainActivity, "Gagal memuat iklan, langsung membuka file...", Toast.LENGTH_SHORT).show()
                        pendingDownloadUrl?.let { executeOpenLink(it) }
                    }
                }
            )
        } else {
            Toast.makeText(this@MainActivity, "Sedang memuat iklan, coba beberapa saat lagi...", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showRewardedAd() {
        var hasEarnedReward = false
        val ad = rewardedAd

        if (ad != null) {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
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
                    loadRewardedAd()
                    pendingDownloadUrl?.let { executeOpenLink(it) }
                    notifyJsRewardEarned()
                }

                override fun onAdShowedFullScreenContent() {}
            }

            ad.show(this@MainActivity) { _ ->
                hasEarnedReward = true
            }
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
        webView.onPause()
        offlineRunnable?.let { mainHandler.removeCallbacks(it) }
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
        try {
            val networkRequest = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (!isInternetAvailable()) {
            showOfflinePage(webView)
            showOfflineDialog()
        } else {
            offlineDialog?.dismiss()
            if (isShowingOfflinePage) {
                isShowingOfflinePage = false
                webView.loadUrl(lastValidUrl)
            }
        }
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        adContainerView.removeAllViews()
        currentNativeAd?.destroy()
        webView.destroy()
        super.onDestroy()
    }
}
