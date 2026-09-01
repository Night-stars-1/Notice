package moe.notice.filter.domain

import java.io.File
import moe.notice.filter.data.ReleaseInfo

/** 应用内更新的状态机。 */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val latestTag: String) : UpdateState
    data class Available(val info: ReleaseInfo) : UpdateState
    data class Downloading(val info: ReleaseInfo, val progress: Float) : UpdateState
    data class Ready(val info: ReleaseInfo, val file: File) : UpdateState
    data class Error(val message: String) : UpdateState
}
