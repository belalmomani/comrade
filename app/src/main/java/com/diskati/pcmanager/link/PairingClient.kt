package com.diskati.pcmanager.link

import org.json.JSONObject
import java.io.IOException
import javax.net.ssl.SSLException

sealed class PairResult {
    data class Success(val pc: PairedPc, val token: String) : PairResult()
    data class Failure(val message: String) : PairResult()
}

object PairingClient {
    /**
     * 1) مصافحة TLS وتسجيل بصمة الشهادة، 2) إرسال HMAC مرتبط بالبصمة والرمز، 3) التحقق من إثبات
     * الكمبيوتر المقابل قبل حفظ أي شيء. لا يُرسل الرمز نفسه أبدًا.
     */
    fun pair(host: String, port: Int, rawCode: String, deviceName: String): PairResult {
        val code = normalizeCode(rawCode)
        if (!isValidCode(code)) return PairResult.Failure("الرمز يجب أن يتكون من $CODE_LENGTH خانة (أرقام من 2 إلى 9 وحروف لاتينية).")
        if (!isValidHost(host)) return PairResult.Failure("العنوان غير صالح.")
        if (port !in 1..65535) return PairResult.Failure("المنفذ غير صالح.")

        val tm = CapturingTrustManager()
        val nonce = randomNonce()
        return try {
            val res = SecureHttp.request(
                host = host,
                port = port,
                path = "/v1/pair",
                method = "POST",
                ctx = sslContextFor(tm),
                bodyProvider = {
                    val fp = tm.fingerprint ?: throw IOException("no certificate")
                    JSONObject()
                        .put("deviceName", deviceName)
                        .put("nonce", nonce)
                        .put("proof", pairingProof(code, "client", fp, nonce))
                        .toString()
                },
            )
            val fp = tm.fingerprint ?: return PairResult.Failure("تعذّر قراءة شهادة الكمبيوتر.")
            when (res.code) {
                200 -> parseSuccess(res.body, host, port, code, fp, nonce)
                403 -> PairResult.Failure("الرمز غير صحيح أو منتهي أو استُخدم من قبل. أنشئ رمزًا جديدًا بالأمر diskati pair.")
                429 -> PairResult.Failure("محاولات كثيرة. انتظر دقيقة ثم أعد المحاولة.")
                else -> PairResult.Failure("رفض الكمبيوتر الطلب (${res.code}).")
            }
        } catch (e: SSLException) {
            PairResult.Failure("تعذّر إنشاء اتصال آمن مع الكمبيوتر. تأكد أن العنوان والمنفذ يخصان برنامج ديسكاتي.")
        } catch (e: IOException) {
            PairResult.Failure("تعذّر الوصول إلى الكمبيوتر. تأكد أن الهاتف والكمبيوتر على الشبكة نفسها وأن جدار الحماية يسمح بالاتصال.")
        }
    }

    private fun parseSuccess(body: String, host: String, port: Int, code: String, fp: String, nonce: String): PairResult {
        return try {
            val j = JSONObject(body)
            val serverProof = j.getString("serverProof")
            if (!constantTimeEquals(serverProof, pairingProof(code, "server", fp, nonce))) {
                return PairResult.Failure("فشل التحقق من هوية الكمبيوتر. لم يُحفظ شيء. قد يكون هناك جهاز وسيط على الشبكة.")
            }
            val token = j.getString("token")
            val id = j.getString("deviceId")
            if (token.length != 64 || id.isEmpty()) return PairResult.Failure("رد غير متوقع من الكمبيوتر.")
            PairResult.Success(PairedPc(host, port, fp, id, j.optString("computerName", host)), token)
        } catch (e: org.json.JSONException) {
            PairResult.Failure("رد غير متوقع من الكمبيوتر.")
        }
    }
}
