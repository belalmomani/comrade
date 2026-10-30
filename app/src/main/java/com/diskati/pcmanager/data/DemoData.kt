package com.diskati.pcmanager.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import java.util.Locale

// كل ما في هذا الملف بيانات تجريبية للمرحلة 2 فقط. ليست من كمبيوتر حقيقي.

enum class FileKind(val label: String) {
    Large("كبيرة"),
    Duplicate("مكررة"),
    Old("قديمة"),
    Temp("مؤقتة"),
}

enum class LogStatus(val label: String) {
    Quarantined("نُقل إلى الحجر"),
    Restored("تمت الاستعادة"),
    Failed("فشل"),
    Deleted("حذف نهائي"),
}

data class DemoCategory(val name: String, val gb: Double, val color: Color, val icon: ImageVector)

data class DemoDisk(
    val letter: String,
    val totalGb: Double,
    val categories: List<DemoCategory>,
    val folders: List<Pair<String, Double>>,
) {
    val usedGb: Double get() = categories.sumOf { it.gb }
    val freeGb: Double get() = totalGb - usedGb
}

data class DemoFile(
    val id: String,
    val name: String,
    val path: String,
    val sizeMb: Double,
    val modified: String,
    val kind: FileKind,
    val groupId: String? = null,
    val note: String? = null,
)

data class DemoLog(
    val id: String,
    val time: String,
    val title: String,
    val details: String,
    val status: LogStatus,
)

fun formatGb(v: Double): String =
    if (v % 1.0 == 0.0) "${v.toLong()} GB" else String.format(Locale.US, "%.1f GB", v)

fun formatMb(mb: Double): String =
    if (mb >= 1024.0) String.format(Locale.US, "%.1f GB", mb / 1024.0)
    else String.format(Locale.US, "%.1f MB", mb)

object DemoData {
    const val COMPUTER_NAME = "كمبيوتر المكتب (تجريبي)"

    private val apps = Color(0xFF4285F4)
    private val photos = Color(0xFF34A853)
    private val videos = Color(0xFFF9AB00)
    private val docs = Color(0xFFA78BFA)
    private val temp = Color(0xFFE57373)
    private val other = Color(0xFF8E99A6)

    val disks: List<DemoDisk> = listOf(
        DemoDisk(
            letter = "C:",
            totalGb = 512.0,
            categories = listOf(
                DemoCategory("التطبيقات والبرامج", 145.0, apps, Icons.Default.Apps),
                DemoCategory("الصور", 68.0, photos, Icons.Default.PhotoLibrary),
                DemoCategory("الفيديوهات", 102.0, videos, Icons.Default.Videocam),
                DemoCategory("المستندات والمشاريع", 42.0, docs, Icons.Default.Description),
                DemoCategory("الملفات المؤقتة", 18.0, temp, Icons.Default.DeleteOutline),
                DemoCategory("أخرى", 60.0, other, Icons.Default.Folder),
            ),
            folders = listOf(
                "Users" to 210.0,
                "Program Files" to 98.0,
                "Program Files (x86)" to 47.0,
                "ProgramData" to 22.0,
                "Games" to 58.0,
            ),
        ),
        DemoDisk(
            letter = "D:",
            totalGb = 931.0,
            categories = listOf(
                DemoCategory("التطبيقات والبرامج", 80.0, apps, Icons.Default.Apps),
                DemoCategory("الصور", 190.0, photos, Icons.Default.PhotoLibrary),
                DemoCategory("الفيديوهات", 260.0, videos, Icons.Default.Videocam),
                DemoCategory("المستندات والمشاريع", 70.0, docs, Icons.Default.Description),
                DemoCategory("الملفات المؤقتة", 4.0, temp, Icons.Default.DeleteOutline),
                DemoCategory("أخرى", 8.0, other, Icons.Default.Folder),
            ),
            folders = listOf(
                "Media" to 410.0,
                "Backups" to 120.0,
                "Projects" to 70.0,
                "Downloads" to 12.0,
            ),
        ),
    )

    val files: List<DemoFile> = listOf(
        DemoFile("l1", "Gameplay_2024.mp4", "C:\\Users\\Demo\\Videos\\Gameplay_2024.mp4", 4812.0, "2024-03-12", FileKind.Large),
        DemoFile("l2", "win_install.iso", "C:\\Users\\Demo\\Downloads\\win_install.iso", 5632.0, "2023-11-02", FileKind.Large),
        DemoFile("l3", "project_render.mov", "C:\\Users\\Demo\\Videos\\project_render.mov", 2301.0, "2025-01-20", FileKind.Large),
        DemoFile("d1", "Report_final.pdf", "C:\\Users\\Demo\\Documents\\Report_final.pdf", 48.2, "2025-05-14", FileKind.Duplicate, groupId = "g1"),
        DemoFile("d2", "Report_final (1).pdf", "C:\\Users\\Demo\\Downloads\\Report_final (1).pdf", 48.2, "2025-05-14", FileKind.Duplicate, groupId = "g1"),
        DemoFile("d3", "Report_final_copy.pdf", "C:\\Users\\Demo\\Desktop\\Report_final_copy.pdf", 48.2, "2025-05-14", FileKind.Duplicate, groupId = "g1"),
        DemoFile("o1", "old_project.zip", "C:\\Users\\Demo\\Documents\\Archive\\old_project.zip", 860.0, "2019-06-30", FileKind.Old),
        DemoFile("o2", "setup_2018.exe", "C:\\Users\\Demo\\Downloads\\setup_2018.exe", 312.0, "2018-09-12", FileKind.Old),
        DemoFile(
            "t1", "الملفات المؤقتة للمستخدم", "C:\\Users\\Demo\\AppData\\Local\\Temp", 1450.0, "2026-10-20", FileKind.Temp,
            note = "موقع مؤقت معروف. تُستثنى الملفات التي تستخدمها عمليات Windows الحالية.",
        ),
        DemoFile(
            "t2", "ذاكرة الصور المصغرة", "C:\\Users\\Demo\\AppData\\Local\\Microsoft\\Windows\\Explorer", 420.0, "2026-10-18", FileKind.Temp,
            note = "يعيد Windows إنشاءها عند الحاجة.",
        ),
    )

    val logs: List<DemoLog> = listOf(
        DemoLog("e1", "2026-10-27 21:14", "تنظيف مؤقتة", "2 عنصران • 1.8 GB • نجح 2 وفشل 0", LogStatus.Quarantined),
        DemoLog("e2", "2026-10-27 21:20", "استعادة ملف", "Report_final (1).pdf إلى موقعه الأصلي", LogStatus.Restored),
        DemoLog("e3", "2026-10-25 09:02", "تنظيف ملفات قديمة", "3 عناصر • نجح 2 وفشل 1 (الملف قيد الاستخدام)", LogStatus.Failed),
        DemoLog("e4", "2026-10-20 18:40", "حذف من الحجر", "1 عنصر • 860.0 MB", LogStatus.Deleted),
    )

    fun file(id: String): DemoFile? = files.firstOrNull { it.id == id }
}
