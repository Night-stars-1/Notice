package moe.notice.filter.data

import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.FileOutputStream
import moe.notice.filter.ModuleStatus
import moe.notice.filter.domain.SpamDelta

/** Writes the tuning delta into the module's libxposed remote file so system_server can read it. */
object SpamDeltaWriter {
    fun write(delta: SpamDelta): Boolean {
        val pfd = ModuleStatus.openRemoteFile(SpamDelta.REMOTE_FILE) ?: return false
        return try {
            ParcelFileDescriptor.AutoCloseOutputStream(pfd).use { out ->
                (out as FileOutputStream).channel.truncate(0)
                out.write(delta.encode())
                out.flush()
            }
            true
        } catch (t: Throwable) {
            Log.w("Notice", "spam delta write failed", t)
            false
        }
    }
}
