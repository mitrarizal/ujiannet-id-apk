package com.ujiannet.id;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdLoader;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.RequestConfiguration;
import com.google.android.gms.ads.appopen.AppOpenAd;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.gms.ads.nativead.MediaView;
import com.google.android.gms.ads.nativead.NativeAd;
import com.google.android.gms.ads.nativead.NativeAdOptions;
import com.google.android.gms.ads.nativead.NativeAdView;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.UserMessagingPlatform;

import java.util.Date;

public class AdManager implements DefaultLifecycleObserver {

    // GANTI 3 UNIT ID INI DENGAN UNIT ID PRODUKSI ADMOB ANDA SEBELUM RILIS KE PLAY STORE
    private static final String AD_UNIT_APP_OPEN = "ca-app-pub-3940256099942544/9257395921";
    private static final String AD_UNIT_NATIVE = "ca-app-pub-3940256099942544/2247696110";
    private static final String AD_UNIT_INTERSTITIAL = "ca-app-pub-3940256099942544/1033173712";

    // Jeda minimal antar-iklan layar penuh agar tidak melanggar kebijakan frekuensi AdMob
    private static final long INTERSTITIAL_COOLDOWN_MS = 45_000L;
    private static final long APP_OPEN_COOLDOWN_MS = 30_000L;
    private static final long COLD_START_WINDOW_MS = 8_000L;

    private final Activity activity;
    private final WebView webView;
    private final FrameLayout nativeAdContainer;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private AppOpenAd appOpenAd;
    private NativeAd currentNativeAd;
    private InterstitialAd interstitialAd;

    private boolean isSdkInitialized = false;
    private boolean isContentReady = false;
    private boolean isExamSessionActive = false;
    private boolean isNativeAllowedByWeb = true;
    private boolean isShowingFullScreenAd = false;

    private boolean isLoadingAppOpen = false;
    private boolean isLoadingInterstitial = false;
    private boolean isLoadingNative = false;

    private boolean isColdStartWindowOpen = true;
    private final long appLaunchTimeMs = System.currentTimeMillis();
    private long appOpenLoadTimeMs = 0L;
    private long lastAppOpenShownMs = 0L;
    private long lastInterstitialShownMs = 0L;

    public AdManager(@NonNull Activity activity,
                     @NonNull WebView webView,
                     @NonNull FrameLayout nativeAdContainer) {
        this.activity = activity;
        this.webView = webView;
        this.nativeAdContainer = nativeAdContainer;

        ProcessLifecycleOwner.get().getLifecycle().addObserver(this);
        setupStandardAdConfiguration();
        requestConsentAndInitializeAds();
    }

