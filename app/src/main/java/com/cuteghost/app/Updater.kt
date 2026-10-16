package com.cuteghost.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * App updates through GitHub Releases (published by the build workflow).
 *
 * Android only installs an update over the current app when the package name matches, the version
 * code is higher and the APK is signed with the SAME certificate. The installation itself always
 * needs the user's confirmation: a normal app cannot install silently.
 */
object Updater {

    const val EXTRA_SHOW_UPDATE = "show_update"
    private const val CHANNEL_ID = "ghost_updates"
    private const val NOTIFICATION_ID = 2
    private const val PREFS = "ghost_prefs"
    private const val KEY_NOTIFIED = "update_notified_code"

    class Release(
        val versionCode: Int,
        val versionName: String,
        val apkUrl: String,
        val notes: String
    )

    // ------------------------------------------------------------------
    // Version info
    // ------------------------------------------------------------------

    @Suppress("DEPRECATION")
    fun currentVersionCode(context: Context): Long {
        return try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        } catch (e: PackageManager.NameNotFoundException) {
            0L
        }
    }

    fun isNewer(context: Context, release: Release): Boolean =
        release.versionCode.toLong() > currentVersionCode(context)

    /** Short fingerprint of the certificate this installed app is signed with (to compare builds). */
    @Suppress("DEPRECATION")
    fun signatureShort(context: Context): String? {
        return try {
            val flags = if (Build.VERSION.SDK_INT >= 28) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                PackageManager.GET_SIGNATURES
            }
            val info = context.packageManager.getPackageInfo(context.packageName, flags)
            signerDigests(info)?.firstOrNull()?.take(8)?.uppercase()
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    /** Runs the check on a background thread; [callback] is called on the main thread. */
    fun checkInBackground(callback: (Release?) -> Unit) {
        Thread {
            val release = fetchLatest()
            Handler(Looper.getMainLooper()).post { callback(release) }
        }.start()
    }

    /** Latest GitHub release (the repository must be public), or null when unavailable. */
    fun fetchLatest(): Release? {
        val repo = BuildConfig.UPDATE_REPO
        if (repo.isBlank()) return null
        var conn: HttpURLConnection? = null
        return try {
            val c = URL("https://api.github.com/repos/$repo/releases/latest").openConnection() as HttpURLConnection
            conn = c
            c.connectTimeout = 15000
            c.readTimeout = 20000
            c.setRequestProperty("Accept", "application/vnd.github+json")
            c.setRequestProperty("User-Agent", "CuteGhost-Updater")
            if (c.responseCode != 200) {
                null
            } else {
                val body = c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                parseRelease(JSONObject(body))
            }
        } catch (e: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun parseRelease(json: JSONObject): Release? {
        val tag = json.optString("tag_name")
        val code = Regex("(\\d+)$").find(tag)?.value?.toIntOrNull() ?: return null
        val assets = json.optJSONArray("assets") ?: return null
        var apk: String? = null
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            if (asset.optString("name").endsWith(".apk")) {
                apk = asset.optString("browser_download_url")
                break
            }
        }
        val url = apk ?: return null
        if (url.isEmpty()) return null
        val last = json.optString("name").trim().split(" ").lastOrNull { it.isNotEmpty() }
        val versionName = if (last != null && last.firstOrNull()?.isDigit() == true) last else tag
        return Release(code, versionName, url, json.optString("body"))
    }

    // ------------------------------------------------------------------
    // Download and verification
    // ------------------------------------------------------------------

    /** Blocking download to the app cache. [onProgress] gets 0..100, or -1 when the size is unknown. */
    fun download(context: Context, release: Release, onProgress: (Int) -> Unit): File? {
        val dir = File(context.cacheDir, "updates")
        dir.mkdirs()
        val file = File(dir, "update.apk")
        if (file.exists()) file.delete()
        var conn: HttpURLConnection? = null
        return try {
            val c = URL(release.apkUrl).openConnection() as HttpURLConnection
            conn = c
            c.connectTimeout = 15000
            c.readTimeout = 30000
            c.setRequestProperty("User-Agent", "CuteGhost-Updater")
            if (c.responseCode != 200) {
                null
            } else {
                val total = c.contentLengthLong
                var done = 0L
                var last = -2
                c.inputStream.use { input ->
                    file.outputStream().use { out ->
                        val buffer = ByteArray(32 * 1024)
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            out.write(buffer, 0, n)
                            done += n
                            val pct = if (total > 0) (done * 100 / total).toInt() else -1
                            if (pct != last) {
                                last = pct
                                onProgress(pct)
                            }
                        }
                    }
                }
                if (file.length() > 0) file else null
            }
        } catch (e: IOException) {
            file.delete()
            null
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * Checks the downloaded APK before handing it to Android: same package, newer version and the
     * same signing certificate as the installed app. Returns an error string resource, or null.
     */
    @Suppress("DEPRECATION")
    fun verify(context: Context, file: File): Int? {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        val archive = pm.getPackageArchiveInfo(file.absolutePath, flags) ?: return R.string.update_bad_file
        if (archive.packageName != context.packageName) return R.string.update_bad_file

        val newCode = if (Build.VERSION.SDK_INT >= 28) archive.longVersionCode else archive.versionCode.toLong()
        if (newCode <= currentVersionCode(context)) return R.string.update_not_newer

        val installed: PackageInfo? = try {
            pm.getPackageInfo(context.packageName, flags)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
        val oldSigners = if (installed != null) signerDigests(installed) else null
        val newSigners = signerDigests(archive)
        // Only block when both certificates could be read and they differ
        if (oldSigners != null && newSigners != null && oldSigners != newSigners) {
            return R.string.update_signature_mismatch
        }
        return null
    }

    @Suppress("DEPRECATION")
    private fun signerDigests(info: PackageInfo): Set<String>? {
        val signatures: Array<Signature>? = if (Build.VERSION.SDK_INT >= 28) {
            info.signingInfo?.apkContentsSigners
        } else {
            info.signatures
        }
        if (signatures == null || signatures.isEmpty()) return null
        val digest = MessageDigest.getInstance("SHA-256")
        return signatures.map { s ->
            digest.reset()
            digest.digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
        }.toSet()
    }

    // ------------------------------------------------------------------
    // Installation (the system always asks the user to confirm)
    // ------------------------------------------------------------------

    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()

    fun openInstallSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:" + context.packageName)
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /** Streams the APK into a PackageInstaller session. The result arrives in [InstallResultReceiver]. */
    fun install(context: Context, file: File): Boolean {
        return try {
            val installer = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            params.setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= 31) {
                // Honoured by the system only in limited cases; otherwise the user is asked as usual
                params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
            val sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                file.inputStream().use { input ->
                    session.openWrite("update.apk", 0, file.length()).use { out ->
                        input.copyTo(out)
                        session.fsync(out)
                    }
                }
                val intent = Intent(context, InstallResultReceiver::class.java)
                    .setAction(InstallResultReceiver.ACTION)
                var piFlags = PendingIntent.FLAG_UPDATE_CURRENT
                if (Build.VERSION.SDK_INT >= 31) piFlags = piFlags or PendingIntent.FLAG_MUTABLE
                val pending = PendingIntent.getBroadcast(context, sessionId, intent, piFlags)
                session.commit(pending.intentSender)
            }
            true
        } catch (e: IOException) {
            false
        } catch (e: SecurityException) {
            false
        } catch (e: IllegalStateException) {
            false
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    // ------------------------------------------------------------------
    // Notification
    // ------------------------------------------------------------------

    /** Posts a clear "update available" notification, once per version. */
    @Suppress("DEPRECATION")
    fun notifyUpdate(context: Context, release: Release) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getInt(KEY_NOTIFIED, 0) == release.versionCode) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.notif_channel_updates),
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }
        val open = PendingIntent.getActivity(
            context, 1,
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_SHOW_UPDATE, true)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_ID)
        } else {
            Notification.Builder(context)
        }
        val notification = builder
            .setSmallIcon(R.drawable.ic_stat_ghost)
            .setContentTitle(context.getString(R.string.update_notif_title))
            .setContentText(context.getString(R.string.update_notif_text, release.versionName))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
        prefs.edit().putInt(KEY_NOTIFIED, release.versionCode).apply()
    }
}
