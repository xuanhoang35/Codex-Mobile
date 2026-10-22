package vn.intsoft.codexmobile

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.viewmodel.compose.viewModel
import vn.intsoft.codexmobile.ui.CodexApp
import vn.intsoft.codexmobile.ui.CodexViewModel

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        try {
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != 0) {
                notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        } catch (_: Throwable) { }
        setContent {
            val viewModel: CodexViewModel = viewModel()
            CodexApp(viewModel)
        }
    }
}

