package com.myfit.tracker.ai.ondevice

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.myfit.tracker.BuildConfig
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Optional rewarded video ads, only when the user taps "Watch an ad" after the free daily AI allowance
 * runs out — never on health, cycle, glucose or mental-health screens. Consent (UMP) is asked first where
 * required; the ads SDK is started on that first tap, never at app launch. Without the ADMOB_* CI secrets
 * Google's public test ads are used.
 */
object RewardedAds {
    @Volatile private var started = false

    private fun start(ctx: Context) {
        if (started) return
        MobileAds.initialize(ctx.applicationContext) {}
        started = true
    }

    private suspend fun load(ctx: Context): RewardedAd = suspendCancellableCoroutine { cont ->
        RewardedAd.load(ctx, BuildConfig.ADMOB_REWARDED_ID, AdRequest.Builder().build(), object : RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) { if (cont.isActive) cont.resume(ad) }
            override fun onAdFailedToLoad(err: LoadAdError) {
                if (cont.isActive) cont.resumeWith(Result.failure(IllegalStateException(if (err.code == AdRequest.ERROR_CODE_NO_FILL) "No ad is available right now — try again in a little while." else "Couldn't load an ad (${err.message}).")))
            }
        })
    }

    /**
     * Google UMP consent: asks (once, where the law requires it — EEA/UK/US states) before any ad request.
     * Returns whether ads may be requested.
     */
    private suspend fun consent(activity: Activity): Boolean {
        val info = com.google.android.ump.UserMessagingPlatform.getConsentInformation(activity)
        val params = com.google.android.ump.ConsentRequestParameters.Builder().setTagForUnderAgeOfConsent(false).build()
        suspendCancellableCoroutine<Unit> { cont ->
            info.requestConsentInfoUpdate(activity, params,
                { com.google.android.ump.UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { if (cont.isActive) cont.resume(Unit) } },
                { if (cont.isActive) cont.resume(Unit) })
        }
        return info.canRequestAds()
    }

    /** Loads and shows one ad. Returns true only if the user watched it to the reward. */
    suspend fun show(activity: Activity): Boolean {
        if (!consent(activity)) throw IllegalStateException("Ads need your consent first — you can change this in Settings → AI → Privacy options.")
        start(activity)
        val ad = load(activity)
        return suspendCancellableCoroutine { cont ->
            var earned = false
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() { if (cont.isActive) cont.resume(earned) }
                override fun onAdFailedToShowFullScreenContent(e: com.google.android.gms.ads.AdError) { if (cont.isActive) cont.resume(false) }
            }
            ad.show(activity) { earned = true }
        }
    }

    /** Re-open the consent choices (shown in AI settings when the law requires a privacy-options entry point). */
    fun privacyOptions(activity: Activity) {
        com.google.android.ump.UserMessagingPlatform.showPrivacyOptionsForm(activity) { }
    }

    fun privacyOptionsRequired(ctx: Context): Boolean = runCatching {
        com.google.android.ump.UserMessagingPlatform.getConsentInformation(ctx).privacyOptionsRequirementStatus ==
            com.google.android.ump.ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
    }.getOrDefault(false)

    val usingTestAds: Boolean get() = BuildConfig.ADMOB_REWARDED_ID.startsWith("ca-app-pub-3940256099942544")
}
