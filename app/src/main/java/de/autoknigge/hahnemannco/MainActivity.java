package de.autoknigge.hahnemannco;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.appopen.AppOpenAd;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.ConsentForm;
import com.google.android.ump.UserMessagingPlatform;

import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends Activity {
    private WebView web;
    private final AtomicBoolean adsStarted = new AtomicBoolean(false);
    private final long startTime = System.currentTimeMillis();
    private boolean appOpenShown = false;
    private boolean resumed = false;
    private AppOpenAd appOpenAd;
    private InterstitialAd interstitial;
    private int opened = 0;
    private long lastInterstitial = 0;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        web = new WebView(this);
        setContentView(web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(false);
        s.setAllowFileAccessFromFileURLs(false);
        s.setAllowUniversalAccessFromFileURLs(false);
        s.setTextZoom(100);
        web.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void shareText(final String title, final String text) {
                runOnUiThread(new Runnable() { public void run() {
                    Intent i = new Intent(Intent.ACTION_SEND);
                    i.setType("text/plain");
                    i.putExtra(Intent.EXTRA_SUBJECT, title);
                    i.putExtra(Intent.EXTRA_TEXT, text);
                    startActivity(Intent.createChooser(i, title));
                } });
            }
            @JavascriptInterface
            public void sectionOpened() {
                runOnUiThread(new Runnable() { public void run() { onSectionOpened(); } });
            }
            @JavascriptInterface
            public boolean privacyOptionsRequired() {
                return UserMessagingPlatform.getConsentInformation(MainActivity.this).getPrivacyOptionsRequirementStatus()
                    == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED;
            }
            @JavascriptInterface
            public void showPrivacyOptions() {
                runOnUiThread(new Runnable() { public void run() {
                    UserMessagingPlatform.showPrivacyOptionsForm(MainActivity.this,
                        new ConsentForm.OnConsentFormDismissedListener() {
                            @Override public void onConsentFormDismissed(com.google.android.ump.FormError e) {
                                if (UserMessagingPlatform.getConsentInformation(MainActivity.this).canRequestAds()) initAds();
                            }
                        });
                } });
            }
        }, "HNAndroid");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                if ("file".equals(u.getScheme())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception e) { }
                return true;
            }
        });
        if (b != null) web.restoreState(b);
        else web.loadUrl("file:///android_asset/index.html");
        startConsentAndAds();
    }

    /** Einwilligung (UMP/DSGVO) einholen, erst danach Werbung laden. */
    private void startConsentAndAds() {
        final ConsentInformation ci = UserMessagingPlatform.getConsentInformation(this);
        ConsentRequestParameters params = new ConsentRequestParameters.Builder().build();
        ci.requestConsentInfoUpdate(this, params,
            new ConsentInformation.OnConsentInfoUpdateSuccessListener() {
                @Override public void onConsentInfoUpdateSuccess() {
                    UserMessagingPlatform.loadAndShowConsentFormIfRequired(MainActivity.this,
                        new com.google.android.ump.ConsentForm.OnConsentFormDismissedListener() {
                            @Override public void onConsentFormDismissed(com.google.android.ump.FormError e) {
                                if (ci.canRequestAds()) initAds();
                            }
                        });
                }
            },
            new ConsentInformation.OnConsentInfoUpdateFailureListener() {
                @Override public void onConsentInfoUpdateFailure(com.google.android.ump.FormError e) {
                    if (ci.canRequestAds()) initAds();
                }
            });
        if (ci.canRequestAds()) initAds();
    }

    private void initAds() {
        if (!adsStarted.compareAndSet(false, true)) return;
        MobileAds.initialize(this, status -> {
            runOnUiThread(() -> { loadAppOpen(); loadInterstitial(); });
        });
    }

    private void loadAppOpen() {
        AppOpenAd.load(this, BuildConfig.APP_OPEN_ID, new AdRequest.Builder().build(),
            new AppOpenAd.AppOpenAdLoadCallback() {
                @Override public void onAdLoaded(AppOpenAd ad) {
                    appOpenAd = ad;
                    showAppOpenIfFresh();
                }
                @Override public void onAdFailedToLoad(LoadAdError e) { appOpenAd = null; }
            });
    }

    /** App-Start-Anzeige nur beim Kaltstart und nur, wenn sie rasch genug da ist. */
    private void showAppOpenIfFresh() {
        if (appOpenAd == null || appOpenShown || !resumed) return;
        if (System.currentTimeMillis() - startTime > 5000) { appOpenAd = null; return; }
        appOpenShown = true;
        lastInterstitial = System.currentTimeMillis();
        appOpenAd.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override public void onAdDismissedFullScreenContent() { appOpenAd = null; }
            @Override public void onAdFailedToShowFullScreenContent(AdError e) { appOpenAd = null; }
        });
        appOpenAd.show(this);
    }

    private void loadInterstitial() {
        InterstitialAd.load(this, BuildConfig.INTERSTITIAL_ID, new AdRequest.Builder().build(),
            new InterstitialAdLoadCallback() {
                @Override public void onAdLoaded(InterstitialAd ad) { interstitial = ad; }
                @Override public void onAdFailedToLoad(LoadAdError e) { interstitial = null; }
            });
    }

    /** Zwischenanzeige selten: nach 12 geöffneten Abschnitten und mindestens 4 Minuten Abstand. */
    private void onSectionOpened() {
        opened++;
        long now = System.currentTimeMillis();
        if (interstitial == null || opened < 12 || now - lastInterstitial < 240000) return;
        opened = 0;
        lastInterstitial = now;
        interstitial.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override public void onAdDismissedFullScreenContent() { interstitial = null; loadInterstitial(); }
            @Override public void onAdFailedToShowFullScreenContent(AdError e) { interstitial = null; loadInterstitial(); }
        });
        interstitial.show(this);
    }

    @Override protected void onResume() { super.onResume(); resumed = true; showAppOpenIfFresh(); }
    @Override protected void onPause() { resumed = false; super.onPause(); }

    @Override
    protected void onSaveInstanceState(Bundle o) { super.onSaveInstanceState(o); web.saveState(o); }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack(); else super.onBackPressed();
    }
}
