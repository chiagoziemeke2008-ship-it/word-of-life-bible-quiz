package com.wordoflife.biblequiz;

import android.app.Activity;
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
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAd;
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAdEventCallback;
import com.google.android.libraries.ads.mobile.sdk.rewarded.OnUserEarnedRewardListener;
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardItem;

public class MainActivity extends Activity {
    private static final String TAG = "WordOfLifeAds";

    // Google's official demo app ID and rewarded test unit ID. Test ads never pay.
    // Replace with the Word of Life app ID and rewarded unit ID from AdMob before release.
    private static final String TEST_ADMOB_APP_ID =
            "ca-app-pub-3940256099942544~3347511713";
    private static final String TEST_REWARDED_AD_UNIT_ID =
            "ca-app-pub-3940256099942544/5224354917";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private WebView gameView;
    private RewardedAd rewardedAd;
    private boolean adLoadInProgress = false;
    private boolean gamePageLoaded = false;
    private String adStatusMessage = "Preparing an optional rewarded test ad…";

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
                updateWebRewardState();
            }
        });
        gameView.setWebChromeClient(new WebChromeClient());

        // The rewarded video is only shown after the player taps the Watch Ad button.
        gameView.addJavascriptInterface(new AdsBridge(), "AndroidAds");
        setContentView(gameView);
        gameView.loadUrl("file:///android_asset/www/index.html");

        initializeAds();
    }

    private void initializeAds() {
        updateStatus("Starting Google's rewarded test ad service…");
        new Thread(() -> MobileAds.initialize(
                getApplicationContext(),
                new InitializationConfig.Builder(TEST_ADMOB_APP_ID).build(),
                initializationStatus -> runOnUiThread(() -> {
                    Log.i(TAG, "Google Mobile Ads SDK initialization callback received.");
                    updateStatus("Google ad service initialized. Preparing a rewarded test ad…");
                    loadRewardedAd();
                })
        ), "word-of-life-ads-init").start();
    }

    private void loadRewardedAd() {
        if (isFinishing() || adLoadInProgress || rewardedAd != null) {
            return;
        }

        adLoadInProgress = true;
        updateStatus("Loading optional rewarded test ad…");

        RewardedAd.load(
                new AdRequest.Builder(TEST_REWARDED_AD_UNIT_ID).build(),
                new AdLoadCallback<RewardedAd>() {
                    @Override
                    public void onAdLoaded(@NonNull RewardedAd ad) {
                        runOnUiThread(() -> {
                            adLoadInProgress = false;
                            rewardedAd = ad;
                            Log.i(TAG, "Rewarded test ad loaded successfully.");
                            updateStatus("TEST AD LOADED. Tap WATCH AD FOR 1 HINT.");
                        });
                    }

                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError error) {
                        runOnUiThread(() -> {
                            adLoadInProgress = false;
                            rewardedAd = null;
                            String message = error.getMessage();
                            Log.e(TAG, "Rewarded test ad failed to load (code " + error.getCode() + "): " + message);
                            updateStatus("Rewarded ad unavailable (code " + error.getCode() + "): " + message);
                            // Retry later. The quiz remains fully playable if no ad is available.
                            mainHandler.postDelayed(() -> {
                                if (!isFinishing() && rewardedAd == null) {
                                    loadRewardedAd();
                                }
                            }, 30000L);
                        });
                    }
                }
        );
    }

    private void showRewardedAd() {
        if (isFinishing()) {
            return;
        }

        if (rewardedAd == null) {
            updateStatus(adLoadInProgress
                    ? "The test ad is still loading. Please try again in a moment."
                    : "No test ad is ready. Requesting another test ad now…");
            loadRewardedAd();
            return;
        }

        RewardedAd ad = rewardedAd;
        rewardedAd = null;
        updateStatus("Opening the rewarded test ad…");

        ad.setAdEventCallback(new RewardedAdEventCallback() {
            @Override
            public void onAdShowedFullScreenContent() {
                runOnUiThread(() -> updateStatus("Rewarded test ad is playing…"));
            }

            @Override
            public void onAdImpression() {
                Log.i(TAG, "Rewarded test ad impression recorded.");
            }

            @Override
            public void onAdDismissedFullScreenContent() {
                runOnUiThread(() -> {
                    updateStatus("Ad closed. Preparing the next optional rewarded ad…");
                    loadRewardedAd();
                });
            }

            @Override
            public void onAdFailedToShowFullScreenContent(@NonNull FullScreenContentError error) {
                runOnUiThread(() -> {
                    String message = error.getMessage();
                    Log.e(TAG, "Rewarded ad failed to show: " + message);
                    updateStatus("Rewarded ad could not be shown: " + message);
                    loadRewardedAd();
                });
            }
        });

        ad.show(this, new OnUserEarnedRewardListener() {
            @Override
            public void onUserEarnedReward(@NonNull RewardItem rewardItem) {
                runOnUiThread(() -> {
                    Log.i(TAG, "User earned reward: "
                            + rewardItem.getAmount() + " " + rewardItem.getType());
                    if (gameView != null && gamePageLoaded) {
                        gameView.evaluateJavascript(
                                "if(window.onRewardedAdEarned){window.onRewardedAdEarned();}",
                                null
                        );
                    }
                });
            }
        });
    }

    private void updateStatus(String message) {
        adStatusMessage = message;
        updateWebRewardState();
    }

    private void updateWebRewardState() {
        if (gameView == null || !gamePageLoaded) {
            return;
        }

        boolean ready = rewardedAd != null;
        String script = "if(window.setRewardedAdState){window.setRewardedAdState("
                + ready + "," + JSONObject.quote(adStatusMessage) + "," + adLoadInProgress + ");}";
        gameView.post(() -> {
            if (gameView != null) {
                gameView.evaluateJavascript(script, null);
            }
        });
    }

    private final class AdsBridge {
        @JavascriptInterface
        public void showRewardedAd() {
            runOnUiThread(MainActivity.this::showRewardedAd);
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
