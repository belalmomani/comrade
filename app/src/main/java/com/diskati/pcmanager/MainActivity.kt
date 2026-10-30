package com.diskati.pcmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.diskati.pcmanager.link.LinkController
import com.diskati.pcmanager.link.SecureStore
import com.diskati.pcmanager.ui.DiskatiApp
import com.diskati.pcmanager.ui.DiskatiTheme

class MainActivity : ComponentActivity() {
    private lateinit var link: LinkController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        link = LinkController(SecureStore(applicationContext))
        link.load()
        setContent {
            DiskatiTheme {
                DiskatiApp(link)
            }
        }
    }

    // الاستعلام الدوري عن حالة الكمبيوتر يعمل والتطبيق ظاهر فقط.
    override fun onStart() {
        super.onStart()
        link.startPolling()
    }

    override fun onStop() {
        link.stopPolling()
        super.onStop()
    }
}
