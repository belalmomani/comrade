package com.cuteghost.app

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.WindowInsets
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher

/**
 * Helpers for apps that target Android 15/16 (API 35/36):
 *  - windows are always drawn edge to edge, so each screen keeps its content clear of the system bars;
 *  - the system back gesture no longer calls onBackPressed(), so a back callback is registered instead.
 */
object Edge {

    /** Pads the activity's content so nothing is hidden under the status bar, navigation bar, cutout or keyboard. */
    fun apply(activity: Activity) {
        val content = activity.findViewById<View>(android.R.id.content) ?: return
        content.setOnApplyWindowInsetsListener { v, insets ->
            val l: Int
            val t: Int
            val r: Int
            val b: Int
            if (Build.VERSION.SDK_INT >= 30) {
                val i = insets.getInsets(
                    WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout() or WindowInsets.Type.ime()
                )
                l = i.left; t = i.top; r = i.right; b = i.bottom
            } else {
                @Suppress("DEPRECATION")
                run {
                    l = insets.systemWindowInsetLeft
                    t = insets.systemWindowInsetTop
                    r = insets.systemWindowInsetRight
                    b = insets.systemWindowInsetBottom
                }
            }
            v.setPadding(l, t, r, b)
            if (Build.VERSION.SDK_INT >= 30) {
                WindowInsets.CONSUMED
            } else {
                @Suppress("DEPRECATION")
                insets.consumeSystemWindowInsets()
            }
        }
        content.requestApplyInsets()
    }

    /** Runs [handler] when the user goes back (gesture, button or predictive back) on Android 13 and newer. */
    fun onBack(activity: Activity, handler: () -> Unit) {
        if (Build.VERSION.SDK_INT >= 33) {
            activity.onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                OnBackInvokedCallback { handler() }
            )
        }
    }
}
