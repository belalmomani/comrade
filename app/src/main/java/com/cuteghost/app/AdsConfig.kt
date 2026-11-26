package com.cuteghost.app

/**
 * AdMob ids. They come from the build (see app/build.gradle.kts): release builds use the real ids,
 * debug builds use Google's official TEST ids, which only ever show test ads.
 */
object AdsConfig {
    val APP_ID: String = BuildConfig.ADMOB_APP_ID
    val REWARDED_UNIT_ID: String = BuildConfig.ADMOB_REWARDED_ID
    val BANNER_UNIT_ID: String = BuildConfig.ADMOB_BANNER_ID
}
