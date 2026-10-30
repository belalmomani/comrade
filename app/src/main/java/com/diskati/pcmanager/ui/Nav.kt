package com.diskati.pcmanager.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList

sealed interface Screen {
    data object Home : Screen
    data object Pairing : Screen
    data object Disks : Screen
    data object ScanResults : Screen
    data class FileDetails(val fileId: String) : Screen
    data object Review : Screen
    data object ConfirmCleanup : Screen
    data object Log : Screen
    data object Settings : Screen
    data object ConnectionSettings : Screen
    data object AdsPrivacy : Screen
    data object Updates : Screen
    data object About : Screen
}

class Nav(private val stack: SnapshotStateList<Screen>) {
    fun go(screen: Screen) {
        stack.add(screen)
    }

    fun back() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    fun toHome() {
        while (stack.size > 1) stack.removeAt(stack.lastIndex)
    }
}

enum class ConnectionState { Connected, Disconnected, Connecting, Failed }
enum class ScanState { Idle, Scanning, Done, DoneEmpty, Failed }
enum class UpdateState { NotChecked, Checking, UpToDate, Available, DownloadFailed, InstallFailed }

/**
 * حالة المعاينة للمرحلة 2 فقط. لا تمثل اتصالًا ولا فحصًا حقيقيًا.
 * تُستبدل بالحالة الحقيقية في المراحل 4 و5 و8.
 */
class DemoState {
    var connection by mutableStateOf(ConnectionState.Connected)
    var scan by mutableStateOf(ScanState.Idle)
    var update by mutableStateOf(UpdateState.NotChecked)
    var selectedDisk by mutableStateOf("C:")
    val selected: SnapshotStateList<String> = mutableStateListOf()

    fun toggle(id: String) {
        if (selected.contains(id)) selected.remove(id) else selected.add(id)
    }
}
