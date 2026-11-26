package com.ujiannet.id;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ProcessLifecycleOwner;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdLoader;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.RequestConfiguration;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.gms.ads.nativead.NativeAd;
import com.google.android.gms.ads.nativead.NativeAdOptions;
import com.google.android.gms.ads.nativead.NativeAdView;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.UserMessagingPlatform;

public class AdManager {

    private static final String AD_UNIT_NATIVE = "ca-app-pub-6983364109428063/2345007949";
    private static final String AD_UNIT_INTERSTITIAL = "ca-app-pub-6983364109428063/6079819961";

    // Batas frekuensi & jeda aman kebijakan Google AdMob
    private static final long INTERSTITIAL_COOLDOWN_MS = 60_000L;
    private static final long RETRY_DELAY_MS = 25_000L;
    private static final int MAX_RETRY_ATTEMPTS = 2;

    private final Activity activity;
    private final WebView webView;
    private final FrameLayout nativeAdContainer;
    @Nullable
    private final View adSectionDivider;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private volatile NativeAd currentNativeAd;
    private volatile InterstitialAd interstitialAd;

    private volatile boolean isConsentFlowDone = false;
    private volatile boolean isSdkInitialized = false;
    private volatile boolean isContentReady = false;
    private volatile boolean isExamSessionActive = false;
    private volatile boolean isNativeAllowedByUrl = true;
    private volatile boolean isNativeAllowedByWeb = true;
    private volatile boolean isShowingFullScreenAd = false;

    private boolean isLoadingInterstitial = false;
    private boolean isLoadingNative = false;

    private int nativeRetryCount = 0;
    private int interstitialRetryCount = 0;

    private long lastInterstitialShownElapsedMs = 0L;

    public AdManager(@NonNull Activity activity,
                     @NonNull WebView webView,
                     @NonNull FrameLayout nativeAdContainer,
                     @Nullable View adSectionDivider) {
        this.activity = activity;
        this.webView = webView;
        this.nativeAdContainer = nativeAdContainer;
        this.adSectionDivider = adSectionDivider;

        setupStandardAdConfiguration();
        requestConsentAndInitializeAds();
    }

