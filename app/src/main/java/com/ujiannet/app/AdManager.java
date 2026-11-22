package com.ujiannet.id;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.View;
import android.webkit.WebView;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.RequestConfiguration;
import com.google.android.gms.ads.appopen.AppOpenAd;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;

import java.util.Date;

public class AdManager implements DefaultLifecycleObserver {

    // Unit ID Produksi AdMob UjianNet-ID
    private static final String AD_UNIT_BANNER = "ca-app-pub-6983364109428063/7392901632";
    private static final String AD_UNIT_INTERSTITIAL = "ca-app-pub-6983364109428063/6079819961";
    private static final String AD_UNIT_REWARDED = "ca-app-pub-6983364109428063/1834373062";
    private static final String AD_UNIT_APP_OPEN = "ca-app-pub-6983364109428063/7652300206";

    private static final long COLD_START_TIMEOUT_MS = 5000L;
    private static final long FULLSCREEN_AD_COOLDOWN_MS = 5000L;
    private static final long BANNER_RETRY_DELAY_MS = 15000L;

    private final Activity activity;
    private final WebView webView;
    private final FrameLayout bannerContainer;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final long appLaunchTimeMs;

    private AdView bannerAdView;
    private InterstitialAd interstitialAd;
    private RewardedAd rewardedAd;
    private AppOpenAd appOpenAd;

    private boolean isInitialized = false;
    private boolean isAdIdInitialized = false;
    private boolean isContentReady = false;
    private boolean isExamOrGameActive = false;
    private boolean isShowingFullScreenAd = false;
    private boolean isLoadingAppOpen = false;
    private boolean isBannerLoading = false;
    private boolean hasShownColdStartAd = false;

    private long appOpenLoadTimeMs = 0L;
    private long lastFullScreenDismissedMs = 0L;
    private int lastReportedBannerHeightDp = 0;

    private final Runnable bannerRetryRunnable = this::loadAdaptiveBanner;

    public AdManager(@NonNull Activity activity,
                     @NonNull WebView webView,
                     @NonNull FrameLayout bannerContainer) {
        this.activity = activity;
        this.webView = webView;
        this.bannerContainer = bannerContainer;
        this.appLaunchTimeMs = System.currentTimeMillis();

        ProcessLifecycleOwner.get().getLifecycle().addObserver(this);
        initMobileAds(false, RequestConfiguration.MAX_AD_CONTENT_RATING_PG);
    }

    /**
     * Inisialisasi Google Mobile Ads SDK & sinkronisasi status AD_ID ke WebView.
     */
    public void initMobileAds(boolean childDirected, @NonNull String maxRating) {
        int childTag = childDirected
                ? RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_TRUE
                : RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_UNSPECIFIED;

        int underAgeTag = childDirected
                ? RequestConfiguration.TAG_FOR_UNDER_AGE_OF_CONSENT_TRUE
                : RequestConfiguration.TAG_FOR_UNDER_AGE_OF_CONSENT_UNSPECIFIED;

        RequestConfiguration config = new RequestConfiguration.Builder()
                .setTagForChildDirectedTreatment(childTag)
                .setTagForUnderAgeOfConsent(underAgeTag)
                .setMaxAdContentRating(maxRating)
                .build();

        MobileAds.setRequestConfiguration(config);

        if (!isInitialized) {
            isInitialized = true;
            MobileAds.initialize(activity.getApplicationContext(), initializationStatus -> {
                mainHandler.post(() -> {
                    isAdIdInitialized = true;
                    notifyBridgeReadyToWeb();

                    loadAppOpenAd();
                    loadInterstitialAd();
                    loadRewardedAd();
                    if (isContentReady && bannerAdView == null && !isBannerLoading) {
                        loadAdaptiveBanner();
                    }
                });
            });
        }
    }

    public boolean isAdIdReady() {
        return isAdIdInitialized;
    }

    private void notifyBridgeReadyToWeb() {
        evaluateJs(
            "if(window.onAdIdReady){window.onAdIdReady('', false);}" +
            "if(window.onAdMobBridgeReady){window.onAdMobBridgeReady();}"
        );
    }

