package com.wordoflife.biblequiz;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;

import org.json.JSONObject;

import com.google.android.libraries.ads.mobile.sdk.MobileAds;
import com.google.android.libraries.ads.mobile.sdk.initialization.InitializationConfig;
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest;
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback;
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError;
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError;
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd;
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdEventCallback;

public class MainActivity extends Activity {
    private static final String TAG = "WordOfLifeAds";

    // Google's official AdMob Android test IDs. These do NOT generate revenue.
    // Replace them with the app ID and interstitial ad unit ID from your AdMob account
    // only after testing is complete and the app is ready for release.
    private static final String TEST_ADMOB_APP_ID =
            "ca-app-pub-3940256099942544~3347511713";
    private static final String TEST_INTERSTITIAL_AD_UNIT_ID =
            "ca-app-pub-3940256099942544/1033173712";

    private static final String AD_PREFS = "word_of_life_ad_state_v3";
    private static final String COMPLETED_LEVELS_KEY = "completed_levels";
    private static final int LEVELS_PER_INTERSTITIAL = 2;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private WebView gameView;
    private InterstitialAd interstitialAd;
    private boolean adLoadInProgress = false;
    private boolean adIsShowing = false;
    private boolean gamePageLoaded = false;
    private int pendingInterstitials = 0;
    private String adStatusMessage = "Starting Google test ad service…";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final int deepGreen = Color.rgb(13, 23, 20);
        Window window = getWindow();
        window.setStatusBarColor(deepGreen);
        window.setNavigationBarColor(deepGreen);
        window.setBackgroundDrawableResource(R.color.splash_background);
        window.getDecorView().setSystemUiVisibility(0);

        gameView = new WebView(this);
        gameView.setBackgroundColor(deepGreen);
        gameView.setOverScrollMode(View.OVER_SCROLL_NEVER);

        WebSettings settings = gameView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setDefaultTextEncodingName("UTF-8");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.setSafeBrowsingEnabled(true);
        }

        gameView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                gamePageLoaded = true;
                renderAdStatus();
            }
        });
        gameView.setWebChromeClient(new WebChromeClient());
        gameView.addJavascriptInterface(new AdsBridge(), "AndroidAds");

        setContentView(gameView);
        gameView.loadUrl("file:///android_asset/www/index.html");

        initializeAds();
    }

    private void initializeAds() {
        setAdStatus("Starting Google test ad service…");
        new Thread(() -> MobileAds.initialize(
                getApplicationContext(),
                new InitializationConfig.Builder(TEST_ADMOB_APP_ID).build(),
                initializationStatus -> runOnUiThread(() -> {
                    setAdStatus("Google ad service initialized. Loading a test ad…");
                    loadInterstitial();
                })
        ), "word-of-life-ads-init").start();
    }

    // Uses Google's documented single-load API rather than polling the preload cache.
    private void loadInterstitial() {
        if (isFinishing() || adLoadInProgress || interstitialAd != null) {
            return;
        }

        adLoadInProgress = true;
        setAdStatus("Loading Google's test advertisement…");

        InterstitialAd.load(
                new AdRequest.Builder(TEST_INTERSTITIAL_AD_UNIT_ID).build(),
                new AdLoadCallback<InterstitialAd>() {
                    @Override
                    public void onAdLoaded(@NonNull InterstitialAd ad) {
                        runOnUiThread(() -> {
                            adLoadInProgress = false;
                            interstitialAd = ad;
                            Log.i(TAG, "Google test interstitial loaded successfully.");
                            if (pendingInterstitials > 0) {
                                showPendingInterstitial();
                            } else {
                                setAdStatus("Test advertisement loaded. It will appear after 2 passed levels.");
                            }
                        });
                    }

                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError error) {
                        runOnUiThread(() -> {
                            adLoadInProgress = false;
                            String message = error.getMessage();
                            Log.e(TAG, "Google test interstitial failed to load: " + message);
                            setAdStatus("Google could not load the test ad: " + message);
                            // Retry at a gentle interval. Never block the quiz when an ad is unavailable.
                            mainHandler.postDelayed(() -> {
                                if (!isFinishing() && interstitialAd == null) {
                                    loadInterstitial();
                                }
                            }, 30000L);
                        });
                    }
                }
        );
    }

    private void showPendingInterstitial() {
        if (isFinishing() || adIsShowing || pendingInterstitials <= 0) {
            return;
        }

        if (interstitialAd == null) {
            setAdStatus(adLoadInProgress
                    ? "Two levels passed. Waiting for the test ad to finish loading…"
                    : "Two levels passed. Requesting a test ad…");
            loadInterstitial();
            return;
        }

        InterstitialAd adToShow = interstitialAd;
        interstitialAd = null;
        pendingInterstitials--;
        adIsShowing = true;

        adToShow.setAdEventCallback(new InterstitialAdEventCallback() {
            @Override
            public void onAdShowedFullScreenContent() {
                runOnUiThread(() -> setAdStatus("Test advertisement is showing."));
            }

            @Override
            public void onAdImpression() {
                Log.i(TAG, "Google test interstitial impression recorded.");
            }

            @Override
            public void onAdDismissedFullScreenContent() {
                runOnUiThread(() -> {
                    adIsShowing = false;
                    setAdStatus("Test advertisement closed. Preparing the next one…");
                    loadInterstitial();
                });
            }

            @Override
            public void onAdFailedToShowFullScreenContent(@NonNull FullScreenContentError error) {
                runOnUiThread(() -> {
                    adIsShowing = false;
                    pendingInterstitials++;
                    setAdStatus("Google loaded the test ad but could not show it: " + error.getMessage());
                    loadInterstitial();
                });
            }
        });

        Log.i(TAG, "Showing Google test interstitial.");
        adToShow.show(this);
    }

    private void setAdStatus(String message) {
        adStatusMessage = message;
        renderAdStatus();
    }

    private void renderAdStatus() {
        if (gameView == null || !gamePageLoaded) {
            return;
        }
        String quoted = JSONObject.quote(adStatusMessage);
        gameView.post(() -> {
            if (gameView != null) {
                gameView.evaluateJavascript(
                        "(function(){var e=document.getElementById('adStatus');if(e){e.textContent="
                                + quoted + ";}})();",
                        null
                );
            }
        });
    }

    private final class AdsBridge {
        @JavascriptInterface
        public void onLevelPassed(int levelId) {
            if (levelId < 1 || levelId > 100) {
                return;
            }

            // The web game calls this only when a level is passed, not on failed attempts.
            runOnUiThread(() -> {
                SharedPreferences preferences =
                        getSharedPreferences(AD_PREFS, MODE_PRIVATE);
                int completedLevels =
                        preferences.getInt(COMPLETED_LEVELS_KEY, 0) + 1;
                preferences.edit()
                        .putInt(COMPLETED_LEVELS_KEY, completedLevels)
                        .apply();

                if (completedLevels % LEVELS_PER_INTERSTITIAL == 0) {
                    pendingInterstitials++;
                    setAdStatus("Two levels passed. Preparing your full-screen test ad…");
                    showPendingInterstitial();
                } else {
                    setAdStatus("Level passed. One more passed level until the next test ad.");
                }
            });
        }
    }

    @Override
    public void onBackPressed() {
        if (gameView != null && gameView.canGoBack()) {
            gameView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        mainHandler.removeCallbacksAndMessages(null);
        if (gameView != null) {
            gameView.stopLoading();
            gameView.destroy();
            gameView = null;
        }
        super.onDestroy();
    }
}
