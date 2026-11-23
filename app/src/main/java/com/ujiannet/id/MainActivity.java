package com.ujiannet.id;

import android.annotation.SuppressLint;
import android.content.Intent;
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
import android.widget.RelativeLayout;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity {

    private static final String BLOGSPOT_URL = "https://ujiannet-id.blogspot.com/?m=1";

    private RelativeLayout rootContainer;
    private WebView webView;
    private FrameLayout nativeAdContainer;
    private AdManager adManager;
    private boolean isInExamSession = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        setupEdgeToEdgeInsets();

        rootContainer = findViewById(R.id.root_container);
        webView = findViewById(R.id.main_webview);
        nativeAdContainer = findViewById(R.id.native_ad_container);

        adManager = new AdManager(this, webView, nativeAdContainer);

        setupWebView();
        setupBackNavigation();

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
        } else {
            webView.loadUrl(BLOGSPOT_URL);
        }
    }

    /**
     * Wajib untuk Android 15/16 (SDK 36) agar tombol Native Ad & WebView tidak tertutup status/navigation bar.
     */
    private void setupEdgeToEdgeInsets() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        View decorView = getWindow().getDecorView();
        ViewCompat.setOnApplyWindowInsetsListener(decorView, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime()
            );
            v.setPadding(insets.left, insets.top, insets.right, insets.bottom);
            return WindowInsetsCompat.CONSUMED;
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

        webView.setBackgroundColor(Color.parseColor("#F8FAFC"));
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        WebAppAdMobInterface jsBridge = new WebAppAdMobInterface();
        webView.addJavascriptInterface(jsBridge, "AndroidAdMob");
        webView.addJavascriptInterface(jsBridge, "AndroidApp");

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
                    // Buka tautan eksternal di browser/aplikasi luar tanpa merusak sesi WebView
                    openExternalUri(uri);
                    return true;
                }

                // Tangani skema whatsapp:, mailto:, tel:, intent:
                openExternalUri(uri);
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (url != null && !url.startsWith("data:")) {
                    adManager.onContentReady();
                }
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request != null && request.isForMainFrame()) {
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
            // Abaikan secara aman jika perangkat tidak memiliki aplikasi penangan skema tersebut
        }
    }

    private void showOfflineFallbackPage() {
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
        if (adManager != null) adManager.onDestroy();
        if (webView != null) {
            webView.removeAllViews();
            webView.destroy();
        }
        super.onDestroy();
    }

    /**
     * Jembatan JavaScript antara https://ujiannet-id.blogspot.com/?m=1 dan Android Native.
     */
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
        public void setExamState(boolean active) {
            isInExamSession = active;
            adManager.setExamSessionActive(active);
        }

        @JavascriptInterface
        public void setGameplayState(boolean active) {
            isInExamSession = active;
            adManager.setExamSessionActive(active);
        }

        @JavascriptInterface
        public void setNativeAdVisible(boolean visible) {
            adManager.setNativeAdVisible(visible);
        }

        @JavascriptInterface
        public void showInterstitial(String placement) {
            adManager.showInterstitialAd(placement != null ? placement : "exam_transition");
        }

        @JavascriptInterface
        public void panggilIklanInterstisial() {
            adManager.showInterstitialAd("exam_transition");
        }

        // Stub kompatibilitas mundur agar skrip lama di Blogspot tidak melempar error JS
        @JavascriptInterface
        public int getBannerHeightPx() {
            return 0;
        }

        @JavascriptInterface
        public void showRewardedAd(String rewardType) {
            // Tidak digunakan (fokus hanya App Open, Native, dan Interstisial)
        }

        @JavascriptInterface
        public void panggilIklanRewarded() {
            // Tidak digunakan
        }
    }
}
