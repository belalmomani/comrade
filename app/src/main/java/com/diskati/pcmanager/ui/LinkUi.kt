package com.diskati.pcmanager.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.diskati.pcmanager.data.formatGb
import com.diskati.pcmanager.link.LinkController
import com.diskati.pcmanager.link.LinkState
import com.diskati.pcmanager.link.RemoteDisk

/** حالة الاتصال المعروضة: الحقيقية إذا كان الهاتف مرتبطًا، وإلا حالة المعاينة التجريبية. */
fun effectiveConnection(demo: DemoState, link: LinkController): ConnectionState =
    if (!link.isPaired) {
        demo.connection
    } else {
        when (link.state) {
            LinkState.Connected -> ConnectionState.Connected
            LinkState.Connecting -> ConnectionState.Connecting
            LinkState.Unpaired -> ConnectionState.Disconnected
            else -> ConnectionState.Failed
        }
    }

private const val BYTES_PER_GB = 1_073_741_824.0

fun gbOf(bytes: Long): Double = bytes / BYTES_PER_GB

@Composable
fun RealDiskCard(d: RemoteDisk) {
    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "القرص المحلي (${d.letter})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                if (d.available) formatGb(gbOf(d.totalBytes)) else "غير متاح",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.size(10.dp))
        if (d.available) {
            StorageBar(
                segments = listOf(
                    BarSegment(MaterialTheme.colorScheme.primary, d.usedBytes.toDouble()),
                    BarSegment(MaterialTheme.colorScheme.surfaceVariant, d.freeBytes.toDouble()),
                ),
            )
            Spacer(Modifier.size(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "مستخدمة ${formatGb(gbOf(d.usedBytes))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "فارغة ${formatGb(gbOf(d.freeBytes))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Text(
                "تعذّرت قراءة هذا القرص. قد يكون مقفلًا أو غير جاهز.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** أقراص الكمبيوتر الحقيقية، مع اختيار القرص عند وجود أكثر من واحد. */
@Composable
fun RealStorageSection(link: LinkController) {
    var selected by remember { mutableStateOf<String?>(null) }
    val list = link.disks
    if (list.isEmpty()) {
        StateCard(
            icon = Icons.Default.Computer,
            title = "لا توجد أقراص ثابتة",
            message = "لم يُبلّغ الكمبيوتر عن أي قرص ثابت.",
        )
        return
    }
    val current = list.firstOrNull { it.letter == selected } ?: list.first()
    if (list.size > 1) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            list.forEach { d ->
                FilterChip(
                    selected = d.letter == current.letter,
                    onClick = { selected = d.letter },
                    label = { Text(d.letter) },
                )
            }
        }
    }
    RealDiskCard(current)
}

/** حالات عدم الاتصال الحقيقي بوضوح: جارٍ، فشل، ألغاه الكمبيوتر، تغيّرت هوية الكمبيوتر. */
@Composable
fun LinkProblemCard(link: LinkController, nav: Nav) {
    when (link.state) {
        LinkState.Connecting -> StateCard(
            icon = Icons.Default.Computer,
            title = "جارٍ الاتصال بالكمبيوتر",
            message = "يتم التحقق من هوية الكمبيوتر قبل عرض بيانات التخزين.",
            loading = true,
        )
        LinkState.Failed -> StateCard(
            icon = Icons.Default.WifiOff,
            title = "تعذّر الوصول إلى الكمبيوتر",
            message = (link.lastError ?: "تأكد أن الكمبيوتر يعمل وأن برنامج ديسكاتي مشغّل وأن الهاتف على الشبكة نفسها.") +
                " نعيد المحاولة تلقائيًا.",
            isError = true,
            actionLabel = "إعادة المحاولة الآن",
            onAction = { link.retry() },
        )
        LinkState.Revoked -> StateCard(
            icon = Icons.Default.LinkOff,
            title = "أُلغي ربط هذا الهاتف",
            message = "حذف الكمبيوتر هذا الهاتف من قائمة الأجهزة المرتبطة. اربطه من جديد برمز اقتران جديد.",
            isError = true,
            actionLabel = "ربط من جديد",
            onAction = {
                link.forgetLocal()
                nav.go(Screen.Pairing)
            },
        )
        LinkState.CertChanged -> StateCard(
            icon = Icons.Default.ErrorOutline,
            title = "تغيّرت هوية الكمبيوتر",
            message = "الشهادة التي قدّمها الكمبيوتر تختلف عن التي وثقنا بها عند الربط، فقطعنا الاتصال. قد يكون البرنامج أُعيد تهيئته (reset-cert)، وقد يكون جهاز آخر ينتحل الكمبيوتر. تابع فقط إذا كنت أنت من أعاد تهيئته.",
            isError = true,
            actionLabel = "إلغاء الربط وإعادة الاقتران",
            onAction = {
                link.forgetLocal()
                nav.go(Screen.Pairing)
            },
        )
        else -> Unit
    }
}
