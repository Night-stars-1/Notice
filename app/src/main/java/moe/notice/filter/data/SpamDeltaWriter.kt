package moe.notice.filter.data

import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.FileOutputStream
import moe.notice.filter.ModuleStatus
import moe.notice.filter.domain.SpamDelta

/** 把微调 delta 写入模块的 libxposed 远程文件，以便 system_server 读取。 */
object SpamDeltaWriter {
    /** 读取当前已下发的修正量；不存在、为空或损坏时返回 null。 */
    fun read(): SpamDelta? {
        val pfd = ModuleStatus.openRemoteFile(SpamDelta.REMOTE_FILE) ?: return null
        return runCatching {
            ParcelFileDescriptor.AutoCloseInputStream(pfd).use { SpamDelta.decode(it) }
        }.getOrNull()?.takeUnless { it.isEmpty }
    }

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