    /**
     * Konfigurasi standar aplikasi umum/akademik (mengizinkan AD_ID & lelang penuh).
     */
    private void setupStandardAdConfiguration() {
        RequestConfiguration config = new RequestConfiguration.Builder()
                .setTagForChildDirectedTreatment(RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_UNSPECIFIED)
                .setTagForUnderAgeOfConsent(RequestConfiguration.TAG_FOR_UNDER_AGE_OF_CONSENT_UNSPECIFIED)
                .setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_PG)
                .build();
        MobileAds.setRequestConfiguration(config);
    }

    /**
     * Memeriksa status persetujuan privasi (UMP) secara non-blocking lalu menginisialisasi SDK.
     */
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
                        formError -> initializeMobileAdsSdk()
                ),
                requestConsentError -> initializeMobileAdsSdk()
        );
    }

    private void initializeMobileAdsSdk() {
        if (isSdkInitialized || activity.isFinishing() || activity.isDestroyed()) return;
        isSdkInitialized = true;

        MobileAds.initialize(activity.getApplicationContext(), initializationStatus -> {
            mainHandler.post(() -> {
                loadAppOpenAd();
                loadInterstitialAd();
                loadNativeAdvancedAd();
            });
        });
    }

    public void onContentReady() {
        mainHandler.post(() -> {
            isContentReady = true;
            if (currentNativeAd == null && !isLoadingNative) {
                loadNativeAdvancedAd();
            }
            updateNativeContainerVisibility();
        });
    }

    /**
     * Mengatur status sesi ujian (true = sedang mengerjakan soal -> sembunyikan Native & tahan App Open).
     */
    public void setExamSessionActive(boolean active) {
        mainHandler.post(() -> {
            this.isExamSessionActive = active;
            if (active) {
                isColdStartWindowOpen = false;
            }
            updateNativeContainerVisibility();
        });
    }

    public void setNativeAdVisible(boolean visible) {
        mainHandler.post(() -> {
            this.isNativeAllowedByWeb = visible;
            if (visible && currentNativeAd == null && !isLoadingNative) {
                loadNativeAdvancedAd();
            }
            updateNativeContainerVisibility();
        });
    }

    private void updateNativeContainerVisibility() {
        boolean shouldShow = isContentReady
                && !isExamSessionActive
                && isNativeAllowedByWeb
                && !isShowingFullScreenAd
                && currentNativeAd != null;
        nativeAdContainer.setVisibility(shouldShow ? View.VISIBLE : View.GONE);
    }

    private AdRequest buildAdRequest() {
        return new AdRequest.Builder().build();
    }

    // =========================================================================
    // 1. APP OPEN AD (PEMBUKAAN APLIKASI - COLD START & WARM RESUME)
    // =========================================================================
    public void loadAppOpenAd() {
        if (!isSdkInitialized || isLoadingAppOpen || isAppOpenAdAvailable()) return;
        if (activity.isFinishing() || activity.isDestroyed()) return;

        isLoadingAppOpen = true;
        AppOpenAd.load(
                activity,
                AD_UNIT_APP_OPEN,
                buildAdRequest(),
                new AppOpenAd.AppOpenAdLoadCallback() {
                    @Override
                    public void onAdLoaded(@NonNull AppOpenAd ad) {
                        appOpenAd = ad;
                        isLoadingAppOpen = false;
                        appOpenLoadTimeMs = new Date().getTime();

                        // Tampilkan segera saat Cold Start jika masih dalam jendela pembukaan awal
                        boolean withinColdStart = (System.currentTimeMillis() - appLaunchTimeMs) <= COLD_START_WINDOW_MS;
                        if (isColdStartWindowOpen && withinColdStart && !isExamSessionActive && !isShowingFullScreenAd) {
                            isColdStartWindowOpen = false;
                            showAppOpenAdInternal();
                        }
                    }

                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                        isLoadingAppOpen = false;
                        appOpenAd = null;
                    }
                }
        );
    }

    private boolean isAppOpenAdAvailable() {
        if (appOpenAd == null) return false;
        long fourHoursMs = 4L * 3600L * 1000L;
        return (new Date().getTime() - appOpenLoadTimeMs) < fourHoursMs;
    }

    @Override
    public void onStart(@NonNull LifecycleOwner owner) {
        // Saat aplikasi kembali ke foreground (Warm Resume)
        if (isExamSessionActive || isShowingFullScreenAd) {
            return;
        }
        if ((System.currentTimeMillis() - lastAppOpenShownMs) < APP_OPEN_COOLDOWN_MS) {
            return;
        }
        if (!isAppOpenAdAvailable()) {
            loadAppOpenAd();
            return;
        }
        isColdStartWindowOpen = false;
        showAppOpenAdInternal();
    }

    private void showAppOpenAdInternal() {
        if (activity.isFinishing() || activity.isDestroyed() || !isAppOpenAdAvailable()) return;

        appOpenAd.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override
            public void onAdShowedFullScreenContent() {
                isShowingFullScreenAd = true;
                lastAppOpenShownMs = System.currentTimeMillis();
                updateNativeContainerVisibility();
                evaluateJs("if(window.onAppOpenAdOpened){window.onAppOpenAdOpened();}");
            }

            @Override
            public void onAdDismissedFullScreenContent() {
                isShowingFullScreenAd = false;
                appOpenAd = null;
                updateNativeContainerVisibility();
                evaluateJs("if(window.onAppOpenAdClosed){window.onAppOpenAdClosed();}");
                loadAppOpenAd();
            }

            @Override
            public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
                isShowingFullScreenAd = false;
                appOpenAd = null;
                updateNativeContainerVisibility();
                evaluateJs("if(window.onAppOpenAdClosed){window.onAppOpenAdClosed();}");
                loadAppOpenAd();
            }
        });

        appOpenAd.show(activity);
    }

    // =========================================================================
    // 2. NATIVE ADVANCED AD (TAMPIL DI MENU / BERANDA / HASIL UJIAN)
    // =========================================================================
    public void loadNativeAdvancedAd() {
        if (!isSdkInitialized || isLoadingNative) return;
        if (activity.isFinishing() || activity.isDestroyed()) return;

        isLoadingNative = true;
        AdLoader adLoader = new AdLoader.Builder(activity, AD_UNIT_NATIVE)
                .forNativeAd(nativeAd -> {
                    isLoadingNative = false;
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
                        nativeAdContainer.setVisibility(View.GONE);
                    }
                })
                .withNativeAdOptions(new NativeAdOptions.Builder()
                        .setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT)
                        .build())
                .build();

        adLoader.loadAd(buildAdRequest());
    }

    private void populateNativeAdView(@NonNull NativeAd nativeAd, @NonNull NativeAdView adView) {
        TextView headlineView = adView.findViewById(R.id.ad_headline);
        TextView bodyView = adView.findViewById(R.id.ad_body);
        TextView advertiserView = adView.findViewById(R.id.ad_advertiser);
        Button ctaView = adView.findViewById(R.id.ad_call_to_action);
        ImageView iconView = adView.findViewById(R.id.ad_app_icon);
        MediaView mediaView = adView.findViewById(R.id.ad_media);

        adView.setHeadlineView(headlineView);
        adView.setBodyView(bodyView);
        adView.setAdvertiserView(advertiserView);
        adView.setCallToActionView(ctaView);
        adView.setIconView(iconView);
        adView.setMediaView(mediaView);

        headlineView.setText(nativeAd.getHeadline());
        if (nativeAd.getMediaContent() != null) {
            mediaView.setMediaContent(nativeAd.getMediaContent());
        }

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
    // 3. INTERSTITIAL AD (TRANSISI ANTAR-HALAMAN / SELESAI UJIAN)
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
                    }

                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                        interstitialAd = null;
                        isLoadingInterstitial = false;
                    }
                }
        );
    }

    public void showInterstitialAd(@NonNull String placement) {
        mainHandler.post(() -> {
            boolean inCooldown = (System.currentTimeMillis() - lastInterstitialShownMs) < INTERSTITIAL_COOLDOWN_MS;

            // Jika iklan belum siap, sedang tampil iklan lain, atau masih masa jeda -> langsung lanjutkan alur web tanpa macet
            if (isShowingFullScreenAd || interstitialAd == null || inCooldown) {
                evaluateJs("if(window.onAdClosed){window.onAdClosed();}");
                if (interstitialAd == null) loadInterstitialAd();
                return;
            }

            interstitialAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                @Override
                public void onAdShowedFullScreenContent() {
                    isShowingFullScreenAd = true;
                    lastInterstitialShownMs = System.currentTimeMillis();
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
                    evaluateJs("if(window.onAdClosed){window.onAdClosed();}");
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
        ProcessLifecycleOwner.get().getLifecycle().removeObserver(this);
        mainHandler.removeCallbacksAndMessages(null);
        if (currentNativeAd != null) {
            currentNativeAd.destroy();
            currentNativeAd = null;
        }
        interstitialAd = null;
        appOpenAd = null;
    }

    private void evaluateJs(@NonNull String script) {
        mainHandler.post(() -> {
            if (!activity.isFinishing() && !activity.isDestroyed() && webView != null) {
                webView.evaluateJavascript(script, null);
            }
        });
    }
}
