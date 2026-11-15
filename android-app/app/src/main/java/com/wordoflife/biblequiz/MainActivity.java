package com.wordoflife.biblequiz;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
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
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd;
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdEventCallback;

public class MainActivity extends Activity {
    private static final String TAG = "WordOfLifeAds";

    // Word of Life AdMob app ID and rewarded ad unit created in the publisher account.
    // Do not click or test live ads. Use Google's test ad units during development.
    private static final String ADMOB_APP_ID =
            "ca-app-pub-5491156476201135~4571929038";
    private static final String REWARDED_AD_UNIT_ID =
            "ca-app-pub-5491156476201135/5515143916";
    private static final String INTERSTITIAL_AD_UNIT_ID =
            "ca-app-pub-5491156476201135/4212836388";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;
    private FrameLayout appRoot;
    private View offlinePanel;
    private boolean gameStarted = false;
    private boolean adsInitializationStarted = false;
    private WebView gameView;
    private RewardedAd rewardedAd;
    private InterstitialAd interstitialAd;
    private boolean interstitialLoadInProgress = false;
    private int pendingLevelEndAds = 0;
    private String interstitialStatusMessage = "Preparing end-of-level ad…";
    private boolean adLoadInProgress = false;
    private boolean pendingRewardedShow = false;
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

        connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);

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
                updateWebInterstitialState();
            }
        });
        gameView.setWebChromeClient(new WebChromeClient());

        // The rewarded video is only shown after the player taps the Watch Ad button.
        gameView.addJavascriptInterface(new AdsBridge(), "AndroidAds");

        appRoot = new FrameLayout(this);
        appRoot.addView(gameView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        offlinePanel = createOfflinePanel(deepGreen);
        offlinePanel.setVisibility(View.GONE);
        appRoot.addView(offlinePanel, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        setContentView(appRoot);

        registerNetworkCallback();
        refreshOnlineState();
    }

    // The game is deliberately unavailable without a validated internet connection.
    private boolean hasValidatedInternet() {
        if (connectivityManager == null) {
            return false;
        }
        Network activeNetwork = connectivityManager.getActiveNetwork();
        if (activeNetwork == null) {
            return false;
        }
        NetworkCapabilities capabilities =
                connectivityManager.getNetworkCapabilities(activeNetwork);
        return capabilities != null
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }

    private void registerNetworkCallback() {
        if (connectivityManager == null || networkCallback != null) {
            return;
        }
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                mainHandler.postDelayed(MainActivity.this::refreshOnlineState, 300L);
            }

            @Override
            public void onLost(@NonNull Network network) {
                mainHandler.post(MainActivity.this::refreshOnlineState);
            }

            @Override
            public void onCapabilitiesChanged(
                    @NonNull Network network,
                    @NonNull NetworkCapabilities capabilities) {
                mainHandler.post(MainActivity.this::refreshOnlineState);
            }
        };
        try {
            connectivityManager.registerDefaultNetworkCallback(networkCallback);
        } catch (RuntimeException error) {
            Log.e(TAG, "Unable to monitor internet connection.", error);
        }
    }

    private View createOfflinePanel(int backgroundColor) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER);
        int padding = (int) (28 * getResources().getDisplayMetrics().density);
        panel.setPadding(padding, padding, padding, padding);
        panel.setBackgroundColor(backgroundColor);

        TextView symbol = new TextView(this);
        symbol.setText("📶");
        symbol.setTextSize(42);
        symbol.setGravity(Gravity.CENTER);
        panel.addView(symbol);

        TextView title = new TextView(this);
        title.setText("Internet connection required");
        title.setTextColor(Color.rgb(255, 248, 232));
        title.setTextSize(23);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, padding / 2, 0, padding / 3);
        panel.addView(title);

        TextView message = new TextView(this);
        message.setText("Word of Life works online only. Connect to Wi-Fi or mobile data to play, then tap Try Again.");
        message.setTextColor(Color.rgb(184, 198, 189));
        message.setTextSize(15);
        message.setGravity(Gravity.CENTER);
        panel.addView(message);

        Button retry = new Button(this);
        retry.setText("TRY AGAIN");
        retry.setOnClickListener(view -> refreshOnlineState());
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        buttonParams.topMargin = padding;
        panel.addView(retry, buttonParams);
        return panel;
    }

    private void refreshOnlineState() {
        if (isFinishing() || gameView == null || offlinePanel == null) {
            return;
        }
        boolean online = hasValidatedInternet();
        if (!online) {
            gameView.setVisibility(View.GONE);
            offlinePanel.setVisibility(View.VISIBLE);
            return;
        }

        offlinePanel.setVisibility(View.GONE);
        gameView.setVisibility(View.VISIBLE);

        if (!gameStarted) {
            gameStarted = true;
            gameView.loadUrl("file:///android_asset/www/index.html");
        }
        if (!adsInitializationStarted) {
            adsInitializationStarted = true;
            initializeAds();
        }
    }

    private void initializeAds() {
        updateStatus("Starting Google's rewarded ad service…");
        new Thread(() -> MobileAds.initialize(
                getApplicationContext(),
                new InitializationConfig.Builder(ADMOB_APP_ID).build(),
                initializationStatus -> runOnUiThread(() -> {
                    Log.i(TAG, "Google Mobile Ads SDK initialization callback received.");
                    updateStatus("Google ad service initialized. Preparing a rewarded ad…");
                    loadRewardedAd();
                    loadInterstitialAd();
                })
        ), "word-of-life-ads-init").start();
    }

    // Automatic full-screen interstitial shown at the end of each level.
    // This is separate from the optional rewarded video that grants a hint.
    private void loadInterstitialAd() {
        if (isFinishing() || interstitialLoadInProgress || interstitialAd != null) {
            return;
        }

        interstitialLoadInProgress = true;
        setInterstitialStatus("Loading end-of-level ad…");

        InterstitialAd.load(
                new AdRequest.Builder(INTERSTITIAL_AD_UNIT_ID).build(),
                new AdLoadCallback<InterstitialAd>() {
                    @Override
                    public void onAdLoaded(@NonNull InterstitialAd ad) {
                        runOnUiThread(() -> {
                            interstitialLoadInProgress = false;
                            interstitialAd = ad;
                            Log.i(TAG, "Interstitial ad loaded successfully.");
                            setInterstitialStatus("End-of-level ad is ready.");
                            showPendingLevelEndAd();
                        });
                    }

                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError error) {
                        runOnUiThread(() -> {
                            interstitialLoadInProgress = false;
                            interstitialAd = null;
                            String message = "End-of-level ad unavailable (code "
                                    + error.getCode() + "): " + error.getMessage();
                            Log.e(TAG, message);
                            setInterstitialStatus(message);
                            // Preserve a pending level-end request and retry later.
                            mainHandler.postDelayed(() -> {
                                if (!isFinishing() && interstitialAd == null) {
                                    loadInterstitialAd();
                                }
                            }, 30000L);
                        });
                    }
                }
        );
    }

    private void queueLevelEndAd(int levelId) {
        if (levelId < 1 || levelId > 100 || isFinishing()) {
            return;
        }
        pendingLevelEndAds++;
        Log.i(TAG, "Level " + levelId + " completed. Pending normal ads: "
                + pendingLevelEndAds);
        setInterstitialStatus("Level completed. Preparing the normal full-screen ad…");
        showPendingLevelEndAd();
        loadInterstitialAd();
    }

    private void showPendingLevelEndAd() {
        if (isFinishing() || pendingLevelEndAds <= 0) {
            return;
        }
        if (interstitialAd == null) {
            setInterstitialStatus(interstitialLoadInProgress
                    ? "Level completed. Waiting for the normal ad to load…"
                    : "Level completed. Requesting the normal ad…");
            loadInterstitialAd();
            return;
        }

        InterstitialAd ad = interstitialAd;
        interstitialAd = null;
        pendingLevelEndAds--;
        setInterstitialStatus("Showing the normal full-screen ad…");

        ad.setAdEventCallback(new InterstitialAdEventCallback() {
            @Override
            public void onAdShowedFullScreenContent() {
                runOnUiThread(() ->
                        setInterstitialStatus("Normal full-screen ad is showing."));
            }

            @Override
            public void onAdImpression() {
                Log.i(TAG, "Interstitial ad impression recorded.");
            }

            @Override
            public void onAdDismissedFullScreenContent() {
                runOnUiThread(() -> {
                    setInterstitialStatus("Normal ad closed. Preparing the next one…");
                    loadInterstitialAd();
                });
            }

            @Override
            public void onAdFailedToShowFullScreenContent(
                    @NonNull FullScreenContentError error) {
                runOnUiThread(() -> {
                    String message = "Normal ad could not be shown: " + error.getMessage();
                    Log.e(TAG, message);
                    setInterstitialStatus(message);
                    loadInterstitialAd();
                });
            }
        });

        ad.show(this);
    }

    private void setInterstitialStatus(String message) {
        interstitialStatusMessage = message;
        updateWebInterstitialState();
    }

    private void updateWebInterstitialState() {
        if (gameView == null || !gamePageLoaded) {
            return;
        }

        String script = "if(window.setInterstitialAdState){window.setInterstitialAdState("
                + JSONObject.quote(interstitialStatusMessage) + ");}";
        gameView.post(() -> {
            if (gameView != null) {
                gameView.evaluateJavascript(script, null);
            }
        });
    }

    private void loadRewardedAd() {
        if (isFinishing() || adLoadInProgress || rewardedAd != null) {
            return;
        }

        adLoadInProgress = true;
        updateStatus("Loading optional rewarded test ad…");

        RewardedAd.load(
                new AdRequest.Builder(REWARDED_AD_UNIT_ID).build(),
                new AdLoadCallback<RewardedAd>() {
                    @Override
                    public void onAdLoaded(@NonNull RewardedAd ad) {
                        runOnUiThread(() -> {
                            adLoadInProgress = false;
                            rewardedAd = ad;
                            Log.i(TAG, "Rewarded ad loaded successfully.");
                            updateStatus("Rewarded ad is ready. Tap WATCH AD FOR 1 HINT.");
                            if (pendingRewardedShow) {
                                pendingRewardedShow = false;
                                showRewardedAd();
                            }
                        });
                    }

                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError error) {
                        runOnUiThread(() -> {
                            adLoadInProgress = false;
                            rewardedAd = null;
                            String message = error.getMessage();
                            Log.e(TAG, "Rewarded ad failed to load (code " + error.getCode() + "): " + message);
                            pendingRewardedShow = false;
                            updateStatus("Rewarded ad unavailable (code " + error.getCode() + "): " + message);
                            notifyRewardedAdUnavailable("Rewarded video unavailable right now. Please try again shortly.");
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
            pendingRewardedShow = true;
            updateStatus(adLoadInProgress
                    ? "The rewarded ad is still loading. It will open automatically when ready."
                    : "No rewarded ad is ready. Loading it now; it will open automatically when ready…");
            loadRewardedAd();
            return;
        }

        pendingRewardedShow = false;
        RewardedAd ad = rewardedAd;
        rewardedAd = null;
        updateStatus("Opening the rewarded ad…");

        ad.setAdEventCallback(new RewardedAdEventCallback() {
            @Override
            public void onAdShowedFullScreenContent() {
                runOnUiThread(() -> updateStatus("Rewarded ad is playing…"));
            }

            @Override
            public void onAdImpression() {
                Log.i(TAG, "Rewarded test ad impression recorded.");
            }

            @Override
            public void onAdDismissedFullScreenContent() {
                runOnUiThread(() -> {
                    notifyRewardedAdClosed();
                    updateStatus("Ad closed. Preparing the next optional rewarded ad…");
                    loadRewardedAd();
                });
            }

            @Override
            public void onAdFailedToShowFullScreenContent(@NonNull FullScreenContentError error) {
                runOnUiThread(() -> {
                    String message = error.getMessage();
                    Log.e(TAG, "Rewarded ad failed to show: " + message);
                    notifyRewardedAdClosed();
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

    private void notifyRewardedAdClosed() {
        if (gameView != null && gamePageLoaded) {
            gameView.evaluateJavascript(
                    "if(window.onRewardedAdClosed){window.onRewardedAdClosed();}",
                    null
            );
        }
    }

    private void notifyRewardedAdUnavailable(String message) {
        if (gameView != null && gamePageLoaded) {
            gameView.evaluateJavascript(
                    "if(window.onRewardedAdUnavailable){window.onRewardedAdUnavailable("
                            + JSONObject.quote(message) + ");}",
                    null
            );
        }
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
                String interstitialScript = "if(window.setInterstitialAdState){window.setInterstitialAdState("
                        + JSONObject.quote(interstitialStatusMessage) + ");}";
                gameView.evaluateJavascript(interstitialScript, null);
            }
        });
    }

    private final class AdsBridge {
        @JavascriptInterface
        public void onLevelCompleted(int levelId) {
            runOnUiThread(() -> queueLevelEndAd(levelId));
        }

        @JavascriptInterface
        public void showRewardedAd() {
            runOnUiThread(MainActivity.this::showRewardedAd);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mainHandler.post(this::refreshOnlineState);
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
        if (connectivityManager != null && networkCallback != null) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback);
            } catch (IllegalArgumentException ignored) {
                // Callback was already removed by the system.
            }
            networkCallback = null;
        }
        if (gameView != null) {
            gameView.stopLoading();
            gameView.destroy();
            gameView = null;
        }
        super.onDestroy();
    }
}