    /**
     * Dipanggil dari JavascriptInterface onContentLoaded() saat halaman Blogspot siap.
     */
    public void onContentReady() {
        mainHandler.post(() -> {
            if (!isContentReady) {
                isContentReady = true;
                notifyBridgeReadyToWeb();
            } else {
                syncBannerHeightToWeb(lastReportedBannerHeightDp);
                tryShowColdStartAppOpenAd();
            }

            if (isInitialized && bannerAdView == null && !isBannerLoading) {
                loadAdaptiveBanner();
            }
            if (interstitialAd == null) loadInterstitialAd();
            if (rewardedAd == null) loadRewardedAd();
            if (appOpenAd == null) {
                loadAppOpenAd();
            } else {
                tryShowColdStartAppOpenAd();
            }
        });
    }

    /**
     * Mengunci iklan App Open agar tidak muncul saat pengguna sedang mengerjakan soal ujian CBT.
     */
    public void setExamActive(boolean active) {
        mainHandler.post(() -> this.isExamOrGameActive = active);
    }

    private AdRequest buildAdRequest() {
        return new AdRequest.Builder().build();
    }

    // =========================================================================
    // 1. ADAPTIVE BANNER AD (DENGAN AUTO-RETRY & ANTI-TABRAKAN)
    // =========================================================================
    public void loadAdaptiveBanner() {
        if (activity.isFinishing() || activity.isDestroyed() || !isInitialized || isBannerLoading) {
            return;
        }

        mainHandler.removeCallbacks(bannerRetryRunnable);
        isBannerLoading = true;

        bannerContainer.setVisibility(View.VISIBLE);
        bannerContainer.removeAllViews();

        if (bannerAdView != null) {
            bannerAdView.destroy();
            bannerAdView = null;
        }

        bannerAdView = new AdView(activity);
        bannerAdView.setAdUnitId(AD_UNIT_BANNER);
        AdSize adSize = getAdaptiveBannerSize();
        bannerAdView.setAdSize(adSize);
        bannerContainer.addView(bannerAdView);

        bannerAdView.setAdListener(new AdListener() {
            @Override
            public void onAdLoaded() {
                isBannerLoading = false;
                bannerContainer.setVisibility(View.VISIBLE);
                int heightDp = adSize.getHeightInPixels(activity) > 0
                        ? Math.round(adSize.getHeightInPixels(activity) / activity.getResources().getDisplayMetrics().density)
                        : adSize.getHeight();
                lastReportedBannerHeightDp = Math.max(50, heightDp);
                syncBannerHeightToWeb(lastReportedBannerHeightDp);
            }

            @Override
            public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                isBannerLoading = false;
                bannerContainer.setVisibility(View.GONE);
                lastReportedBannerHeightDp = 0;
                syncBannerHeightToWeb(0);

                if (bannerAdView != null) {
                    bannerAdView.destroy();
                    bannerAdView = null;
                }

                mainHandler.removeCallbacks(bannerRetryRunnable);
                mainHandler.postDelayed(bannerRetryRunnable, BANNER_RETRY_DELAY_MS);
            }
        });

