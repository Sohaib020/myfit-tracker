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
 * runs out. The ads SDK is started on that first tap, never at app launch. Without the ADMOB_* CI secrets
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

    /** Loads and shows one ad. Returns true only if the user watched it to the reward. */
    suspend fun show(activity: Activity): Boolean {
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

    val usingTestAds: Boolean get() = BuildConfig.ADMOB_REWARDED_ID.startsWith("ca-app-pub-3940256099942544")
}
