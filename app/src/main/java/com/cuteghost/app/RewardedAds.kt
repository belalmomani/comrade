package com.cuteghost.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.util.UUID
import java.util.TimeZone
import kotlin.random.Random

/** Something that can show an optional rewarded ad. A real one is plugged in at [RewardedAds.provider]. */
interface AdProvider {
    val name: String
    fun isReady(): Boolean

    /** Shows the ad and reports whether it was watched to the end (only then is the reward given). */
    fun show(activity: Activity, onResult: (Boolean) -> Unit)
}

/**
 * Google AdMob rewarded ads, configured for children (Google Play Families policy):
 * child-directed treatment, under-age-of-consent, G content rating only, no advertising id
 * (the AD_ID permission is removed in the manifest). Ads are only shown by [RewardedAds.watch],
 * after the person clearly chooses to watch one.
 */
object AdMobProvider : AdProvider {
    override val name = "admob"

    private val main = Handler(Looper.getMainLooper())
    private var appContext: Context? = null
    private var started = false
    private var ready = false
    private var loading = false
    private var ad: RewardedAd? = null

    fun start(context: Context) {
        if (started) {
            main.post { load() }
            return
        }
        started = true
        val app = context.applicationContext
        appContext = app
        val config = MobileAds.getRequestConfiguration().toBuilder()
            .setTagForChildDirectedTreatment(RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_TRUE)
            .setTagForUnderAgeOfConsent(RequestConfiguration.TAG_FOR_UNDER_AGE_OF_CONSENT_TRUE)
            .setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_G)
            .build()
        MobileAds.setRequestConfiguration(config)
        MobileAds.initialize(app) {
            ready = true
            main.post { load() }
        }
    }

    private fun load() {
        val ctx = appContext ?: return
        if (!ready || loading || ad != null) return
        loading = true
        RewardedAd.load(
            ctx,
            AdsConfig.REWARDED_UNIT_ID,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(loaded: RewardedAd) {
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

    /** One anchored banner for the grown-ups' panel only (never on the child's screens). */
    fun createBanner(activity: Activity): AdView {
        start(activity)
        val dm = activity.resources.displayMetrics
        val widthDp = (dm.widthPixels / dm.density).toInt()
        val view = AdView(activity)
        view.setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, widthDp))
        view.adUnitId = AdsConfig.BANNER_UNIT_ID
        view.loadAd(AdRequest.Builder().build())
        return view
    }

    override fun isReady(): Boolean = ad != null

    override fun show(activity: Activity, onResult: (Boolean) -> Unit) {
        val rewarded = ad
        if (rewarded == null) {
            onResult(false)
            load()
            return
        }
        ad = null
        var earned = false
        rewarded.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                onResult(earned)
                load()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                onResult(false)
                load()
            }
        }
        rewarded.show(activity) { earned = true }
    }
}

/**
 * Optional rewarded ads, never forced: the reward is named before the
 * ad starts, the person chooses to watch, ads are spaced out and limited per day, and the reward is
 * given only when the provider says the ad was watched to the end, once per ad.
 */
object RewardedAds {
    private const val PREFS = "ghost_ads"
    private const val MIN_GAP_MS = 3L * 60L * 1000L
    private const val MAX_PER_DAY = 5

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The provider in use: AdMob, set up for children. */
    fun provider(context: Context): AdProvider {
        AdMobProvider.start(context)
        return AdMobProvider
    }

    private fun dayNumber(): Int {
        val now = System.currentTimeMillis()
        return ((now + TimeZone.getDefault().getOffset(now)) / 86_400_000L).toInt()
    }

    /** Why an ad cannot be shown now, or null when it can. */
    fun blockedReason(context: Context): String? {
        if (!provider(context).isReady()) {
            return "الإعلان غير جاهز الآن، حاولوا بعد قليل"
        }
        val p = prefs(context)
        if (System.currentTimeMillis() - p.getLong("last", 0L) < MIN_GAP_MS) return "انتظر قليلًا بين إعلان وآخر"
        val count = if (p.getInt("day", -1) == dayNumber()) p.getInt("count", 0) else 0
        if (count >= MAX_PER_DAY) return "وصلتم إلى حد الإعلانات لهذا اليوم، عودوا غدًا"
        return null
    }

    /** The grown-up question, the named reward, then the ad. [onGranted] runs once, only after a full view. */
    fun watch(activity: Activity, offer: String, onGranted: () -> Unit) {
        val reason = blockedReason(activity)
        if (reason != null) {
            Toast.makeText(activity, reason, Toast.LENGTH_LONG).show()
            return
        }
        askParent(activity, offer) {
            val id = UUID.randomUUID().toString()
            prefs(activity).edit().putString("pending", id).apply()
            provider(activity).show(activity) { watched ->
                val p = prefs(activity)
                if (watched && p.getString("pending", "") == id) {
                    // One reward for one ad: the pending number is used up
                    val today = dayNumber()
                    val count = if (p.getInt("day", -1) == today) p.getInt("count", 0) else 0
                    p.edit().putString("pending", "").putLong("last", System.currentTimeMillis())
                        .putInt("day", today).putInt("count", count + 1).apply()
                    onGranted()
                } else {
                    p.edit().putString("pending", "").apply()
                    Toast.makeText(activity, "لم تكتمل المشاهدة، فلا مكافأة", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /** A plain, clear question: the reward is named and the person chooses to watch or not. */
    private fun askParent(activity: Activity, offer: String, onPass: () -> Unit) {
        AlertDialog.Builder(activity)
            .setTitle("مشاهدة إعلان قصير")
            .setMessage("شاهد إعلانًا قصيرًا واحصل على:\n$offer")
            .setPositiveButton("شاهد الإعلان") { _, _ -> onPass() }
            .setNegativeButton("لا شكرًا", null)
            .show()
    }
}
