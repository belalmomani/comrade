package com.diskati.pcmanager.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.diskati.pcmanager.link.LinkController

@Composable
private fun appVersionName(): String {
    val context = LocalContext.current
    return remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "غير معروف"
        } catch (e: Exception) {
            "غير معروف"
        }
    }
}

@Composable
fun SettingsScreen(demo: DemoState, link: LinkController, nav: Nav) {
    ScreenScaffold(title = "الإعدادات", onBack = { nav.back() }) { padding ->
        ScrollBody(padding) {
            SectionCard {
                NavRow(Icons.Default.Link, "إعداد وربط الكمبيوتر") { nav.go(Screen.Pairing) }
                HorizontalDivider()
                NavRow(Icons.Default.Computer, "إعدادات الاتصال") { nav.go(Screen.ConnectionSettings) }
                HorizontalDivider()
                NavRow(Icons.Default.FolderOpen, "الأقراص والمجلدات") { nav.go(Screen.Disks) }
                HorizontalDivider()
                NavRow(Icons.Default.History, "سجل الحذف والاستعادة") { nav.go(Screen.Log) }
                HorizontalDivider()
                NavRow(Icons.Default.PrivacyTip, "الإعلانات والخصوصية") { nav.go(Screen.AdsPrivacy) }
                HorizontalDivider()
                NavRow(Icons.Default.Update, "التحقق من التحديثات") { nav.go(Screen.Updates) }
                HorizontalDivider()
                NavRow(Icons.Default.Info, "معلومات التطبيق والمساعدة") { nav.go(Screen.About) }
            }

            SectionTitle("معاينة الحالات (للتطوير)")
            DemoBanner(
                if (link.isPaired) {
                    "الربط الحقيقي مفعّل، فهذه الأدوات لا تغيّر شاشات الاتصال والأقراص. تُزال قبل الإصدار النهائي."
                } else {
                    "أدوات مؤقتة لمراجعة تصميم الحالات المختلفة. تُزال قبل الإصدار النهائي."
                },
            )
            SectionCard {
                OptionChips(
                    title = "حالة الاتصال",
                    options = listOf(
                        "متصل" to ConnectionState.Connected,
                        "غير متصل" to ConnectionState.Disconnected,
                        "جارٍ الاتصال" to ConnectionState.Connecting,
                        "فشل الاتصال" to ConnectionState.Failed,
                    ),
                    selected = demo.connection,
                    onSelect = { demo.connection = it },
                )
                Spacer(Modifier.size(12.dp))
                OptionChips(
                    title = "حالة الفحص",
                    options = listOf(
                        "لم يبدأ" to ScanState.Idle,
                        "جارٍ" to ScanState.Scanning,
                        "اكتمل" to ScanState.Done,
                        "بلا نتائج" to ScanState.DoneEmpty,
                        "فشل" to ScanState.Failed,
                    ),
                    selected = demo.scan,
                    onSelect = { demo.scan = it },
                )
                Spacer(Modifier.size(12.dp))
                OptionChips(
                    title = "حالة التحديث",
                    options = listOf(
                        "لم يُفحص" to UpdateState.NotChecked,
                        "جارٍ الفحص" to UpdateState.Checking,
                        "لا تحديث" to UpdateState.UpToDate,
                        "تحديث متاح" to UpdateState.Available,
                        "فشل التنزيل" to UpdateState.DownloadFailed,
                        "فشل التثبيت" to UpdateState.InstallFailed,
                    ),
                    selected = demo.update,
                    onSelect = { demo.update = it },
                )
            }
        }
    }
}

