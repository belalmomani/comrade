package com.diskati.pcmanager.link

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

const val CODE_LENGTH = 12
private const val CODE_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
private const val PROOF_PREFIX = "diskati-pair-v1"
private const val HEX = "0123456789abcdef"

fun ByteArray.toHex(): String {
    val sb = StringBuilder(size * 2)
    for (b in this) {
        sb.append(HEX[(b.toInt() shr 4) and 0xF])
        sb.append(HEX[b.toInt() and 0xF])
    }
    return sb.toString()
}

fun sha256Hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

/** يحوّل الرمز المكتوب (مع شرطات أو مسافات أو أحرف صغيرة) إلى الشكل القياسي. */
fun normalizeCode(raw: String): String = raw.uppercase().filter { it != '-' && it != ' ' }

fun isValidCode(code: String): Boolean = code.length == CODE_LENGTH && code.all { it in CODE_ALPHABET }

/**
 * إثبات معرفة الرمز: HMAC-SHA256(code, "diskati-pair-v1|role|fingerprint|nonce").
 * الرمز نفسه لا يُرسل أبدًا. يجب أن يطابق pairing.Proof في برنامج Windows حرفيًا.
 */
fun pairingProof(code: String, role: String, fingerprint: String, nonce: String): String {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(code.toByteArray(Charsets.UTF_8), "HmacSHA256"))
    val msg = PROOF_PREFIX + "|" + role + "|" + fingerprint + "|" + nonce
    return mac.doFinal(msg.toByteArray(Charsets.UTF_8)).toHex()
}

fun constantTimeEquals(a: String, b: String): Boolean =
    MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))

fun randomNonce(): String {
    val b = ByteArray(16)
    SecureRandom().nextBytes(b)
    return Base64.encodeToString(b, Base64.NO_WRAP)
}

/** أحرف اسم المضيف المسموحة فقط، لمنع حقن أجزاء في عنوان الاتصال. */
fun isValidHost(host: String): Boolean =
    host.isNotEmpty() && host.length <= 253 && host.all { it.isLetterOrDigit() || it == '.' || it == '-' }

/** بصمة للعرض: أول 32 خانة مجمّعة. */
fun formatFingerprint(fp: String): String = fp.take(32).chunked(4).joinToString(" ") + " …"
