package com.ujiannet.id;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private static final String BLOG_URL = "https://ujiannet-id.blogspot.com/?m=1";

    private WebView webView;
    private AdManager adManager;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.game_webview);
        FrameLayout bannerContainer = findViewById(R.id.banner_ad_container);

        // 1. Inisialisasi AdManager lebih dulu agar App Open Ad langsung diunduh
        adManager = new AdManager(this, webView, bannerContainer);

        // 2. Konfigurasi WebView & JavascriptInterface
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);

        webView.addJavascriptInterface(new AdMobJsBridge(), "AndroidAdMob");

        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase() : "";
                String host = uri.getHost() != null ? uri.getHost().toLowerCase() : "";

                // Buka WhatsApp / Telegram / Intent eksternal di aplikasi masing-masing
                if (!scheme.equals("http") && !scheme.equals("https")) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, uri));
                    } catch (Exception ignored) {}
                    return true;
                }

                // Tautan unduhan file langsung buka di browser/download manager agar tidak macet
                String path = uri.getPath() != null ? uri.getPath().toLowerCase() : "";
                if (path.matches(".*\\.(pdf|doc|docx|xls|xlsx|ppt|pptx|zip|rar)$")
                        || host.contains("drive.google.com")) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, uri));
                    } catch (Exception ignored) {}
                    return true;
                }

                return false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                adManager.onContentReady();
            }
        });

        // 3. Penanganan tombol Back Android
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (webView != null && webView.canGoBack()) {
                    webView.goBack();
                } else {
                    finish();
                }
            }
        });

        // 4. Muat halaman utama UjianNet-ID
        webView.loadUrl(BLOG_URL);
    }

    private class AdMobJsBridge {
        @JavascriptInterface
        public void onContentLoaded() {
            adManager.onContentReady();
        }

        @JavascriptInterface
        public void setGameplayState(boolean active) {
            adManager.setExamActive(active);
        }

        @JavascriptInterface
        public int getBannerHeightDp() {
            return adManager.getBannerHeightDp();
        }

        @JavascriptInterface
        public boolean isAdIdReady() {
            return adManager.isAdIdReady();
        }

        @JavascriptInterface
        public void showInterstitial(String placement) {
            adManager.showInterstitialAd(placement != null ? placement : "page_transition");
        }

        @JavascriptInterface
        public void showRewardedAd(String rewardType) {
            adManager.showRewardedAd(rewardType != null ? rewardType : "unduh_dokumen");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) webView.onResume();
        if (adManager != null) adManager.onResume();
    }

    @Override
    protected void onPause() {
        if (adManager != null) adManager.onPause();
        if (webView != null) webView.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (adManager != null) adManager.onDestroy();
        if (webView != null) webView.destroy();
        super.onDestroy();
    }
}
