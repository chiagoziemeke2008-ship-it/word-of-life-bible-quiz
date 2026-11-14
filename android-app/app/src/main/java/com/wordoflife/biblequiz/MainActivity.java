package com.wordoflife.biblequiz;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.google.android.libraries.ads.mobile.sdk.MobileAds;
import com.google.android.libraries.ads.mobile.sdk.initialization.InitializationConfig;
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest;
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError;
import com.google.android.libraries.ads.mobile.sdk.common.PreloadCallback;
import com.google.android.libraries.ads.mobile.sdk.common.PreloadConfiguration;
import com.google.android.libraries.ads.mobile.sdk.common.ResponseInfo;
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd;
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdEventCallback;
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdPreloader;

public class MainActivity extends Activity {
    private static final String TAG = "WordOfLifeAds";

    // Google's official demo IDs are used for testing only.
    // Replace both with this app's real AdMob IDs before public release.
    private static final String TEST_ADMOB_APP_ID =
            "ca-app-pub-3940256099942544~3347511713";
    private static final String TEST_INTERSTITIAL_AD_UNIT_ID =
            "ca-app-pub-3940256099942544/1033173712";

    private static final String AD_PREFS = "word_of_life_ad_state";
    private static final String COMPLETED_LEVELS_KEY = "completed_levels";
    private static final int LEVELS_PER_INTERSTITIAL = 2;

    private WebView gameView;

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

        gameView.setWebViewClient(new WebViewClient());
        gameView.setWebChromeClient(new WebChromeClient());

        // Only the locally bundled game is loaded in this WebView.
        gameView.addJavascriptInterface(new AdsBridge(), "AndroidAds");
        setContentView(gameView);
        gameView.loadUrl("file:///android_asset/www/index.html");

        initializeAds();
    }

    private void initializeAds() {
        // Google requires SDK initialization away from the UI thread.
        new Thread(() -> MobileAds.initialize(
                this,
                new InitializationConfig.Builder(TEST_ADMOB_APP_ID).build(),
                initializationStatus -> runOnUiThread(this::startInterstitialPreloading)
        ), "word-of-life-ads-init").start();
    }

    private void startInterstitialPreloading() {
        AdRequest request = new AdRequest.Builder(TEST_INTERSTITIAL_AD_UNIT_ID).build();
        PreloadConfiguration configuration = new PreloadConfiguration(request);

        PreloadCallback callback = new PreloadCallback() {
            @Override
            public void onAdPreloaded(String preloadId, ResponseInfo responseInfo) {
                Log.d(TAG, "Test interstitial preloaded.");
            }

            @Override
            public void onAdFailedToPreload(String preloadId, LoadAdError error) {
                Log.w(TAG, "Interstitial preload failed: " + error.getMessage());
            }

            @Override
            public void onAdsExhausted(String preloadId) {
                Log.d(TAG, "Interstitial preload cache is empty.");
            }
        };

        InterstitialAdPreloader.start(
                TEST_INTERSTITIAL_AD_UNIT_ID,
                configuration,
                callback
        );
    }

    private void showInterstitialIfAvailable() {
        if (isFinishing()) {
            return;
        }

        InterstitialAd ad = InterstitialAdPreloader.pollAd(TEST_INTERSTITIAL_AD_UNIT_ID);
        if (ad == null) {
            // No ad was available (for example, no connection or no ad fill).
            // The player can continue; the next ad is still preloaded by the SDK.
            Log.d(TAG, "No test interstitial was available at the level break.");
            return;
        }

        ad.setAdEventCallback(new InterstitialAdEventCallback() {
            @Override
            public void onAdImpression() {
                Log.d(TAG, "Interstitial ad impression recorded.");
            }
        });
        ad.show(this);
    }

    private final class AdsBridge {
        @JavascriptInterface
        public void onLevelPassed(int levelId) {
            if (levelId < 1 || levelId > 100) {
                return;
            }

            SharedPreferences preferences =
                    getSharedPreferences(AD_PREFS, MODE_PRIVATE);
            int completedLevels =
                    preferences.getInt(COMPLETED_LEVELS_KEY, 0) + 1;
            preferences.edit()
                    .putInt(COMPLETED_LEVELS_KEY, completedLevels)
                    .apply();

            // Count passed levels only. Show a full-screen ad after each pair.
            if (completedLevels % LEVELS_PER_INTERSTITIAL == 0) {
                runOnUiThread(MainActivity.this::showInterstitialIfAvailable);
            }
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
        InterstitialAdPreloader.destroy(TEST_INTERSTITIAL_AD_UNIT_ID);
        if (gameView != null) {
            gameView.stopLoading();
            gameView.destroy();
            gameView = null;
        }
        super.onDestroy();
    }
}
