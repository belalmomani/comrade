package com.diskati.pcmanager.link

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** تشفير AES-256-GCM بمفتاح غير قابل للتصدير داخل Android Keystore. */
private object KeystoreCipher {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val ALIAS = "diskati_token_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_LENGTH = 12

    private fun key(): SecretKey? {
        return try {
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE)
            ks.load(null)
            val existing = ks.getKey(ALIAS, null) as? SecretKey
            if (existing != null) return existing
            val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            gen.init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            gen.generateKey()
        } catch (e: Exception) {
            null
        }
    }

    fun encrypt(plain: ByteArray): String? {
        return try {
            val k = key() ?: return null
            val c = Cipher.getInstance(TRANSFORMATION)
            c.init(Cipher.ENCRYPT_MODE, k)
            Base64.encodeToString(c.iv + c.doFinal(plain), Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        }
    }

    fun decrypt(stored: String): ByteArray? {
        return try {
            val k = key() ?: return null
            val raw = Base64.decode(stored, Base64.NO_WRAP)
            if (raw.size <= IV_LENGTH) return null
            val c = Cipher.getInstance(TRANSFORMATION)
            c.init(Cipher.DECRYPT_MODE, k, GCMParameterSpec(128, raw.copyOfRange(0, IV_LENGTH)))
            c.doFinal(raw.copyOfRange(IV_LENGTH, raw.size))
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * يحفظ بيانات الكمبيوتر المرتبط. رمز الجهاز لا يُكتب أبدًا دون تشفير؛ إذا تعذّر التشفير
 * (Keystore غير متاح) يفشل الحفظ بدل الرجوع إلى نص صريح.
 */
class SecureStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("diskati_link", Context.MODE_PRIVATE)

    fun save(pc: PairedPc, token: String): Boolean {
        val enc = KeystoreCipher.encrypt(token.toByteArray(Charsets.UTF_8)) ?: return false
        return prefs.edit()
            .putString("host", pc.host)
            .putInt("port", pc.port)
            .putString("fp", pc.fingerprint)
            .putString("deviceId", pc.deviceId)
            .putString("computerName", pc.computerName)
            .putString("token", enc)
            .commit()
    }

    fun load(): Pair<PairedPc, String>? {
        val host = prefs.getString("host", null) ?: return null
        val fp = prefs.getString("fp", null) ?: return null
        val id = prefs.getString("deviceId", null) ?: return null
        val enc = prefs.getString("token", null) ?: return null
        val token = KeystoreCipher.decrypt(enc)?.toString(Charsets.UTF_8) ?: return null
        val pc = PairedPc(host, prefs.getInt("port", 47831), fp, id, prefs.getString("computerName", host) ?: host)
        return pc to token
    }

    fun clear() {
        prefs.edit().clear().commit()
    }
}
