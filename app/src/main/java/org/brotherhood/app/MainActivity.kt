package org.brotherhood.app

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import org.brotherhood.app.ui.BrotherhoodApp
import org.brotherhood.app.ui.BrotherhoodTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        intent?.dataString?.let(viewModel::acceptDeepLink)
        setContent {
            BrotherhoodTheme {
                BrotherhoodApp(viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.dataString?.let(viewModel::acceptDeepLink)
    }

    override fun onStart() {
        super.onStart()
        viewModel.onAppForeground()
    }

    override fun onStop() {
        viewModel.onAppBackground()
        super.onStop()
    }
}
