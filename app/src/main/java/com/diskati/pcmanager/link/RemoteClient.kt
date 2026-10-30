package com.diskati.pcmanager.link

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import javax.net.ssl.SSLException

class RemoteException(val kind: Kind, message: String) : Exception(message) {
    enum class Kind { Unreachable, Unauthorized, CertChanged, Server }
}

/** عميل الأوامر المحددة مسبقًا فقط: status وdisks وunpair. الاتصال يقبل شهادة واحدة (المثبّتة). */
class RemoteClient(private val pc: PairedPc, private val token: String) {
    private val ctx = sslContextFor(PinnedTrustManager(pc.fingerprint))

    private fun authHeaders() = mapOf(
        "X-Diskati-Device" to pc.deviceId,
        "Authorization" to "Bearer $token",
    )

    private fun call(method: String, path: String, withBody: Boolean = false): String {
        val res = try {
            SecureHttp.request(
                host = pc.host,
                port = pc.port,
                path = path,
                method = method,
                ctx = ctx,
                headers = authHeaders(),
                bodyProvider = if (withBody) ({ "" }) else null,
            )
        } catch (e: SSLException) {
            val mismatch = generateSequence<Throwable>(e) { it.cause }
                .any { it.message?.contains("fingerprint mismatch") == true }
            if (mismatch) throw RemoteException(RemoteException.Kind.CertChanged, "تغيّرت هوية الكمبيوتر")
            throw RemoteException(RemoteException.Kind.Unreachable, "تعذّر إنشاء اتصال آمن")
        } catch (e: IOException) {
            throw RemoteException(RemoteException.Kind.Unreachable, "تعذّر الوصول إلى الكمبيوتر")
        }
        return when (res.code) {
            200 -> res.body
            401 -> throw RemoteException(RemoteException.Kind.Unauthorized, "ألغى الكمبيوتر ربط هذا الهاتف")
            429 -> throw RemoteException(RemoteException.Kind.Server, "محاولات كثيرة، أعد المحاولة بعد قليل")
            else -> throw RemoteException(RemoteException.Kind.Server, "خطأ من الكمبيوتر (${res.code})")
        }
    }

    fun status(): RemoteStatus = parse {
        val j = JSONObject(call("GET", "/v1/status"))
        RemoteStatus(j.optString("computerName", pc.computerName), j.optString("version", ""))
    }

    fun disks(): List<RemoteDisk> = parse {
        val arr = JSONArray(call("GET", "/v1/disks"))
        (0 until arr.length()).map { i ->
            val d = arr.getJSONObject(i)
            RemoteDisk(
                letter = d.getString("letter"),
                totalBytes = d.optLong("totalBytes", 0),
                freeBytes = d.optLong("freeBytes", 0),
                usedBytes = d.optLong("usedBytes", 0),
                available = d.optBoolean("available", false),
                error = if (d.has("error")) d.optString("error") else null,
            )
        }
    }

    fun unpair() {
        call("POST", "/v1/unpair", withBody = true)
    }

    private fun <T> parse(block: () -> T): T = try {
        block()
    } catch (e: JSONException) {
        throw RemoteException(RemoteException.Kind.Server, "رد غير متوقع من الكمبيوتر")
    }
}
