package moe.notice.filter.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Process
import moe.notice.filter.data.LogRepository
import moe.notice.filter.data.NotificationDetailsCodec

class NotificationLogProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        val uid = Binder.getCallingUid()
        if (uid != Process.SYSTEM_UID && uid != Process.myUid()) return null
        val ctx = context ?: return null
        val record = LogRepository.get(ctx).add(
            packageName = values?.getAsString(COL_PACKAGE).orEmpty(),
            title = values?.getAsString(COL_TITLE).orEmpty(),
            text = values?.getAsString(COL_TEXT).orEmpty(),
            timestamp = values?.getAsLong(COL_TIMESTAMP) ?: System.currentTimeMillis(),
            blocked = values?.getAsInteger(COL_BLOCKED) == 1,
            ruleId = values?.getAsString(COL_RULE_ID),
            ruleName = values?.getAsString(COL_RULE_NAME),
            details = NotificationDetailsCodec.fromJson(values?.getAsString(COL_DETAILS)),
        )
        return uri.buildUpon().appendPath(record.id).build()
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun getType(uri: Uri): String = "vnd.android.cursor.item/vnd.moe.notice.filter.log"

    companion object {
        const val AUTHORITY = "moe.notice.filter.logs"
        const val COL_PACKAGE = "package"
        const val COL_TITLE = "title"
        const val COL_TEXT = "text"
        const val COL_TIMESTAMP = "timestamp"
        const val COL_BLOCKED = "blocked"
        const val COL_RULE_ID = "rule_id"
        const val COL_RULE_NAME = "rule_name"
        const val COL_DETAILS = "details"

        val CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY/notifications")
    }
}
