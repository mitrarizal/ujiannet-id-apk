package com.ujiannet.id;

import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.URLUtil;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

public class MainActivity extends AppCompatActivity {

    private static final String BLOGGER_HOME_URL = "https://ujiannet-id.blogspot.com/?m=1";

    private WebView webView;
    private FrameLayout bannerContainer;
    private AdManager adManager;
    private boolean isInExamSession = false;
    private boolean isShowingOfflinePage = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        enableImmersiveFullscreen();

        webView = findViewById(R.id.game_webview);
        bannerContainer = findViewById(R.id.banner_ad_container);

        applyEdgeToEdgeInsets();

        adManager = new AdManager(this, webView, bannerContainer);

        setupWebView();
        setupBackNavigation();

        if (!isNetworkAvailable()) {
            showOfflinePage();
        } else if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
        } else {
            webView.loadUrl(BLOGGER_HOME_URL);
        }
    }

    private void applyEdgeToEdgeInsets() {
        View rootView = findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, windowInsets) -> {
            Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets displayCutout = windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout());

            int leftInset = Math.max(systemBars.left, displayCutout.left);
            int topInset = Math.max(systemBars.top, displayCutout.top);
            int rightInset = Math.max(systemBars.right, displayCutout.right);
            int bottomInset = systemBars.bottom;

            if (webView != null) {
                webView.setPadding(leftInset, topInset, rightInset, 0);
            }

            if (bannerContainer != null) {
                bannerContainer.setPadding(leftInset, 0, rightInset, bottomInset);
            }

            return WindowInsetsCompat.CONSUMED;
        });
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        Network network = cm.getActiveNetwork();
        if (network == null) return false;
        NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
        return capabilities != null && (
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        );
    }

    private void showOfflinePage() {
        isShowingOfflinePage = true;
        String offlineHtml = "<!DOCTYPE html><html><head><meta name='viewport' content='width=device-width,initial-scale=1.0'>"
                + "<style>"
                + "body{margin:0;padding:24px;background:#0F172A;color:#F8FAFC;font-family:sans-serif;display:flex;flex-direction:column;align-items:center;justify-content:center;min-height:90vh;text-align:center;}"
                + "img{max-width:260px;width:75%;border-radius:16px;margin-bottom:20px;box-shadow:0 10px 25px rgba(0,0,0,0.4);}"
                + "h2{margin:0 0 8px;font-size:20px;}"
                + "p{margin:0 0 24px;font-size:14px;color:#94A3B8;line-height:1.5;}"
                + "button{background:#0284C7;color:#fff;border:none;padding:12px 28px;border-radius:10px;font-size:15px;font-weight:600;cursor:pointer;}"
                + "</style></head><body>"
                + "<img src='file:///android_asset/no_internet.png' alt='Tidak Ada Internet'/>"
                + "<h2>Koneksi Internet Terputus</h2>"
                + "<p>Periksa jaringan Wi-Fi atau data seluler Anda untuk mengakses Ujiannet ID.</p>"
                + "<button onclick='AndroidAdMob.retryConnection()'>Coba Lagi</button>"
                + "</body></html>";
        webView.loadDataWithBaseURL("file:///android_asset/", offlineHtml, "text/html", "UTF-8", null);
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.setBackgroundColor(Color.parseColor("#F0F9FF"));
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);

        WebAppAdMobInterface jsBridge = new WebAppAdMobInterface();
        webView.addJavascriptInterface(jsBridge, "AndroidAdMob");
        webView.addJavascriptInterface(jsBridge, "AndroidApp");

        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            try {
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                request.setMimeType(mimeType);
                String cookies = CookieManager.getInstance().getCookie(url);
                if (cookies != null) {
                    request.addRequestHeader("cookie", cookies);
                }
                request.addRequestHeader("User-Agent", userAgent);
                request.setDescription("Mengunduh berkas dari Ujiannet-id...");
                String fileName = URLUtil.guessFileName(url, contentDisposition, mimeType);
                request.setTitle(fileName);
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);

                DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                if (dm != null) {
                    dm.enqueue(request);
                    Toast.makeText(this, "Mengunduh: " + fileName, Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                } catch (Exception ignored) {}
            }
        });

        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String host = uri.getHost();
                if (host != null && (host.contains("ujiannet-id.blogspot.com")
                        || host.contains("blogspot.com")
                        || host.contains("script.google.com")
                        || host.contains("googleusercontent.com")
                        || host.contains("drive.google.com"))) {
                    return false;
                }
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                } catch (Exception ignored) {}
                return true;
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request.isForMainFrame() && !isNetworkAvailable()) {
                    showOfflinePage();
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (url != null && !url.equals("about:blank") && !url.startsWith("file:///android_asset/") && !isShowingOfflinePage) {
                    adManager.onContentReady();
                }
                int bannerDp = adManager.getBannerHeightDp();
                if (bannerDp > 0) {
                    view.evaluateJavascript(
                        "if(window.AdMobBridge&&window.AdMobBridge.setBannerHeight){window.AdMobBridge.setBannerHeight(" + bannerDp + ");}" +
                        "if(window.setBannerHeight){window.setBannerHeight(" + bannerDp + ");}", null);
                }
            }
        });
    }

    private void setupBackNavigation() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (isInExamSession && webView != null) {
                    webView.evaluateJavascript("if(typeof confirmQuitLevel==='function'){confirmQuitLevel();}", null);
                } else if (webView != null && webView.canGoBack() && !isShowingOfflinePage) {
                    webView.goBack();
                } else {
                    finish();
                }
            }
        });
    }

    private void enableImmersiveFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (controller != null) {
            controller.hide(WindowInsetsCompat.Type.systemBars());
            controller.setSystemBarsBehavior(
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            );
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            enableImmersiveFullscreen();
            ViewCompat.requestApplyInsets(findViewById(android.R.id.content));
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (webView != null && !isShowingOfflinePage) {
            webView.saveState(outState);
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
        if (webView != null) {
            webView.removeAllViews();
            webView.destroy();
        }
        super.onDestroy();
    }

    /**
     * Jembatan metode JavaScript yang dipanggil dari Blogger (ujiannet-id.blogspot.com)
     */
    private class WebAppAdMobInterface {

        @JavascriptInterface
        public void retryConnection() {
            runOnUiThread(() -> {
                if (isNetworkAvailable()) {
                    isShowingOfflinePage = false;
                    webView.loadUrl(BLOGGER_HOME_URL);
                } else {
                    Toast.makeText(MainActivity.this, "Masih belum ada koneksi internet", Toast.LENGTH_SHORT).show();
                }
            });
        }

        @JavascriptInterface
        public void configureFamiliesPolicy(boolean childDirected, String maxAdContentRating) {
            String rating = (maxAdContentRating != null && !maxAdContentRating.isEmpty())
                    ? maxAdContentRating : "PG";
            adManager.initMobileAds(childDirected, rating);
        }

        @JavascriptInterface
        public void onGameContentReady() {
            adManager.onContentReady();
        }

        @JavascriptInterface
        public void onContentLoaded() {
            adManager.onContentReady();
        }

        @JavascriptInterface
        public void setGameplayState(boolean active) {
            isInExamSession = active;
            adManager.setExamActive(active);
        }

        @JavascriptInterface
        public int getBannerHeightPx() {
            return adManager.getBannerHeightDp();
        }

        @JavascriptInterface
        public void showInterstitial(String placement) {
            adManager.showInterstitialAd(placement != null ? placement : "page_transition");
        }

        @JavascriptInterface
        public void panggilIklanInterstisial() {
            adManager.showInterstitialAd("page_transition");
        }

        @JavascriptInterface
        public void showRewardedAd(String rewardType) {
            adManager.showRewardedAd(rewardType != null ? rewardType : "unduh_dokumen");
        }

        @JavascriptInterface
        public void panggilIklanRewarded() {
            adManager.showRewardedAd("unduh_dokumen");
        }

        @JavascriptInterface
        public void panggilIklanRewarded(String rewardType) {
            adManager.showRewardedAd(rewardType != null ? rewardType : "unduh_dokumen");
        }

        @JavascriptInterface
        public void onScoreUpdated(String jsonPayload) {
            // Hook opsional sinkronisasi skor
        }
    }
}
