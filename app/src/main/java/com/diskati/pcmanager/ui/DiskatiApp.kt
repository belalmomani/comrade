package com.diskati.pcmanager.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import com.diskati.pcmanager.link.LinkController

@Composable
fun DiskatiApp(link: LinkController) {
    val demo = remember { DemoState() }
    val stack = remember { mutableStateListOf<Screen>(Screen.Home) }
    val nav = remember { Nav(stack) }

    BackHandler(enabled = stack.size > 1) { nav.back() }

    when (val current = stack[stack.lastIndex]) {
        Screen.Home -> HomeScreen(demo, link, nav)
        Screen.Pairing -> PairingScreen(link, nav)
        Screen.Disks -> DisksScreen(demo, link, nav)
        Screen.ScanResults -> ScanResultsScreen(demo, nav)
        is Screen.FileDetails -> FileDetailsScreen(current.fileId, demo, nav)
        Screen.Review -> ReviewScreen(demo, nav)
        Screen.ConfirmCleanup -> ConfirmCleanupScreen(demo, nav)
        Screen.Log -> LogScreen(nav)
        Screen.Settings -> SettingsScreen(demo, link, nav)
        Screen.ConnectionSettings -> ConnectionSettingsScreen(demo, link, nav)
        Screen.AdsPrivacy -> AdsPrivacyScreen(nav)
        Screen.Updates -> UpdatesScreen(demo, nav)
        Screen.About -> AboutScreen(nav)
    }
}
