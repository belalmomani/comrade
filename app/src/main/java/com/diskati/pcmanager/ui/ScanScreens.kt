package com.diskati.pcmanager.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.diskati.pcmanager.data.DemoData
import com.diskati.pcmanager.data.DemoFile
import com.diskati.pcmanager.data.FileKind
import com.diskati.pcmanager.data.formatMb

private fun selectedFiles(demo: DemoState): List<DemoFile> =
    DemoData.files.filter { it.id in demo.selected }

/** مجموعات مكررة اختيرت كل نسخها: خطر فقدان الملف كاملًا. */
private fun fullySelectedGroups(selected: List<DemoFile>): List<String> {
    val ids = selected.map { it.id }.toSet()
    return selected.mapNotNull { it.groupId }.distinct().filter { g ->
        DemoData.files.filter { it.groupId == g }.all { it.id in ids }
    }
}

@Composable
fun ScanResultsScreen(demo: DemoState, nav: Nav) {
    var filter by remember { mutableStateOf(FileKind.Large) }
    val items = DemoData.files
        .filter { it.kind == filter }
        .sortedByDescending { it.sizeMb }
    val chosen = selectedFiles(demo)

    ScreenScaffold(
        title = "نتائج الفحص",
        onBack = { nav.back() },
        bottomBar = {
            if (demo.scan == ScanState.Done && chosen.isNotEmpty()) {
                Surface(tonalElevation = 3.dp, color = MaterialTheme.colorScheme.surface) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text("المحدد: ${chosen.size}", fontWeight = FontWeight.Bold)
                            Text(
                                "المتوقع تحريره: ${formatMb(chosen.sumOf { it.sizeMb })}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Button(
                            onClick = { nav.go(Screen.Review) },
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) { Text("مراجعة المحدد") }
                    }
                }
            }
        },
    ) { padding ->
        ScrollBody(padding) {
            DemoBanner()
            when (demo.scan) {
                ScanState.Idle -> StateCard(
                    icon = Icons.Default.Search,
                    title = "لم يبدأ فحص بعد",
                    message = "ابدأ الفحص من الشاشة الرئيسية لعرض النتائج هنا.",
                    actionLabel = "الرجوع للرئيسية",
                    onAction = { nav.toHome() },
                )
                ScanState.Scanning -> SectionCard {
                    Text("الفحص جارٍ", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.size(8.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.size(8.dp))
                    Text(
                        "لا تُعرض نتائج جزئية على أنها نهائية. ستظهر النتائج عند اكتمال الفحص.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ScanState.Failed -> StateCard(
                    icon = Icons.Default.ErrorOutline,
                    title = "فشل الفحص",
                    message = "تعذّر إكمال الفحص، فلا توجد نتائج موثوقة لعرضها.",
                    isError = true,
                    actionLabel = "الرجوع للرئيسية",
                    onAction = { nav.toHome() },
                )
                ScanState.DoneEmpty -> StateCard(
                    icon = Icons.Default.Search,
                    title = "لا توجد نتائج",
                    message = "لم يُعثر على ملفات تطابق معايير الفحص.",
                    actionLabel = "الرجوع للرئيسية",
                    onAction = { nav.toHome() },
                )
                ScanState.Done -> {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FileKind.entries.forEach { k ->
                            FilterChip(
                                selected = k == filter,
                                onClick = { filter = k },
                                label = { Text(k.label) },
                            )
                        }
                    }
                    Text(
                        kindHint(filter),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    items.forEach { f ->
                        FileRow(
                            file = f,
                            checked = f.id in demo.selected,
                            onToggle = { demo.toggle(f.id) },
                            onOpen = { nav.go(Screen.FileDetails(f.id)) },
                        )
                    }
                }
            }
        }
    }
}

private fun kindHint(kind: FileKind): String = when (kind) {
    FileKind.Large -> "مرتبة من الأكبر إلى الأصغر. كِبَر الملف لا يعني أنه غير ضروري."
    FileKind.Duplicate -> "لا تُحدَّد كل النسخ تلقائيًا. اترك نسخة واحدة على الأقل."
    FileKind.Old -> "قِدَم الملف لا يعني أنه غير مهم. راجع تاريخ آخر تعديل بعناية."
    FileKind.Temp -> "مواقع مؤقتة معروفة فقط. أي مسار غير معروف يُستبعد."
}

@Composable
private fun FileRow(file: DemoFile, checked: Boolean, onToggle: () -> Unit, onOpen: () -> Unit) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = checked, onCheckedChange = { onToggle() })
            Spacer(Modifier.width(4.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onOpen)
                    .padding(vertical = 4.dp),
            ) {
                Text(file.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                LtrText(file.path)
                Spacer(Modifier.size(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(formatMb(file.sizeMb), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    Text(
                        "آخر تعديل: ${file.modified}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun FileDetailsScreen(fileId: String, demo: DemoState, nav: Nav) {
    val file = DemoData.file(fileId)
    ScreenScaffold(title = "تفاصيل الملف", onBack = { nav.back() }) { padding ->
        ScrollBody(padding) {
            if (file == null) {
                StateCard(
                    icon = Icons.Default.ErrorOutline,
                    title = "الملف غير موجود",
                    message = "لم يعد هذا الملف ضمن نتائج الفحص.",
                    isError = true,
                )
            } else {
                DemoBanner()
                SectionCard {
                    Text(file.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.size(6.dp))
                    LtrText(file.path)
                    Spacer(Modifier.size(10.dp))
                    HorizontalDivider()
                    InfoRow("الحجم", formatMb(file.sizeMb))
                    InfoRow("آخر تعديل", file.modified)
                    InfoRow("الفئة", file.kind.label)
                }
                val hint = file.note ?: kindHint(file.kind)
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Text(hint, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                val isSelected = file.id in demo.selected
                Button(
                    onClick = { demo.toggle(file.id) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                ) { Text(if (isSelected) "إلغاء التحديد" else "تحديد للتنظيف") }
            }
        }
    }
}

@Composable
fun ReviewScreen(demo: DemoState, nav: Nav) {
    val chosen = selectedFiles(demo)
    val risky = fullySelectedGroups(chosen)
    ScreenScaffold(title = "مراجعة الملفات المحددة", onBack = { nav.back() }) { padding ->
        ScrollBody(padding) {
            DemoBanner()
            if (chosen.isEmpty()) {
                StateCard(
                    icon = Icons.Default.Search,
                    title = "لا ملفات محددة",
                    message = "ارجع إلى النتائج واختر ما تريد تنظيفه.",
                    actionLabel = "العودة للنتائج",
                    onAction = { nav.back() },
                )
            } else {
                SectionCard {
                    InfoRow("عدد الملفات المحددة", "${chosen.size}")
                    InfoRow("المساحة المتوقع تحريرها", formatMb(chosen.sumOf { it.sizeMb }))
                    Text(
                        "تقدير أولي. قد يختلف الفعلي إذا تغيرت الملفات أو تعذّر الوصول إليها.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (risky.isNotEmpty()) {
                    StateCard(
                        icon = Icons.Default.Warning,
                        title = "حددت كل نسخ ملف مكرر",
                        message = "إذا نفّذت التنظيف فلن تبقى أي نسخة من هذا الملف. ألغِ تحديد نسخة واحدة على الأقل.",
                        isError = true,
                    )
                }
                chosen.forEach { f ->
                    SectionCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(f.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                LtrText(f.path)
                                Text(
                                    "${f.kind.label} • ${formatMb(f.sizeMb)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            IconButton(onClick = { demo.toggle(f.id) }) {
                                Icon(Icons.Default.Close, contentDescription = "إزالة من التحديد")
                            }
                        }
                    }
                }
                Button(
                    onClick = { nav.go(Screen.ConfirmCleanup) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                ) { Text("متابعة إلى التأكيد") }
            }
        }
    }
}

@Composable
fun ConfirmCleanupScreen(demo: DemoState, nav: Nav) {
    val chosen = selectedFiles(demo)
    var understood by remember { mutableStateOf(false) }
    var executed by remember { mutableStateOf(false) }
    ScreenScaffold(title = "تأكيد التنظيف", onBack = { nav.back() }) { padding ->
        ScrollBody(padding) {
            DemoBanner("معاينة للواجهة: لا يُحذف ولا يُنقل أي ملف في هذه المرحلة.")
            StateCard(
                icon = Icons.Default.Warning,
                title = "تحذير قبل التنفيذ",
                message = "ستُنقل الملفات المحددة إلى منطقة الحجر حيثما أمكن. لا نضمن أن كل الملفات قابلة للاستعادة، والحذف النهائي لا يمكن التراجع عنه.",
                isError = true,
            )
            SectionCard {
                InfoRow("عدد الملفات", "${chosen.size}")
                InfoRow("المساحة المتوقع تحريرها", formatMb(chosen.sumOf { it.sizeMb }))
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { understood = !understood }
                    .heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = understood, onCheckedChange = { understood = it })
                Text("أفهم أن هذه العملية قد لا تكون قابلة للتراجع وأوافق على المتابعة.")
            }
            if (executed) {
                StateCard(
                    icon = Icons.Default.CheckCircle,
                    title = "انتهت المعاينة",
                    message = "لم يُحذف أي ملف. التنفيذ الفعلي مع الحجر والسجل يُبنى في المرحلة 6.",
                    actionLabel = "الرجوع للرئيسية",
                    onAction = { nav.toHome() },
                )
            } else {
                Button(
                    onClick = { executed = true },
                    enabled = understood && chosen.isNotEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                ) { Text("تأكيد (معاينة)") }
                OutlinedButton(
                    onClick = { nav.back() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                ) { Text("إلغاء") }
            }
        }
    }
}

@Composable
fun LogScreen(nav: Nav) {
    ScreenScaffold(title = "سجل الحذف والاستعادة", onBack = { nav.back() }) { padding ->
        ScrollBody(padding) {
            DemoBanner("سجل تجريبي. السجل الحقيقي يُبنى في المرحلة 6.")
            if (DemoData.logs.isEmpty()) {
                StateCard(
                    icon = Icons.Default.History,
                    title = "لا توجد عمليات",
                    message = "ستظهر هنا عمليات التنظيف والاستعادة.",
                )
            }
            DemoData.logs.forEach { e ->
                SectionCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(e.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                        SuggestionChip(onClick = {}, label = { Text(e.status.label) })
                    }
                    Text(e.details, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.size(4.dp))
                    Text(
                        e.time,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
