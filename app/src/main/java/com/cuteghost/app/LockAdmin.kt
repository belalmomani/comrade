package com.cuteghost.app

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/** Only used to turn the screen off and show the phone's own lock screen. It gives no other power. */
class LockAdminReceiver : DeviceAdminReceiver()

object LockAdmin {

    private fun component(context: Context) = ComponentName(context, LockAdminReceiver::class.java)

    private fun manager(context: Context) =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    fun isActive(context: Context): Boolean =
        try {
            manager(context).isAdminActive(component(context))
        } catch (e: RuntimeException) {
            false
        }

    /** Turns the screen off and shows the phone's lock screen (its PIN, pattern or password if there is one). */
    fun lockNow(context: Context) {
        if (!isActive(context)) return
        try {
            manager(context).lockNow()
        } catch (e: RuntimeException) {
            // not allowed right now: the sleep screen still covers the phone
        }
    }

    fun deactivate(context: Context) {
        try {
            if (isActive(context)) manager(context).removeActiveAdmin(component(context))
        } catch (e: RuntimeException) {
            // nothing to do
        }
    }

    fun activateIntent(context: Context): Intent =
        Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component(context))
            .putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "يُستخدم فقط لإطفاء الشاشة وإظهار قفل هاتفكم عند بدء وقت الراحة. لا يقرأ شيئًا ولا يغيّر كلمة المرور."
            )
}
