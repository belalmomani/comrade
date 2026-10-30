package com.diskati.pcmanager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.diskati.pcmanager.data.DemoData
import com.diskati.pcmanager.data.DemoDisk
import com.diskati.pcmanager.data.formatGb
import com.diskati.pcmanager.data.formatMb
import com.diskati.pcmanager.link.LinkController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(demo: DemoState, link: LinkController, nav: Nav) {
    val paired = link.isPaired
    val connection = effectiveConnection(demo, link)
    val demoDisk = DemoData.disks.first { it.letter == demo.selectedDisk }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            link.pc?.computerName ?: DemoData.COMPUTER_NAME,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        ConnectionStatusLine(connection)
                    }
                },
                actions = {
                    IconButton(onClick = { nav.go(Screen.Log) }) {
                        Icon(Icons.Default.History, contentDescription = "سجل العمليات")
                    }
                    IconButton(onClick = { nav.go(Screen.Settings) }) {
                        Icon(Icons.Default.Settings, contentDescription = "الإعدادات")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        ScrollBody(padding) {
            if (paired) {
                if (link.state == com.diskati.pcmanager.link.LinkState.Connected) {
                    RealStorageSection(link)
                } else {
                    LinkProblemCard(link, nav)
                }
                RealScanNotice()
                SectionCard {
                    Text(
                        "تصنيف الملفات (تطبيقات، صور، فيديوهات…) يظهر بعد تنفيذ الفحص الحقيقي في المرحلة 5. لا نعرض أرقامًا تقديرية.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                DemoBanner()
                DemoConnectionSection(demo, nav, demoDisk)
                DemoScanCard(demo, nav)
                if (demo.connection == ConnectionState.Connected) {
                    SectionTitle("فئات الملفات")
                    SectionCard {
                        demoDisk.categories.forEach { c ->
                            CategoryRow(
                                icon = c.icon,
                                color = c.color,
                                name = c.name,
                                sizeText = formatGb(c.gb),
                                fraction = c.gb / demoDisk.totalGb,
                            )
                        }
                    }
                }
                LastScanCard(demo)
            }
            SectionCard {
                NavRow(Icons.Default.FolderOpen, "الأقراص والمجلدات") { nav.go(Screen.Disks) }
                HorizontalDivider()
                NavRow(Icons.Default.History, "سجل الحذف والاستعادة") { nav.go(Screen.Log) }
                HorizontalDivider()
                NavRow(Icons.Default.Update, "التحديثات") { nav.go(Screen.Updates) }
                HorizontalDivider()
                NavRow(Icons.Default.Settings, "الإعدادات والخصوصية") { nav.go(Screen.Settings) }
            }
        }
    }
}

@Composable
private fun ConnectionStatusLine(state: ConnectionState) {
    val (text, color) = when (state) {
        ConnectionState.Connected -> "متصل" to Color(0xFF34A853)
        ConnectionState.Disconnected -> "غير متصل" to MaterialTheme.colorScheme.outline
        ConnectionState.Connecting -> "جارٍ الاتصال…" to Color(0xFFF9AB00)
        ConnectionState.Failed -> "فشل الاتصال" to MaterialTheme.colorScheme.error
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** الفحص الحقيقي غير منفذ بعد، فلا زر يبدأ فحصًا وهميًا عند وجود ربط حقيقي. */
@Composable
private fun RealScanNotice() {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Text("فحص التخزين", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.size(6.dp))
        Text(
            "الفحص الحقيقي للملفات الكبيرة والمكررة والقديمة والمؤقتة يُنفذ في المرحلة 5. لا يُحذف شيء من الكمبيوتر في هذه النسخة.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(12.dp))
        Button(
            onClick = {},
            enabled = false,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
        ) { Text("بدء الفحص (غير متاح بعد)") }
    }
}

@Composable
private fun ColumnScope.DemoConnectionSection(demo: DemoState, nav: Nav, disk: DemoDisk) {
    when (demo.connection) {
        ConnectionState.Connected -> StorageCard(demo, disk)
        ConnectionState.Connecting -> StateCard(
            icon = Icons.Default.Computer,
            title = "جارٍ الاتصال بالكمبيوتر",
            message = "يتم التحقق من الجهاز قبل عرض بيانات التخزين.",
            loading = true,
        )
        ConnectionState.Disconnected -> StateCard(
            icon = Icons.Default.LinkOff,
            title = "الكمبيوتر غير متصل",
            message = "اربط هاتفك بكمبيوتر Windows لعرض الأقراص والمساحات.",
            actionLabel = "ربط الكمبيوتر",
            onAction = { nav.go(Screen.Pairing) },
        )
        ConnectionState.Failed -> StateCard(
            icon = Icons.Default.WifiOff,
            title = "فشل الاتصال بالكمبيوتر",
            message = "تأكد أن الكمبيوتر يعمل وأن الهاتف على الشبكة نفسها، ثم أعد المحاولة.",
            isError = true,
            actionLabel = "إعدادات الاتصال",
            onAction = { nav.go(Screen.ConnectionSettings) },
        )
    }
}

@Composable
private fun StorageCard(demo: DemoState, disk: DemoDisk) {
    SectionCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DemoData.disks.forEach { d ->
                FilterChip(
                    selected = d.letter == demo.selectedDisk,
                    onClick = { demo.selectedDisk = d.letter },
                    label = { Text(d.letter) },
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "القرص المحلي (${disk.letter})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                formatGb(disk.totalGb),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.size(10.dp))
        StorageBar(
            segments = disk.categories.map { BarSegment(it.color, it.gb) } +
                BarSegment(MaterialTheme.colorScheme.surfaceVariant, disk.freeGb),
        )
        Spacer(Modifier.size(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "مستخدمة ${formatGb(disk.usedGb)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "فارغة ${formatGb(disk.freeGb)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DemoScanCard(demo: DemoState, nav: Nav) {
    val connected = demo.connection == ConnectionState.Connected
    when (demo.scan) {
        ScanState.Idle -> SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text("فحص التخزين", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.size(6.dp))
            Text(
                "تحليل الملفات الكبيرة والمكررة والقديمة والمؤقتة. لا يُحذف شيء قبل مراجعتك وتأكيدك.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(12.dp))
            Button(
                onClick = { demo.scan = ScanState.Scanning },
                enabled = connected,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) { Text("بدء الفحص") }
            if (!connected) {
                Spacer(Modifier.size(6.dp))
                Text(
                    "يتطلب اتصالًا بالكمبيوتر.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        ScanState.Scanning -> SectionCard {
            Text("جارٍ الفحص…", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.size(10.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.size(8.dp))
            Text(
                "معاينة للواجهة فقط. نسبة التقدم الحقيقية تُعرض بعد تنفيذ محرك الفحص (المرحلة 5).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { demo.scan = ScanState.Idle },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text("إلغاء") }
                OutlinedButton(
                    onClick = { demo.scan = ScanState.Done },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text("معاينة: إنهاء الفحص") }
            }
        }

        ScanState.Done -> SectionCard {
            Text("اكتمل الفحص (بيانات تجريبية)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.size(6.dp))
            Text(
                "راجع النتائج واختر ما تريد تنظيفه. لا يُحدَّد أي ملف تلقائيًا.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { nav.go(Screen.ScanResults) },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text("عرض النتائج") }
                OutlinedButton(
                    onClick = { demo.scan = ScanState.Scanning },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text("إعادة الفحص") }
            }
        }

        ScanState.DoneEmpty -> StateCard(
            icon = Icons.Default.Search,
            title = "لا توجد نتائج",
            message = "لم يُعثر على ملفات تطابق معايير الفحص الحالية.",
            actionLabel = "إعادة الفحص",
            onAction = { demo.scan = ScanState.Scanning },
        )

        ScanState.Failed -> StateCard(
            icon = Icons.Default.ErrorOutline,
            title = "فشل الفحص",
            message = "تعذّر إكمال الفحص. لم تُعرض أي نتائج جزئية على أنها نهائية.",
            isError = true,
            actionLabel = "إعادة المحاولة",
            onAction = { demo.scan = ScanState.Scanning },
        )
    }
}

@Composable
private fun LastScanCard(demo: DemoState) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Text("آخر عملية فحص", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.size(8.dp))
        if (demo.scan == ScanState.Done) {
            val files = DemoData.files
            InfoRow("العناصر المكتشفة", "${files.size}")
            InfoRow("الحجم الإجمالي المكتشف", formatMb(files.sumOf { it.sizeMb }))
            Text(
                "أرقام تجريبية. المساحة المتوقع تحريرها تُحسب من اختيارك فقط.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                "لا توجد عملية فحص مكتملة بعد.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
