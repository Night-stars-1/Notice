package moe.notice.filter

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import moe.notice.filter.ui.NoticeApp
import moe.notice.filter.ui.NoticeViewModel

class MainActivity : AppCompatActivity() {
    private val openLogs = MutableStateFlow(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        InboxChannel.ensure(this)
        openLogs.value = intent.getBooleanExtra(EXTRA_OPEN_LOGS, false)
        setContent {
            val viewModel: NoticeViewModel = viewModel()
            val showLogs by openLogs.collectAsStateWithLifecycle()
            NoticeApp(
                viewModel = viewModel,
                openLogs = showLogs,
                onLogsConsumed = { openLogs.value = false },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_LOGS, false)) {
            openLogs.value = true
        }
    }

    companion object {
        const val EXTRA_OPEN_LOGS = "open_logs"
    }
}
