package com.diskati.pcmanager.ui

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.diskati.pcmanager.data.DemoData
import com.diskati.pcmanager.data.formatGb
import com.diskati.pcmanager.link.LinkController
import com.diskati.pcmanager.link.LinkState
import com.diskati.pcmanager.link.formatFingerprint
import com.diskati.pcmanager.link.isValidCode
import com.diskati.pcmanager.link.isValidHost
import com.diskati.pcmanager.link.normalizeCode

private const val DEFAULT_PORT = "47831"

@Composable
fun PairingScreen(link: LinkController, nav: Nav) {
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf(DEFAULT_PORT) }
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val portNum = port.toIntOrNull()
    val canPair = !link.busy &&
        isValidHost(host.trim()) &&
        portNum != null && portNum in 1..65535 &&
        isValidCode(normalizeCode(code))
    val ltr = TextStyle(textDirection = TextDirection.Ltr)

    ScreenScaffold(title = "إعداد وربط الكمبيوتر", onBack = { nav.back() }) { padding ->
        ScrollBody(padding) {
            if (link.isPaired) {
                SectionCard {
                    Text(
                        "هذا الهاتف مرتبط حاليًا بـ ${link.pc?.computerName}. الاقتران الجديد يستبدله. يُفضّل إلغاء الربط الحالي أولًا من إعدادات الاتصال حتى لا يبقى الهاتف مسجّلًا على الكمبيوتر القديم.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            SectionCard {
                Text("خطوات الربط", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.size(8.dp))
                StepRow(1, "نزّل برنامج ديسكاتي المرافق (diskati.exe) وضعه في مجلد ثابت على كمبيوتر Windows.")
                StepRow(2, "افتح PowerShell في ذلك المجلد وشغّل الأمر التالي. سيعرض رمز اقتران وعنوان الكمبيوتر:")
                LtrText(".\\diskati.exe pair", style = MaterialTheme.typography.bodyMedium)
                StepRow(3, "أدخل العنوان والمنفذ والرمز هنا خلال 5 دقائق. الرمز يُستخدم مرة واحدة فقط.")
            }
            SectionCard {
                OutlinedTextField(
                    value = host,
                    onValueChange = { v -> host = v.filter { it.isLetterOrDigit() || it == '.' || it == '-' }.take(253) },
                    label = { Text("عنوان الكمبيوتر (مثل 192.168.1.5)") },
                    singleLine = true,
                    textStyle = ltr,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.size(8.dp))
                OutlinedTextField(
                    value = port,
                    onValueChange = { v -> port = v.filter { it.isDigit() }.take(5) },
                    label = { Text("المنفذ") },
                    singleLine = true,
                    textStyle = ltr,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.size(8.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { v ->
                        code = v.filter { it.isLetterOrDigit() || it == '-' || it == ' ' }.take(14).uppercase()
                    },
                    label = { Text("رمز الاقتران (12 خانة)") },
                    singleLine = true,
                    textStyle = ltr,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        keyboardType = KeyboardType.Ascii,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.size(12.dp))
                Button(
                    onClick = {
                        error = null
                        link.pair(host.trim(), portNum ?: 0, code, Build.MODEL ?: "Android") { err ->
                            if (err == null) nav.toHome() else error = err
                        }
                    },
                    enabled = canPair,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                ) {
                    if (link.busy) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("جارٍ التحقق…")
                    } else {
                        Text("ربط الكمبيوتر")
                    }
                }
            }
            error?.let {
                StateCard(
                    icon = Icons.Default.LinkOff,
                    title = "لم يتم الربط",
                    message = it,
                    isError = true,
                )
            }
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "الاتصال مشفّر ومحلي بين الهاتف والكمبيوتر فقط. لا يُرسل الرمز نفسه عبر الشبكة، بل إثبات مشتق منه، ويتحقق الطرفان من هوية بعضهما قبل الحفظ.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun StepRow(n: Int, text: String) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text("$n.", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun DisksScreen(demo: DemoState, link: LinkController, nav: Nav) {
    ScreenScaffold(title = "الأقراص والمجلدات", onBack = { nav.back() }) { padding ->
        ScrollBody(padding) {
            if (link.isPaired) {
                if (link.state == LinkState.Connected) {
                    link.disks.forEach { RealDiskCard(it) }
                    if (link.disks.isEmpty()) {
                        StateCard(
                            icon = Icons.Default.Folder,
                            title = "لا توجد أقراص ثابتة",
                            message = "لم يُبلّغ الكمبيوتر عن أي قرص ثابت.",
                        )
                    }
                    Text(
                        "استعراض المجلدات والملفات المسموح بها يُنفذ مع محرك الفحص في المرحلة 5.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LinkProblemCard(link, nav)
                }
                return@ScrollBody
            }
            DemoBanner()
            val disk = DemoData.disks.first { it.letter == demo.selectedDisk }
            if (demo.connection != ConnectionState.Connected) {
                StateCard(
                    icon = Icons.Default.LinkOff,
                    title = "الكمبيوتر غير متصل",
                    message = "اربط الكمبيوتر لعرض الأقراص والمجلدات المسموح بها.",
                    actionLabel = "ربط الكمبيوتر",
                    onAction = { nav.go(Screen.Pairing) },
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DemoData.disks.forEach { d ->
                        FilterChip(
                            selected = d.letter == demo.selectedDisk,
                            onClick = { demo.selectedDisk = d.letter },
                            label = { Text(d.letter) },
                        )
                    }
                }
                SectionCard {
                    InfoRow("السعة الإجمالية", formatGb(disk.totalGb))
                    InfoRow("المستخدمة", formatGb(disk.usedGb))
                    InfoRow("المتاحة", formatGb(disk.freeGb))
                    Spacer(Modifier.size(8.dp))
                    StorageBar(
                        segments = disk.categories.map { BarSegment(it.color, it.gb) } +
                            BarSegment(MaterialTheme.colorScheme.surfaceVariant, disk.freeGb),
                    )
                }
                SectionTitle("المجلدات المسموح بها (عينة)")
                SectionCard {
                    disk.folders.forEachIndexed { i, (name, gb) ->
                        if (i > 0) HorizontalDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Text(name, modifier = Modifier.weight(1f))
                            Text(formatGb(gb), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Text(
                    "ملفات النظام المحمية لا تظهر ولا تُحذف.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun ConnectionSettingsScreen(demo: DemoState, link: LinkController, nav: Nav) {
    var confirm by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    val pc = link.pc

    ScreenScaffold(title = "إعدادات الاتصال", onBack = { nav.back() }) { padding ->
        ScrollBody(padding) {
            notice?.let {
                SectionCard { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
            if (pc == null) {
                DemoBanner("لا يوجد ربط حقيقي. القيم أدناه للمعاينة فقط.")
                val statusText = when (demo.connection) {
                    ConnectionState.Connected -> "متصل"
                    ConnectionState.Disconnected -> "غير متصل"
                    ConnectionState.Connecting -> "جارٍ الاتصال"
                    ConnectionState.Failed -> "فشل الاتصال"
                }
                SectionCard {
                    InfoRow("اسم الكمبيوتر", DemoData.COMPUTER_NAME)
                    InfoRow("الحالة", statusText)
                }
                Button(
                    onClick = { nav.go(Screen.Pairing) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                ) { Text("إعداد ربط جديد") }
            } else {
                val statusText = when (link.state) {
                    LinkState.Connected -> "متصل"
                    LinkState.Connecting -> "جارٍ الاتصال"
                    LinkState.Failed -> "تعذّر الوصول"
                    LinkState.Revoked -> "أُلغي الربط من الكمبيوتر"
                    LinkState.CertChanged -> "تغيّرت هوية الكمبيوتر"
                    LinkState.Unpaired -> "غير مرتبط"
                }
                SectionCard {
                    InfoRow("اسم الكمبيوتر", pc.computerName)
                    InfoRow("الحالة", statusText)
                    InfoRow("نوع الاتصال", "الشبكة المحلية، مشفّر (TLS)")
                    InfoRow("معرّف هذا الهاتف", pc.deviceId)
                    Text("العنوان", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LtrText("${pc.host}:${pc.port}", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.size(8.dp))
                    Text(
                        "بصمة شهادة الكمبيوتر (SHA-256)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LtrText(formatFingerprint(pc.fingerprint))
                }
                if (link.state != LinkState.Connected) {
                    LinkProblemCard(link, nav)
                }
                SectionCard {
                    OutlinedButton(
                        onClick = { link.retry() },
                        enabled = !link.busy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                    ) { Text("إعادة المحاولة الآن") }
                    Spacer(Modifier.size(8.dp))
                    Button(
                        onClick = { confirm = true },
                        enabled = !link.busy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                    ) { Text("إلغاء الربط") }
                }
            }
            Text(
                "يمكن إلغاء الربط من الهاتف ومن الكمبيوتر (diskati unpair). الأجهزة غير الموثقة لا تستطيع إرسال أي أمر.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("إلغاء الربط؟") },
            text = { Text("سيُحذف الربط من الهاتف ويُبلَّغ الكمبيوتر بذلك. تحتاج إلى رمز اقتران جديد لربطه مرة أخرى.") },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    link.unpair { notified ->
                        notice = if (notified) {
                            "أُلغي الربط."
                        } else {
                            "حُذف الربط من الهاتف، لكن تعذّر إبلاغ الكمبيوتر. احذف الهاتف منه بالأمر diskati unpair."
                        }
                    }
                }) { Text("إلغاء الربط") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("تراجع") } },
        )
    }
}