    private void setupStandardAdConfiguration() {
        RequestConfiguration config = new RequestConfiguration.Builder()
                .setTagForChildDirectedTreatment(RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_UNSPECIFIED)
                .setTagForUnderAgeOfConsent(RequestConfiguration.TAG_FOR_UNDER_AGE_OF_CONSENT_UNSPECIFIED)
                .setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_PG)
                .build();
        MobileAds.setRequestConfiguration(config);
    }

    private void requestConsentAndInitializeAds() {
        ConsentRequestParameters params = new ConsentRequestParameters.Builder()
                .setTagForUnderAgeOfConsent(false)
                .build();

        ConsentInformation consentInformation = UserMessagingPlatform.getConsentInformation(activity);
        consentInformation.requestConsentInfoUpdate(
                activity,
                params,
                () -> UserMessagingPlatform.loadAndShowConsentFormIfRequired(
                        activity,
                        formError -> onConsentFinished()
                ),
                requestConsentError -> onConsentFinished()
        );
    }

    private void onConsentFinished() {
        isConsentFlowDone = true;
        initializeMobileAdsSdk();
    }

    private void initializeMobileAdsSdk() {
        if (isSdkInitialized || activity.isFinishing() || activity.isDestroyed()) return;
        isSdkInitialized = true;

        MobileAds.initialize(activity.getApplicationContext(), initializationStatus -> mainHandler.post(() -> {
            loadInterstitialAd();
            loadNativeAdvancedAd();
        }));
    }

    public boolean isConsentCompleted() {
        return isConsentFlowDone && isSdkInitialized;
    }

    public boolean isInterstitialAdReady() {
        boolean inCooldown = lastInterstitialShownElapsedMs > 0L
                && (SystemClock.elapsedRealtime() - lastInterstitialShownElapsedMs) < INTERSTITIAL_COOLDOWN_MS;
        return interstitialAd != null && !isShowingFullScreenAd && !isExamSessionActive && !inCooldown;
    }

    /**
     * Dipanggil saat halaman baru mulai dimuat (onPageStarted) untuk menyembunyikan iklan
     * sementara hingga konten benar-benar siap tampil.
     */
    public void onPageLoading(boolean allowedByUrl) {
        mainHandler.post(() -> {
            this.isContentReady = false;
            this.isNativeAllowedByUrl = allowedByUrl;
            updateNativeContainerVisibility();
        });
    }

    public void onContentReady() {
        mainHandler.post(() -> {
            isContentReady = true;
            if (currentNativeAd == null && !isLoadingNative && isNativeAllowedByUrl && isNativeAllowedByWeb) {
                loadNativeAdvancedAd();
            }
            updateNativeContainerVisibility();
        });
    }

    /**
     * Kontrol langsung dari URL Android untuk memblokir iklan di halaman legal/pencarian/login.
     */
    public void setPagePolicyAllowed(boolean allowed) {
        mainHandler.post(() -> {
            this.isNativeAllowedByUrl = allowed;
            if (allowed) {
                this.isNativeAllowedByWeb = true;
                if (currentNativeAd == null && !isLoadingNative) {
                    loadNativeAdvancedAd();
                }
            }
            updateNativeContainerVisibility();
        });
    }

    public void setExamSessionActive(boolean active) {
        mainHandler.post(() -> {
            this.isExamSessionActive = active;
            updateNativeContainerVisibility();
        });
    }

    public void setNativeAdVisible(boolean visible) {
        mainHandler.post(() -> {
            this.isNativeAllowedByWeb = visible;
            if (visible && isNativeAllowedByUrl && currentNativeAd == null && !isLoadingNative) {
                loadNativeAdvancedAd();
            }
            updateNativeContainerVisibility();
        });
    }

    private void updateNativeContainerVisibility() {
        boolean shouldShow = isContentReady
                && !isExamSessionActive
                && isNativeAllowedByUrl
                && isNativeAllowedByWeb
                && !isShowingFullScreenAd
                && currentNativeAd != null;

        int visibility = shouldShow ? View.VISIBLE : View.GONE;
        nativeAdContainer.setVisibility(visibility);
        if (adSectionDivider != null) {
            adSectionDivider.setVisibility(visibility);
        }
    }

    private AdRequest buildAdRequest() {
        return new AdRequest.Builder().build();
    }

    private boolean isAppInForeground() {
        return !activity.isFinishing()
                && !activity.isDestroyed()
                && ProcessLifecycleOwner.get().getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED);
    }

    // =========================================================================
    // 1. NATIVE ADVANCED AD (BERANDA & ARTIKEL MATERI - PATUH UKURAN ASET)
    // =========================================================================
    public void loadNativeAdvancedAd() {
        if (!isSdkInitialized || isLoadingNative) return;
        if (activity.isFinishing() || activity.isDestroyed()) return;

        isLoadingNative = true;

        NativeAdOptions nativeOptions = new NativeAdOptions.Builder()
                .setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT)
                .build();

        AdLoader adLoader = new AdLoader.Builder(activity, AD_UNIT_NATIVE)
                .forNativeAd(nativeAd -> {
                    isLoadingNative = false;
                    nativeRetryCount = 0;

                    if (activity.isDestroyed() || activity.isFinishing()) {
                        nativeAd.destroy();
                        return;
                    }
                    if (currentNativeAd != null) {
                        currentNativeAd.destroy();
                    }
                    currentNativeAd = nativeAd;

                    NativeAdView adView = (NativeAdView) LayoutInflater.from(activity)
                            .inflate(R.layout.ad_native_layout, nativeAdContainer, false);
                    populateNativeAdView(nativeAd, adView);

                    nativeAdContainer.removeAllViews();
                    nativeAdContainer.addView(adView);
                    updateNativeContainerVisibility();
                })
                .withAdListener(new AdListener() {
                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                        isLoadingNative = false;
                        updateNativeContainerVisibility();

                        if (nativeRetryCount < MAX_RETRY_ATTEMPTS) {
                            nativeRetryCount++;
                            mainHandler.postDelayed(() -> loadNativeAdvancedAd(), RETRY_DELAY_MS);
                        }
                    }
                })
                .withNativeAdOptions(nativeOptions)
                .build();

        adLoader.loadAd(buildAdRequest());
    }

    private void populateNativeAdView(@NonNull NativeAd nativeAd, @NonNull NativeAdView adView) {
        TextView headlineView = adView.findViewById(R.id.ad_headline);
        TextView bodyView = adView.findViewById(R.id.ad_body);
        TextView advertiserView = adView.findViewById(R.id.ad_advertiser);
        Button ctaView = adView.findViewById(R.id.ad_call_to_action);
        ImageView iconView = adView.findViewById(R.id.ad_app_icon);

        adView.setHeadlineView(headlineView);
        adView.setBodyView(bodyView);
        adView.setAdvertiserView(advertiserView);
        adView.setCallToActionView(ctaView);
        adView.setIconView(iconView);

        headlineView.setText(nativeAd.getHeadline());

        if (nativeAd.getBody() != null) {
            bodyView.setVisibility(View.VISIBLE);
            bodyView.setText(nativeAd.getBody());
        } else {
            bodyView.setVisibility(View.GONE);
        }

        if (nativeAd.getCallToAction() != null) {
            ctaView.setVisibility(View.VISIBLE);
            ctaView.setText(nativeAd.getCallToAction());
        } else {
            ctaView.setVisibility(View.INVISIBLE);
        }

        if (nativeAd.getIcon() != null) {
            iconView.setVisibility(View.VISIBLE);
            iconView.setImageDrawable(nativeAd.getIcon().getDrawable());
        } else {
            iconView.setVisibility(View.GONE);
        }

        if (nativeAd.getAdvertiser() != null) {
            advertiserView.setVisibility(View.VISIBLE);
            advertiserView.setText(nativeAd.getAdvertiser());
        } else {
            advertiserView.setVisibility(View.GONE);
        }

        adView.setNativeAd(nativeAd);
    }

    // =========================================================================
    // 2. INTERSTITIAL AD (TRANSISI ANTAR-ARTIKEL / SELESAI UJIAN)
    // =========================================================================
    public void loadInterstitialAd() {
        if (!isSdkInitialized || isLoadingInterstitial || interstitialAd != null) return;
        if (activity.isFinishing() || activity.isDestroyed()) return;

        isLoadingInterstitial = true;
        InterstitialAd.load(
                activity,
                AD_UNIT_INTERSTITIAL,
                buildAdRequest(),
                new InterstitialAdLoadCallback() {
                    @Override
                    public void onAdLoaded(@NonNull InterstitialAd ad) {
                        interstitialAd = ad;
                        isLoadingInterstitial = false;
                        interstitialRetryCount = 0;
                    }

                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                        interstitialAd = null;
                        isLoadingInterstitial = false;
                        evaluateJs("if(window.onAdFailedToLoad){window.onAdFailedToLoad();}");

                        if (interstitialRetryCount < MAX_RETRY_ATTEMPTS) {
                            interstitialRetryCount++;
                            mainHandler.postDelayed(() -> loadInterstitialAd(), RETRY_DELAY_MS);
                        }
                    }
                }
        );
    }

    public void showInterstitialAd(@NonNull String placement) {
        mainHandler.post(() -> {
            boolean inCooldown = lastInterstitialShownElapsedMs > 0L
                    && (SystemClock.elapsedRealtime() - lastInterstitialShownElapsedMs) < INTERSTITIAL_COOLDOWN_MS;

            if (!isAppInForeground() || isShowingFullScreenAd || isExamSessionActive || interstitialAd == null || inCooldown) {
                evaluateJs("if(window.onAdClosed){window.onAdClosed();}");
                if (interstitialAd == null) loadInterstitialAd();
                return;
            }

            interstitialAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                @Override
                public void onAdShowedFullScreenContent() {
                    isShowingFullScreenAd = true;
                    lastInterstitialShownElapsedMs = SystemClock.elapsedRealtime();
                    updateNativeContainerVisibility();
                    evaluateJs("if(window.onAdOpened){window.onAdOpened();}");
                }

                @Override
                public void onAdDismissedFullScreenContent() {
                    isShowingFullScreenAd = false;
                    interstitialAd = null;
                    updateNativeContainerVisibility();
                    evaluateJs("if(window.onAdClosed){window.onAdClosed();}");
                    loadInterstitialAd();
                }

                @Override
                public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
                    isShowingFullScreenAd = false;
                    interstitialAd = null;
                    updateNativeContainerVisibility();
                    evaluateJs("if(window.onAdFailedToLoad){window.onAdFailedToLoad();}");
                    loadInterstitialAd();
                }
            });

            interstitialAd.show(activity);
        });
    }

    // =========================================================================
    // LIFECYCLE & UTILITAS
    // =========================================================================
    public void onDestroy() {
        mainHandler.removeCallbacksAndMessages(null);
        nativeAdContainer.removeAllViews();
        if (currentNativeAd != null) {
            currentNativeAd.destroy();
            currentNativeAd = null;
        }
        interstitialAd = null;
    }

    private void evaluateJs(@NonNull String script) {
        mainHandler.post(() -> {
            if (!activity.isFinishing() && !activity.isDestroyed() && webView != null) {
                webView.evaluateJavascript(script, null);
            }
        });
    }
}
