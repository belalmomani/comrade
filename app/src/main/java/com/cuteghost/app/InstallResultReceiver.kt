package com.cuteghost.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.widget.Toast

/** Receives the result of the update installation session. */
class InstallResultReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION = "com.companion.theghost.INSTALL_RESULT"
    }

    @Suppress("DEPRECATION")
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // Android asks the user to confirm the update
                val confirm: Intent? = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try {
                        context.startActivity(confirm)
                    } catch (e: RuntimeException) {
                        toast(context, R.string.update_install_failed)
                    }
                }
            }
            PackageInstaller.STATUS_SUCCESS -> Unit // the app is replaced by the new version
            PackageInstaller.STATUS_FAILURE_ABORTED -> toast(context, R.string.update_install_cancelled)
            PackageInstaller.STATUS_FAILURE_CONFLICT,
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> toast(context, R.string.update_install_conflict)
            else -> toast(context, R.string.update_install_failed)
        }
    }

    private fun toast(context: Context, resId: Int) {
        Toast.makeText(context, resId, Toast.LENGTH_LONG).show()
    }
}
