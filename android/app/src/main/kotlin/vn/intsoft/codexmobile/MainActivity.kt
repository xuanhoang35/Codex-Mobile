package vn.intsoft.codexmobile

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import vn.intsoft.codexmobile.ui.CodexApp
import vn.intsoft.codexmobile.ui.CodexViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: CodexViewModel by viewModels()

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

        handleIntent(intent)

        setContent {
            CodexApp(viewModel)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent?.data?.let { uri ->
            viewModel.handleAuthDeepLink(uri)
        }
    }
}
