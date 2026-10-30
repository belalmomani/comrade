package com.diskati.pcmanager.link

import java.io.InputStream
import java.net.URL
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext

class HttpResult(val code: Int, val body: String)

private const val MAX_RESPONSE = 64 * 1024

object SecureHttp {
    /**
     * طلب HTTPS واحد. [bodyProvider] يُستدعى بعد اكتمال مصافحة TLS، فيستطيع المنادي قراءة
     * بصمة الشهادة قبل بناء محتوى الطلب (ضروري للاقتران).
     */
    fun request(
        host: String,
        port: Int,
        path: String,
        method: String,
        ctx: SSLContext,
        headers: Map<String, String> = emptyMap(),
        bodyProvider: (() -> String)? = null,
    ): HttpResult {
        val hostPart = if (host.contains(':')) "[$host]" else host
        val conn = URL("https://$hostPart:$port$path").openConnection() as HttpsURLConnection
        try {
            conn.sslSocketFactory = ctx.socketFactory
            // الهوية تُتحقق بالبصمة لا باسم المضيف (شهادة ذاتية على عنوان IP).
            conn.hostnameVerifier = HostnameVerifier { name, _ -> name == host }
            conn.connectTimeout = 5000
            conn.readTimeout = 10000
            conn.useCaches = false
            conn.instanceFollowRedirects = false
            conn.requestMethod = method
            for ((k, v) in headers) conn.setRequestProperty(k, v)
            if (bodyProvider != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
            }
            conn.connect()
            if (bodyProvider != null) {
                val bytes = bodyProvider().toByteArray(Charsets.UTF_8)
                conn.outputStream.use { it.write(bytes) }
            }
            val code = conn.responseCode
            val stream: InputStream? = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.use { readLimited(it) } ?: ""
            return HttpResult(code, text)
        } finally {
            conn.disconnect()
        }
    }

    private fun readLimited(input: InputStream): String {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(4096)
        while (out.size() < MAX_RESPONSE) {
            val n = input.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
        }
        return out.toString("UTF-8")
    }
}
