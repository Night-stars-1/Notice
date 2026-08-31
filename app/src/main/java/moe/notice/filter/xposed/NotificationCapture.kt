package moe.notice.filter.xposed

import android.app.Notification
import android.os.Bundle
import android.os.Parcelable
import moe.notice.filter.domain.NotificationDetails

internal object NotificationCapture {
    fun capture(notification: Notification, args: Array<Any?>): NotificationDetails {
        return runCatching { captureInner(notification, args) }.getOrDefault(NotificationDetails())
    }

    private fun captureInner(notification: Notification, args: Array<Any?>): NotificationDetails {
        val extras = notification.extras
        val nIndex = args.indexOfFirst { it is Notification }
        val id = if (nIndex > 0) args.getOrNull(nIndex - 1) as? Int ?: 0 else 0
        val tag = if (nIndex > 1) args.getOrNull(nIndex - 2) as? String ?: "" else ""
        val template = extras?.getString(Notification.EXTRA_TEMPLATE).orEmpty().substringAfterLast('$')
        return NotificationDetails(
            ticker = clip(notification.tickerText?.toString().orEmpty()),
            subText = clip(charSeq(extras, Notification.EXTRA_SUB_TEXT)),
            infoText = clip(charSeq(extras, Notification.EXTRA_INFO_TEXT)),
            summaryText = clip(charSeq(extras, Notification.EXTRA_SUMMARY_TEXT)),
            bigText = clip(charSeq(extras, Notification.EXTRA_BIG_TEXT)),
            bigTitle = clip(charSeq(extras, Notification.EXTRA_TITLE_BIG)),
            channelId = notification.channelId.orEmpty(),
            category = notification.category.orEmpty(),
            groupKey = notification.group.orEmpty(),
            template = template,
            tag = tag,
            notificationId = id,
            number = notification.number,
            flags = notification.flags,
            visibility = notification.visibility,
            progress = progress(extras),
            actions = notification.actions.orEmpty().mapNotNull {
                it.title?.toString()?.trim()?.takeIf { title -> title.isNotEmpty() }
            },
            textLines = textLines(extras),
            messages = messages(extras),
        )
    }

    private fun progress(extras: Bundle?): String {
        if (extras == null || !extras.containsKey(Notification.EXTRA_PROGRESS)) return ""
        val indeterminate = extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false)
        if (indeterminate) return "不确定进度"
        val current = extras.getInt(Notification.EXTRA_PROGRESS, 0)
        val max = extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
        return if (max > 0) "$current / $max" else current.toString()
    }

    private fun textLines(extras: Bundle?): List<String> {
        val lines = extras?.getCharSequenceArray(Notification.EXTRA_TEXT_LINES) ?: return emptyList()
        return lines.map { clip(it?.toString().orEmpty()) }.filter { it.isNotBlank() }
    }

    @Suppress("DEPRECATION")
    private fun messages(extras: Bundle?): List<String> {
        val items = extras?.getParcelableArray(Notification.EXTRA_MESSAGES) ?: return emptyList()
        val out = ArrayList<String>(items.size)
        for (item in items) {
            val text = when (item) {
                is Bundle -> item.getCharSequence("text")?.toString()
                is Parcelable -> messageText(item)
                else -> null
            }.orEmpty().trim()
            if (text.isNotEmpty()) out += clip(text)
        }
        return out
    }

    private fun messageText(item: Parcelable): String? {
        return try {
            val text = item.javaClass.methods
                .firstOrNull { method -> method.name == "getText" && method.parameterCount == 0 }
                ?.invoke(item) as? CharSequence
            text?.toString()
        } catch (_: Throwable) {
            null
        }
    }

    private fun charSeq(extras: Bundle?, key: String): String {
        return extras?.getCharSequence(key)?.toString().orEmpty().trim()
    }

    private fun clip(value: String): String {
        if (value.length <= MAX) return value
        return value.take(MAX) + "…"
    }

    private const val MAX = 4000
}
