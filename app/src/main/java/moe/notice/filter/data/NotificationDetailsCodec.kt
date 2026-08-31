package moe.notice.filter.data

import moe.notice.filter.domain.NotificationDetails
import org.json.JSONArray
import org.json.JSONObject

object NotificationDetailsCodec {
    fun toJson(details: NotificationDetails): String = JSONObject().apply {
        put("ticker", details.ticker)
        put("subText", details.subText)
        put("infoText", details.infoText)
        put("summaryText", details.summaryText)
        put("bigText", details.bigText)
        put("bigTitle", details.bigTitle)
        put("channelId", details.channelId)
        put("category", details.category)
        put("groupKey", details.groupKey)
        put("template", details.template)
        put("tag", details.tag)
        put("notificationId", details.notificationId)
        put("number", details.number)
        put("flags", details.flags)
        put("visibility", details.visibility)
        put("progress", details.progress)
        put("actions", JSONArray(details.actions))
        put("textLines", JSONArray(details.textLines))
        put("messages", JSONArray(details.messages))
    }.toString()

    fun fromJson(raw: String?): NotificationDetails {
        if (raw.isNullOrBlank()) return NotificationDetails()
        return runCatching {
            val obj = JSONObject(raw)
            NotificationDetails(
                ticker = obj.optString("ticker"),
                subText = obj.optString("subText"),
                infoText = obj.optString("infoText"),
                summaryText = obj.optString("summaryText"),
                bigText = obj.optString("bigText"),
                bigTitle = obj.optString("bigTitle"),
                channelId = obj.optString("channelId"),
                category = obj.optString("category"),
                groupKey = obj.optString("groupKey"),
                template = obj.optString("template"),
                tag = obj.optString("tag"),
                notificationId = obj.optInt("notificationId"),
                number = obj.optInt("number"),
                flags = obj.optInt("flags"),
                visibility = obj.optInt("visibility"),
                progress = obj.optString("progress"),
                actions = obj.optJSONArray("actions").toStringList(),
                textLines = obj.optJSONArray("textLines").toStringList(),
                messages = obj.optJSONArray("messages").toStringList(),
            )
        }.getOrDefault(NotificationDetails())
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        val out = ArrayList<String>(length())
        for (i in 0 until length()) {
            val value = optString(i)
            if (value.isNotBlank()) out += value
        }
        return out
    }
}
