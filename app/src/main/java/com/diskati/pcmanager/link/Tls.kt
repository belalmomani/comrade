package com.diskati.pcmanager.link

import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/**
 * يُستخدم في طلب الاقتران فقط. لا يثق بالشهادة، بل يسجّل بصمتها ليُربط بها إثبات HMAC.
 * إذا كان بين الهاتف والكمبيوتر وسيط، فبصمته تختلف عن بصمة الكمبيوتر الحقيقية فيفشل الإثبات.
 * (مراجعة مخطط لها: استبدال هذا بمسح QR يحمل البصمة قبل الاتصال، قبل النشر على Google Play.)
 */
class CapturingTrustManager : X509TrustManager {
    @Volatile
    var fingerprint: String? = null

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        throw CertificateException("client certificates are not used")
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        if (chain.isNullOrEmpty()) throw CertificateException("empty certificate chain")
        fingerprint = sha256Hex(chain[0].encoded)
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}

/** بعد الاقتران: يقبل شهادة واحدة فقط هي المطابقة للبصمة المحفوظة. أي اختلاف يقطع الاتصال. */
class PinnedTrustManager(private val expectedFingerprint: String) : X509TrustManager {
    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        throw CertificateException("client certificates are not used")
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        if (chain.isNullOrEmpty()) throw CertificateException("empty certificate chain")
        val actual = sha256Hex(chain[0].encoded)
        if (!constantTimeEquals(actual, expectedFingerprint)) {
            throw CertificateException("fingerprint mismatch")
        }
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}

fun sslContextFor(tm: X509TrustManager): SSLContext {
    val ctx = SSLContext.getInstance("TLS")
    ctx.init(null, arrayOf(tm), SecureRandom())
    return ctx
}