        bannerAdView.loadAd(buildAdRequest());
    }

    private AdSize getAdaptiveBannerSize() {
        Display display = activity.getWindowManager().getDefaultDisplay();
        DisplayMetrics outMetrics = new DisplayMetrics();
        display.getMetrics(outMetrics);
        float density = outMetrics.density;

        View parent = bannerContainer.getParent() instanceof View ? (View) bannerContainer.getParent() : null;
        float adWidthPixels = bannerContainer.getWidth() > 0
                ? bannerContainer.getWidth()
                : (parent != null && parent.getWidth() > 0 ? parent.getWidth() : outMetrics.widthPixels);

        int adWidth = (int) (adWidthPixels / density);
        return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, Math.max(320, adWidth));
    }

    public int getBannerHeightDp() {
        return lastReportedBannerHeightDp;
    }

    private void syncBannerHeightToWeb(int heightDp) {
        evaluateJs(
            "if(window.AdMobBridge&&window.AdMobBridge.setBannerHeight){window.AdMobBridge.setBannerHeight(" + heightDp + ");}" +
            "if(window.setBannerHeight){window.setBannerHeight(" + heightDp + ");}"
        );
    }

    // =========================================================================
    // 2. INTERSTITIAL AD (TRANSISI ANTAR-HALAMAN / SELESAI UJIAN)
    // =========================================================================
    public void loadInterstitialAd() {
        if (activity.isFinishing() || activity.isDestroyed()) return;

        InterstitialAd.load(activity, AD_UNIT_INTERSTITIAL, buildAdRequest(),
                new InterstitialAdLoadCallback() {
                    @Override
                    public void onAdLoaded(@NonNull InterstitialAd ad) {
                        interstitialAd = ad;
                    }

                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                        interstitialAd = null;
                    }
                });
    }

    public void showInterstitialAd(@NonNull String placement) {
        mainHandler.post(() -> {
            if (isShowingFullScreenAd || interstitialAd == null) {
                evaluateJs("if(window.onAdFailedToLoad){window.onAdFailedToLoad();}");
                if (interstitialAd == null) loadInterstitialAd();
                return;
            }

            interstitialAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                @Override
                public void onAdShowedFullScreenContent() {
                    isShowingFullScreenAd = true;
                    evaluateJs("if(window.onAdOpened){window.onAdOpened();}");
                }

                @Override
                public void onAdDismissedFullScreenContent() {
                    isShowingFullScreenAd = false;
                    lastFullScreenDismissedMs = System.currentTimeMillis();
                    interstitialAd = null;
                    evaluateJs("if(window.onAdClosed){window.onAdClosed();}");
                    loadInterstitialAd();
                }

                @Override
                public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
                    isShowingFullScreenAd = false;
                    interstitialAd = null;
                    evaluateJs("if(window.onAdFailedToLoad){window.onAdFailedToLoad();}");
                    loadInterstitialAd();
                }
            });

            interstitialAd.show(activity);
        });
    }

    // =========================================================================
    // 3. REWARDED AD (UNTUK TOMBOL UNDUH / HADIAH OPT-IN)
    // =========================================================================
    public void loadRewardedAd() {
        if (activity.isFinishing() || activity.isDestroyed()) return;

        RewardedAd.load(activity, AD_UNIT_REWARDED, buildAdRequest(),
                new RewardedAdLoadCallback() {
                    @Override
                    public void onAdLoaded(@NonNull RewardedAd ad) {
                        rewardedAd = ad;
                    }

                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                        rewardedAd = null;
                    }
                });
    }

    public void showRewardedAd(@NonNull String rewardType) {
        mainHandler.post(() -> {
            if (isShowingFullScreenAd || rewardedAd == null) {
                evaluateJs("if(window.onRewardFailed){window.onRewardFailed('not_ready');}");
                if (rewardedAd == null) loadRewardedAd();
                return;
            }

            final boolean[] rewardGranted = {false};

            rewardedAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                @Override
                public void onAdShowedFullScreenContent() {
                    isShowingFullScreenAd = true;
                    evaluateJs("if(window.onAdOpened){window.onAdOpened();}");
                }

                @Override
                public void onAdDismissedFullScreenContent() {
                    isShowingFullScreenAd = false;
                    lastFullScreenDismissedMs = System.currentTimeMillis();
                    rewardedAd = null;
                    if (rewardGranted[0]) {
                        evaluateJs("if(window.onRewardEarned){window.onRewardEarned('" + escapeJs(rewardType) + "');}");
                    } else {
                        evaluateJs("if(window.onAdClosed){window.onAdClosed();}");
                    }
                    loadRewardedAd();
                }

                @Override
                public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
                    isShowingFullScreenAd = false;
                    rewardedAd = null;
                    evaluateJs("if(window.onRewardFailed){window.onRewardFailed('failed_to_show');}");
                    loadRewardedAd();
                }
            });

            rewardedAd.show(activity, rewardItem -> rewardGranted[0] = true);
        });
    }

    // =========================================================================
    // 4. APP OPEN AD (PEMBUKAAN APLIKASI - COLD START & WARM START)
    // =========================================================================
    public void loadAppOpenAd() {
        if (isLoadingAppOpen || isAppOpenAdAvailable() || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        isLoadingAppOpen = true;

        AppOpenAd.load(activity, AD_UNIT_APP_OPEN, buildAdRequest(),
                new AppOpenAd.AppOpenAdLoadCallback() {
                    @Override
                    public void onAdLoaded(@NonNull AppOpenAd ad) {
                        appOpenAd = ad;
                        isLoadingAppOpen = false;
                        appOpenLoadTimeMs = new Date().getTime();
                        tryShowColdStartAppOpenAd();
                    }

                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                        isLoadingAppOpen = false;
                    }
                });
    }

    private boolean isAppOpenAdAvailable() {
        if (appOpenAd == null) return false;
        long fourHoursMs = 4L * 3600L * 1000L;
        return (new Date().getTime() - appOpenLoadTimeMs) < fourHoursMs;
    }

    private boolean isAppInForeground() {
        return !activity.isFinishing()
                && !activity.isDestroyed()
                && ProcessLifecycleOwner.get().getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED);
    }

    private void tryShowColdStartAppOpenAd() {
        if (hasShownColdStartAd) return;
        long elapsedSinceLaunch = System.currentTimeMillis() - appLaunchTimeMs;
        if (elapsedSinceLaunch > COLD_START_TIMEOUT_MS) {
            hasShownColdStartAd = true;
            return;
        }
        if (isContentReady && isAppOpenAdAvailable() && !isExamOrGameActive && !isShowingFullScreenAd) {
            hasShownColdStartAd = true;
            showAppOpenAdInternal();
        }
    }

    @Override
    public void onStart(@NonNull LifecycleOwner owner) {
        if (!isContentReady) {
            loadAppOpenAd();
            return;
        }
        hasShownColdStartAd = true;
        showAppOpenAdInternal();
    }

    private void showAppOpenAdInternal() {
        if (!isAppInForeground() || isExamOrGameActive || isShowingFullScreenAd) {
            return;
        }

        if (System.currentTimeMillis() - lastFullScreenDismissedMs < FULLSCREEN_AD_COOLDOWN_MS) {
            return;
        }

        if (!isAppOpenAdAvailable()) {
            loadAppOpenAd();
            return;
        }

        final AppOpenAd adToShow = appOpenAd;
        if (adToShow == null) return;

        adToShow.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override
            public void onAdShowedFullScreenContent() {
                isShowingFullScreenAd = true;
                evaluateJs("if(window.onAppOpenAdOpened){window.onAppOpenAdOpened();}");
            }

            @Override
            public void onAdDismissedFullScreenContent() {
                isShowingFullScreenAd = false;
                lastFullScreenDismissedMs = System.currentTimeMillis();
                appOpenAd = null;
                evaluateJs("if(window.onAppOpenAdClosed){window.onAppOpenAdClosed();}");
                loadAppOpenAd();
            }

            @Override
            public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
                isShowingFullScreenAd = false;
                appOpenAd = null;
                evaluateJs("if(window.onAppOpenAdClosed){window.onAppOpenAdClosed();}");
                loadAppOpenAd();
            }
        });

        adToShow.show(activity);
    }

    // =========================================================================
    // LIFECYCLE & UTILITAS WEBVIEW
    // =========================================================================
    public void onResume() {
        if (bannerAdView != null) bannerAdView.resume();
    }

    public void onPause() {
        if (bannerAdView != null) bannerAdView.pause();
    }

    public void onDestroy() {
        mainHandler.removeCallbacks(bannerRetryRunnable);
        ProcessLifecycleOwner.get().getLifecycle().removeObserver(this);
        if (bannerAdView != null) {
            bannerAdView.destroy();
            bannerAdView = null;
        }
    }

    private void evaluateJs(@NonNull String script) {
        mainHandler.post(() -> {
            if (!activity.isFinishing() && !activity.isDestroyed() && webView != null) {
                webView.evaluateJavascript(script, null);
            }
        });
    }

    private String escapeJs(@NonNull String input) {
        return input.replace("\\", "\\\\").replace("'", "\\'");
    }
}
