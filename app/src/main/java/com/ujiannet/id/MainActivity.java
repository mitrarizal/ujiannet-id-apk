package com.ujiannet.id;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.gms.ads.identifier.AdvertisingIdClient;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final String BLOGSPOT_URL = "https://ujiannet-id.blogspot.com/?m=1";

    private LinearLayout rootContainer;
    private WebView webView;
    private View adSectionDivider;
    private FrameLayout nativeAdContainer;
    private AdManager adManager;

    private final ExecutorService bgExecutor = Executors.newSingleThreadExecutor();
    private volatile boolean isInExamSession = false;
    private volatile boolean isAdIdFetched = false;
    private volatile String cachedAdId = "";
    private volatile boolean isLimitAdTracking = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        rootContainer = findViewById(R.id.root_container);
        webView = findViewById(R.id.main_webview);
        adSectionDivider = findViewById(R.id.ad_section_divider);
        nativeAdContainer = findViewById(R.id.native_ad_container);

        setupEdgeToEdgeInsets();

        adManager = new AdManager(this, webView, nativeAdContainer, adSectionDivider);
        fetchAdvertisingIdAsync();

        setupWebView();
        setupBackNavigation();

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
        } else {
            webView.loadUrl(BLOGSPOT_URL);
        }
    }

    /**
     * Mengatur jarak aman system bars + IME serta memastikan ikon status bar & navigasi sistem terlihat jelas.
     */
    private void setupEdgeToEdgeInsets() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat insetsController =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (insetsController != null) {
            insetsController.setAppearanceLightStatusBars(true);
            insetsController.setAppearanceLightNavigationBars(true);
        }

        View targetView = rootContainer != null ? rootContainer : getWindow().getDecorView();
        ViewCompat.setOnApplyWindowInsetsListener(targetView, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime()
            );
            v.setPadding(insets.left, insets.top, insets.right, insets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
    }

    private void updateExamSessionState(boolean active) {
        this.isInExamSession = active;
        if (adManager != null) {
            adManager.setExamSessionActive(active);
        }
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            if (active) {
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            } else {
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
        });
    }

    private boolean isUrlAllowedForNativeAd(String url) {
        if (url == null || url.isEmpty() || url.startsWith("data:") || url.startsWith("file:")) {
            return false;
        }
        try {
            Uri uri = Uri.parse(url);
            String path = uri.getPath();
            if (path != null) {
                String lowerPath = path.toLowerCase(Locale.ROOT);
                // Jangan tampilkan iklan pada halaman kebijakan (/p/...) atau halaman pencarian (/search)
                if (lowerPath.startsWith("/p/") || lowerPath.startsWith("/search")) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void fetchAdvertisingIdAsync() {
        bgExecutor.execute(() -> {
            try {
                AdvertisingIdClient.Info info = AdvertisingIdClient.getAdvertisingIdInfo(getApplicationContext());
                if (info != null) {
                    cachedAdId = info.getId() != null ? info.getId() : "";
                    isLimitAdTracking = info.isLimitAdTrackingEnabled();
                }
            } catch (Exception ignored) {
                cachedAdId = "";
                isLimitAdTracking = false;
            } finally {
                isAdIdFetched = true;
                notifyJsAdIdSynced();
            }
        });
    }

    private void notifyJsAdIdSynced() {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed() || webView == null) return;
            String safeId = cachedAdId.replace("\\", "\\\\").replace("'", "\\'");
            boolean consentReady = adManager != null && adManager.isConsentCompleted();
            String script = "if(window.onAdIdReady){window.onAdIdReady('"
                    + safeId + "'," + isLimitAdTracking + "," + consentReady + ");}";
            webView.evaluateJavascript(script, null);
        });
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setSupportMultipleWindows(false);

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.setBackgroundColor(Color.parseColor("#FFFFFF"));
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        WebAppAdMobInterface jsBridge = new WebAppAdMobInterface();
        webView.addJavascriptInterface(jsBridge, "AndroidAdMob");
        webView.addJavascriptInterface(jsBridge, "AndroidApp");

        webView.setDownloadListener((url, userAgent, contentDisposition, mimetype, contentLength) -> {
            if (url != null && !url.isEmpty()) {
                openExternalUri(Uri.parse(url));
            }
        });

        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String scheme = uri.getScheme();
                String host = uri.getHost();

                if (scheme != null && (scheme.equals("http") || scheme.equals("https"))) {
                    if (host != null && (host.contains("ujiannet-id.blogspot.com")
                            || host.contains("blogspot.com")
                            || host.contains("blogger.com")
                            || host.contains("script.google.com")
                            || host.contains("googleusercontent.com")
                            || host.contains("accounts.google.com"))) {
                        return false;
                    }
                    openExternalUri(uri);
                    return true;
                }

                openExternalUri(uri);
                return true;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                adManager.setPagePolicyAllowed(isUrlAllowedForNativeAd(url));
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (url != null && !url.startsWith("data:") && !url.startsWith("file:")) {
                    adManager.setPagePolicyAllowed(isUrlAllowedForNativeAd(url));
                    adManager.onContentReady();
                    view.evaluateJavascript(
                            "window.dispatchEvent(new Event('AndroidAdMobReady'));"
                                    + "if(window.onAdMobBridgeReady){window.onAdMobBridgeReady();}",
                            null
                    );
                    if (isAdIdFetched) {
                        notifyJsAdIdSynced();
                    }
                } else {
                    adManager.setPagePolicyAllowed(false);
                }
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request != null && request.isForMainFrame()) {
                    adManager.setPagePolicyAllowed(false);
                    showOfflineFallbackPage();
                }
            }
        });
    }

    private void openExternalUri(@NonNull Uri uri) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            startActivity(intent);
        } catch (Exception ignored) {
        }
    }

    private void showOfflineFallbackPage() {
        adManager.setPagePolicyAllowed(false);
        String offlineHtml = "<!DOCTYPE html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<style>body{font-family:sans-serif;background:#F8FAFC;color:#0F172A;display:flex;flex-direction:column;"
                + "align-items:center;justify-content:center;height:100vh;margin:0;padding:24px;text-align:center;}"
                + "img{max-width:180px;margin-bottom:16px;}h2{font-size:18px;margin:0 0 8px;}"
                + "p{font-size:14px;color:#475569;margin:0 0 20px;}"
                + "button{background:#0284C7;color:#fff;border:none;border-radius:10px;padding:12px 24px;"
                + "font-size:14px;font-weight:bold;}</style></head><body>"
                + "<img src='file:///android_asset/no_internet.png' alt='Offline' onerror=\"this.style.display='none'\"/>"
                + "<h2>Koneksi Internet Terputus</h2>"
                + "<p>Pastikan paket data atau Wi-Fi aktif untuk mengakses UjianNet ID.</p>"
                + "<button onclick=\"window.location.href='" + BLOGSPOT_URL + "'\">Coba Muat Ulang</button>"
                + "</body></html>";
        webView.loadDataWithBaseURL("file:///android_asset/", offlineHtml, "text/html", "UTF-8", null);
    }

    private void setupBackNavigation() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (isInExamSession && webView != null) {
                    webView.evaluateJavascript(
                            "if(typeof confirmQuitExam==='function'){confirmQuitExam();}"
                                    + "else if(typeof confirmQuitLevel==='function'){confirmQuitLevel();}",
                            null
                    );
                } else if (webView != null && webView.canGoBack()) {
                    webView.goBack();
                } else {
                    finish();
                }
            }
        });
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (webView != null) {
            webView.saveState(outState);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) webView.onResume();
    }

    @Override
    protected void onPause() {
        if (webView != null) webView.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        bgExecutor.shutdownNow();
        if (adManager != null) adManager.onDestroy();
        if (webView != null) {
            webView.removeAllViews();
            webView.destroy();
        }
        super.onDestroy();
    }

    private class WebAppAdMobInterface {

        @JavascriptInterface
        public void onContentLoaded() {
            adManager.onContentReady();
        }

        @JavascriptInterface
        public void onGameContentReady() {
            adManager.onContentReady();
        }

        @JavascriptInterface
        public void onAppOpenReady(String pageType, boolean eligible) {
            updateExamSessionState(!eligible);
            adManager.onContentReady();
        }

        @JavascriptInterface
        public void notifyPageReady(String pageType) {
            adManager.onContentReady();
        }

        @JavascriptInterface
        public void setExamState(boolean active) {
            updateExamSessionState(active);
        }

        @JavascriptInterface
        public void setGameplayState(boolean active) {
            updateExamSessionState(active);
        }

        @JavascriptInterface
        public void setAppOpenEligible(boolean eligible) {
            updateExamSessionState(!eligible);
        }

        @JavascriptInterface
        public void setNativeAdVisible(boolean visible) {
            adManager.setNativeAdVisible(visible);
        }

        @JavascriptInterface
        public boolean isInterstitialReady() {
            return adManager.isInterstitialAdReady();
        }

        @JavascriptInterface
        public boolean isInterstitialLoaded() {
            return adManager.isInterstitialAdReady();
        }

        @JavascriptInterface
        public void preloadInterstitial() {
            runOnUiThread(() -> adManager.loadInterstitialAd());
        }

        @JavascriptInterface
        public void preloadAppOpenAd() {
            runOnUiThread(() -> adManager.loadAppOpenAd());
        }

        @JavascriptInterface
        public void showInterstitial(String placement) {
            adManager.showInterstitialAd(placement != null ? placement : "article_transition");
        }

        @JavascriptInterface
        public void panggilIklanInterstisial() {
            adManager.showInterstitialAd("article_transition");
        }

        @JavascriptInterface
        public void requestAdId() {
            if (isAdIdFetched) {
                notifyJsAdIdSynced();
            } else {
                fetchAdvertisingIdAsync();
            }
        }

        @JavascriptInterface
        public boolean isAdIdReady() {
            return isAdIdFetched;
        }

        @JavascriptInterface
        public String getAdId() {
            return cachedAdId;
        }

        @JavascriptInterface
        public String getAdvertisingId() {
            return cachedAdId;
        }

        @JavascriptInterface
        public boolean isLimitAdTrackingEnabled() {
            return isLimitAdTracking;
        }

        @JavascriptInterface
        public boolean getLimitAdTracking() {
            return isLimitAdTracking;
        }

        @JavascriptInterface
        public boolean isConsentReady() {
            return adManager.isConsentCompleted();
        }

        @JavascriptInterface
        public int getBannerHeightPx() {
            return 0;
        }

        @JavascriptInterface
        public int getBannerHeightDp() {
            return 0;
        }

        @JavascriptInterface
        public boolean isBannerOverlay() {
            return false;
        }

        @JavascriptInterface
        public void showRewardedAd(String rewardType) {
        }

        @JavascriptInterface
        public void panggilIklanRewarded() {
        }
    }
}
