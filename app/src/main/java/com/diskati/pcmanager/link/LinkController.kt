package com.diskati.pcmanager.link

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.concurrent.Executors

/**
 * حالة الربط الحقيقي. كل عمليات الشبكة على خيط واحد، وتُحدَّث الحالة على الخيط الرئيسي.
 * لا يُعرض "متصل" إلا بعد نجاح طلب فعلي موثّق إلى الكمبيوتر.
 */
class LinkController(private val store: SecureStore) {
    var state by mutableStateOf(LinkState.Unpaired)
        private set
    var pc by mutableStateOf<PairedPc?>(null)
        private set
    var disks by mutableStateOf<List<RemoteDisk>>(emptyList())
        private set
    var lastError by mutableStateOf<String?>(null)
        private set
    var busy by mutableStateOf(false)
        private set

    val isPaired: Boolean get() = pc != null

    private var token: String? = null
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var polling = false
    private var failures = 0

    /** يقرأ الربط المحفوظ. إذا تعذّر فك التشفير يُعتبر الهاتف غير مرتبط. */
    fun load() {
        val saved = store.load() ?: return
        pc = saved.first
        token = saved.second
        state = LinkState.Connecting
    }

    fun pair(host: String, port: Int, code: String, deviceName: String, onDone: (String?) -> Unit) {
        if (busy) return
        busy = true
        io.execute {
            val result = PairingClient.pair(host, port, code, deviceName)
            var error: String? = null
            var newPc: PairedPc? = null
            var newToken: String? = null
            when (result) {
                is PairResult.Success -> {
                    if (store.save(result.pc, result.token)) {
                        newPc = result.pc
                        newToken = result.token
                    } else {
                        error = "تعذّر حفظ بيانات الربط بأمان على هذا الهاتف. احذف الهاتف من الكمبيوتر بالأمر diskati unpair وأعد المحاولة."
                    }
                }
                is PairResult.Failure -> error = result.message
            }
            main.post {
                busy = false
                if (newPc != null) {
                    pc = newPc
                    token = newToken
                    disks = emptyList()
                    lastError = null
                    failures = 0
                    state = LinkState.Connecting
                    refresh()
                }
                onDone(error)
            }
        }
    }

    /** طلب حالة وأقراص فعلي. يستدعيه المؤقت وزر إعادة المحاولة. */
    fun refresh() {
        val p = pc ?: return
        val t = token ?: return
        io.execute {
            try {
                val client = RemoteClient(p, t)
                client.status()
                val list = client.disks()
                main.post {
                    if (pc?.deviceId == p.deviceId) {
                        state = LinkState.Connected
                        disks = list
                        lastError = null
                        failures = 0
                    }
                }
            } catch (e: RemoteException) {
                main.post { if (pc?.deviceId == p.deviceId) handleFailure(e) }
            }
        }
    }

    private fun handleFailure(e: RemoteException) {
        lastError = e.message
        when (e.kind) {
            RemoteException.Kind.Unauthorized -> state = LinkState.Revoked
            RemoteException.Kind.CertChanged -> state = LinkState.CertChanged
            else -> {
                state = LinkState.Failed
                failures++
            }
        }
    }

    /** إعادة محاولة يدوية، حتى بعد إلغاء الربط أو تغيّر الشهادة. */
    fun retry() {
        if (pc == null) return
        state = LinkState.Connecting
        refresh()
    }

    /** يُبلغ الكمبيوتر ثم يحذف الربط محليًا. يحذف محليًا حتى لو تعذّر الوصول للكمبيوتر. */
    fun unpair(onDone: (Boolean) -> Unit) {
        val p = pc
        val t = token
        if (p == null || t == null) {
            forgetLocal()
            onDone(true)
            return
        }
        busy = true
        io.execute {
            val notified = try {
                RemoteClient(p, t).unpair()
                true
            } catch (e: RemoteException) {
                e.kind == RemoteException.Kind.Unauthorized // الكمبيوتر حذفنا مسبقًا
            }
            main.post {
                busy = false
                forgetLocal()
                onDone(notified)
            }
        }
    }

    /** حذف الربط من الهاتف فقط. */
    fun forgetLocal() {
        store.clear()
        pc = null
        token = null
        disks = emptyList()
        lastError = null
        failures = 0
        state = LinkState.Unpaired
    }

    private val tick = object : Runnable {
        override fun run() {
            if (!polling) return
            if (pc != null && state != LinkState.Revoked && state != LinkState.CertChanged) refresh()
            val delay = if (failures == 0) 10_000L else minOf(30_000L, 5_000L * (failures + 1))
            main.postDelayed(this, delay)
        }
    }

    fun startPolling() {
        if (polling) return
        polling = true
        main.post(tick)
    }

    fun stopPolling() {
        polling = false
        main.removeCallbacks(tick)
    }
}
