package moe.notice.filter.xposed

import android.app.Notification
import android.os.Bundle
import android.os.Parcelable

internal object NotificationText {
    data class Extracted(
        val title: String,
        val body: String,
        val combined: String,
    )

    fun extract(notification: Notification): Extracted {
        val extras = notification.extras
        if (extras == null) {
            val ticker = notification.tickerText?.toString().orEmpty()
            return Extracted(title = ticker, body = "", combined = ticker)
        }
        val parts = ArrayList<String>(8)
        append(parts, notification.tickerText)
        val title = firstText(
            extras.getCharSequence(Notification.EXTRA_TITLE),
            extras.getCharSequence(Notification.EXTRA_TITLE_BIG),
            notification.tickerText,
        )
        append(parts, extras.getCharSequence(Notification.EXTRA_TITLE))
        append(parts, extras.getCharSequence(Notification.EXTRA_TITLE_BIG))
        val bodyParts = ArrayList<String>(6)
        append(bodyParts, extras.getCharSequence(Notification.EXTRA_TEXT))
        append(bodyParts, extras.getCharSequence(Notification.EXTRA_SUB_TEXT))
        append(bodyParts, extras.getCharSequence(Notification.EXTRA_INFO_TEXT))
        append(bodyParts, extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT))
        append(bodyParts, extras.getCharSequence(Notification.EXTRA_BIG_TEXT))
        extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.forEach { append(bodyParts, it) }
        appendMessages(bodyParts, extras)
        for (item in bodyParts) append(parts, item)
        return Extracted(
            title = title,
            body = bodyParts.joinToString("\n"),
            combined = parts.joinToString("\n"),
        )
    }

    @Suppress("DEPRECATION")
    private fun appendMessages(parts: MutableList<String>, extras: Bundle) {
        val messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES) ?: return
        for (item in messages) {
            when (item) {
                is Bundle -> append(parts, item.getCharSequence("text"))
                is Parcelable -> appendParcelableMessage(parts, item)
            }
        }
    }

    private fun appendParcelableMessage(parts: MutableList<String>, item: Parcelable) {
        try {
            val text = item.javaClass.methods
                .firstOrNull { it.name == "getText" && it.parameterCount == 0 }
                ?.invoke(item)
            append(parts, text as? CharSequence)
        } catch (_: Throwable) {
            // MessagingStyle.Message shape varies across API levels.
        }
    }

    private fun append(parts: MutableList<String>, value: CharSequence?) {
        val text = value?.toString()?.trim().orEmpty()
        if (text.isNotEmpty()) parts += text
    }

    private fun firstText(vararg values: CharSequence?): String {
        for (value in values) {
            val text = value?.toString()?.trim().orEmpty()
            if (text.isNotEmpty()) return text
        }
        return ""
    }
}
