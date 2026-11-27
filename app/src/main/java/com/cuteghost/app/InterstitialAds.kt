package com.cuteghost.app

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

/**
 * Short full-screen ads at NATURAL BREAKS only (the end of a game, when the child chooses to leave a game
 * after playing a few rounds): never at app start, never at app exit, never during play, never twice in a row.
 * Configured for children by [AdMobProvider.start]. Disabled until an interstitial unit id is supplied.
 */
object InterstitialAds {
    private const val PREFS = "ghost_interstitial"
    private const val MIN_GAP_MS = 4L * 60L * 1000L
    private const val DAILY_CAP = 3

    private var ad: InterstitialAd? = null
    private var loading = false
    private var showing = false

    private fun enabled() = AdsConfig.INTERSTITIAL_UNIT_ID.isNotBlank()

    fun preload(context: Context) {
        if (!enabled() || ad != null || loading) return
        AdMobProvider.start(context)
        loading = true
        InterstitialAd.load(
            context.applicationContext,
            AdsConfig.INTERSTITIAL_UNIT_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(loaded: InterstitialAd) {
                    ad = loaded
                    loading = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    ad = null
                    loading = false
                }
            }
        )
    }

    /** Shows an ad if the spacing and daily limits allow it, then always calls [onDone] exactly once. */
    fun maybeShow(activity: Activity, onDone: () -> Unit) {
        val loaded = ad
        val p = activity.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val today = (now / 86_400_000L).toInt()
        val count = if (p.getInt("day", -1) == today) p.getInt("count", 0) else 0
        val allowed = enabled() && !showing && loaded != null &&
            now - p.getLong("last", 0L) >= MIN_GAP_MS && count < DAILY_CAP
        if (!allowed || loaded == null) {
            preload(activity)
            onDone()
            return
        }
        ad = null
        showing = true
        p.edit().putInt("day", today).putInt("count", count + 1).putLong("last", now).apply()
        loaded.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                showing = false
                onDone()
                preload(activity)
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                showing = false
                onDone()
                preload(activity)
            }
        }
        loaded.show(activity)
    }
}
