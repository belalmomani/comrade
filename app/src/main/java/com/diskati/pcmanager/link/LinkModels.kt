package com.diskati.pcmanager.link

/** الكمبيوتر المرتبط. الرمز السري لا يُحفظ هنا بل في SecureStore مشفّرًا. */
data class PairedPc(
    val host: String,
    val port: Int,
    /** بصمة SHA-256 لشهادة الكمبيوتر (hex صغير). أي شهادة بغيرها تُرفض. */
    val fingerprint: String,
    val deviceId: String,
    val computerName: String,
)

data class RemoteDisk(
    val letter: String,
    val totalBytes: Long,
    val freeBytes: Long,
    val usedBytes: Long,
    val available: Boolean,
    val error: String?,
)

data class RemoteStatus(val computerName: String, val version: String)

enum class LinkState { Unpaired, Connecting, Connected, Failed, CertChanged, Revoked }