@Composable
fun AdsPrivacyScreen(nav: Nav) {
    ScreenScaffold(title = "الإعلانات والخصوصية", onBack = { nav.back() }) { padding ->
        ScrollBody(padding) {
            DemoBanner("لم تُدمج أي مكتبة إعلانات بعد. التنفيذ في المرحلة 7.")
            SectionCard {
                Text("الإعلانات (مخطط لها)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.size(8.dp))
                Text("• إعلان Banner أسفل الشاشة لا يغطي المحتوى ولا الأزرار.", style = MaterialTheme.typography.bodyMedium)
                Text("• حد أقصى 3 إعلانات بملء الشاشة يوميًا.", style = MaterialTheme.typography.bodyMedium)
                Text("• لا إعلانات أثناء الفحص أو الحذف أو الاستعادة أو تثبيت التحديث.", style = MaterialTheme.typography.bodyMedium)
                Text("• لا تتوقف الوظائف الأساسية إذا لم يتوفر إعلان.", style = MaterialTheme.typography.bodyMedium)
            }
            SectionCard {
                Text("الخصوصية", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.size(8.dp))
                Text(
                    "المبدأ المعتمد: لا تُرفع أسماء ملفات الكمبيوتر ولا مساراتها ولا محتوياتها إلى أي خدمة سحابية.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    "ستُعرض هنا خيارات الموافقة والإفصاح عن البيانات التي تجمعها مكتبة الإعلانات بعد دمجها وفحصها فعليًا.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SectionCard {
                Text("سياسة الخصوصية", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.size(6.dp))
                Text(
                    "سيُضاف الرابط العام الدائم للسياسة في المرحلة 9 قبل النشر.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun UpdatesScreen(demo: DemoState, nav: Nav) {
    val current = appVersionName()
    ScreenScaffold(title = "التحقق من التحديثات", onBack = { nav.back() }) { padding ->
        ScrollBody(padding) {
            DemoBanner("حالات التحديث للمعاينة فقط. الفحص والتنزيل والتثبيت الفعلي في المرحلة 8.")
            SectionCard {
                InfoRow("الإصدار الحالي", current)
                if (demo.update == UpdateState.Available) {
                    InfoRow("الإصدار الجديد (مثال)", "0.3.0")
                }
            }
            when (demo.update) {
                UpdateState.NotChecked -> StateCard(
                    icon = Icons.Default.Update,
                    title = "لم يُفحص التحديث بعد",
                    message = "التحقق من وجود إصدار أحدث يُنفذ في المرحلة 8.",
                )
                UpdateState.Checking -> StateCard(
                    icon = Icons.Default.Update,
                    title = "جارٍ التحقق",
                    message = "نبحث عن إصدار أحدث.",
                    loading = true,
                )
                UpdateState.UpToDate -> StateCard(
                    icon = Icons.Default.CheckCircle,
                    title = "لا يوجد تحديث متاح",
                    message = "أنت تستخدم أحدث إصدار.",
                )
                UpdateState.Available -> {
                    SectionCard {
                        Text("ملاحظات الإصدار (مثال)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.size(6.dp))
                        Text("• تحسينات في الأداء والاستقرار.", style = MaterialTheme.typography.bodyMedium)
                        Text("• إصلاحات متنوعة.", style = MaterialTheme.typography.bodyMedium)
                    }
                    Button(
                        onClick = {},
                        enabled = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                    ) { Text("تنزيل التحديث (المرحلة 8)") }
                }
                UpdateState.DownloadFailed -> StateCard(
                    icon = Icons.Default.ErrorOutline,
                    title = "فشل تنزيل التحديث",
                    message = "تحقق من الاتصال بالإنترنت وأعد المحاولة. يمكنك أيضًا تنزيل الإصدار من GitHub Releases.",
                    isError = true,
                )
                UpdateState.InstallFailed -> StateCard(
                    icon = Icons.Default.ErrorOutline,
                    title = "فشل تثبيت التحديث",
                    message = "لم يكتمل التثبيت أو أُلغي. نسختك الحالية لم تتغير.",
                    isError = true,
                )
            }
        }
    }
}

@Composable
fun AboutScreen(nav: Nav) {
    val current = appVersionName()
    ScreenScaffold(title = "معلومات التطبيق والمساعدة", onBack = { nav.back() }) { padding ->
        ScrollBody(padding) {
            SectionCard {
                Text("ديسكاتي", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.size(4.dp))
                Text(
                    "إدارة مساحة تخزين كمبيوتر Windows من هاتفك.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(8.dp))
                HorizontalDivider()
                InfoRow("الإصدار", current)
                InfoRow("اسم الحزمة", "com.diskati.pcmanager")
            }
            SectionTitle("مساعدة")
            SectionCard {
                Text("هل يمكن استعادة كل الملفات المحذوفة؟", fontWeight = FontWeight.Bold)
                Text(
                    "لا. الملفات المنقولة إلى منطقة الحجر قابلة للاستعادة حيثما أمكن، أما الحذف النهائي فلا يمكن التراجع عنه.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.size(12.dp))
                HorizontalDivider()
                Spacer(Modifier.size(12.dp))
                Text("هل يُحذف شيء تلقائيًا؟", fontWeight = FontWeight.Bold)
                Text(
                    "لا. كل تنظيف يمر بمراجعة وتحديد يدوي وتأكيد صريح.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.size(12.dp))
                HorizontalDivider()
                Spacer(Modifier.size(12.dp))
                Text("هل الملف الكبير أو القديم غير ضروري؟", fontWeight = FontWeight.Bold)
                Text(
                    "ليس بالضرورة. الحجم والقِدَم مؤشران فقط، والقرار لك.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
