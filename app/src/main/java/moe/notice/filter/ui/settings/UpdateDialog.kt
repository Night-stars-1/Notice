package moe.notice.filter.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import moe.notice.filter.R
import moe.notice.filter.domain.UpdateState

/** 新版本对话框：展示更新说明、下载进度，并交给系统安装器。在任何页面都可弹出。 */
@Composable
fun UpdateDialog(
    updateState: UpdateState,
    onDownload: () -> Unit,
    onInstall: () -> Boolean,
    onDismiss: () -> Unit,
) {
    val updateInfo = when (updateState) {
        is UpdateState.Available -> updateState.info
        is UpdateState.Downloading -> updateState.info
        is UpdateState.Ready -> updateState.info
        else -> return
    }
        val context = LocalContext.current
        val installFailed = stringResource(R.string.update_install_failed)
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.update_dialog_title, updateInfo.tag)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (updateState is UpdateState.Downloading) {
                        val p = updateState.progress
                        if (p >= 0f) LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                        else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    Text(
                        text = updateInfo.notes.ifBlank { updateInfo.htmlUrl },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                }
            },
            confirmButton = {
                when (updateState) {
                    is UpdateState.Ready -> TextButton(onClick = {
                        if (!onInstall()) Toast.makeText(context, installFailed, Toast.LENGTH_SHORT).show()
                    }) { Text(stringResource(R.string.update_install)) }
                    is UpdateState.Downloading -> Unit
                    else -> TextButton(onClick = onDownload) { Text(stringResource(R.string.update_download_install)) }
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.update_later)) }
            },
        )
}
