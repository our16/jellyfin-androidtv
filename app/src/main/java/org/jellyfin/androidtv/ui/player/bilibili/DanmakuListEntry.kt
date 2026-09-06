package org.jellyfin.androidtv.ui.player.bilibili

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * One danmaku entry shown in the player's danmaku list side panel.
 */
data class DanmakuListEntry(
	/** Position in the video (ms) */
	val timeMs: Long,
	val text: String,
	/** 1 scroll, 4 bottom, 5 top */
	val type: Int,
	/** Sender hash if the danmaku format provides one */
	val sender: String?,
	/** Epoch seconds when the danmaku was sent (0 when unknown) */
	val sentAt: Long,
) {
	val typeLabel: String
		get() = when (type) {
			4 -> "底部"
			5 -> "顶部"
			else -> "滚动"
		}

	val sentDateLabel: String?
		get() = if (sentAt > 0) sentDateFormat.format(Date(sentAt * 1000L)) else null

	companion object {
		private val sentDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
	}
}

private val danmakuEntryLock = Any()

/**
 * Parse the raw danmaku XML (Bilibili flavour: `<d p="time,type,size,color,date,pool,uid,...">text</d>`)
 * into entries for the side panel. Tolerant to missing attributes and unknown tags.
 */
fun parseDanmakuList(xml: String): List<DanmakuListEntry> = synchronized(danmakuEntryLock) {
	runCatching {
		val parser = XmlPullParserFactory.newInstance().newPullParser()
		parser.setInput(StringReader(xml))

		val entries = ArrayList<DanmakuListEntry>()
		var event = parser.eventType
		while (event != XmlPullParser.END_DOCUMENT) {
			if (event == XmlPullParser.START_TAG && parser.name.equals("d", ignoreCase = true)) {
				val p = parser.getAttributeValue(null, "p")
				val text = parser.nextText()
				if (!p.isNullOrBlank() && text.isNotBlank()) {
					val parts = p.split(",")
					val timeMs = ((parts.getOrNull(0)?.toFloatOrNull() ?: 0f) * 1000L).toLong().coerceAtLeast(0L)
					val type = parts.getOrNull(1)?.toIntOrNull() ?: 1
					val sentAt = parts.getOrNull(4)?.toLongOrNull() ?: 0L
					val sender = parts.getOrNull(6)?.takeIf { it.isNotBlank() && it != "0" }
					entries.add(DanmakuListEntry(timeMs, text.trim(), type, sender, sentAt))
				}
			}
			event = try {
				parser.next()
			} catch (_: Exception) {
				XmlPullParser.END_DOCUMENT
			}
		}
		entries.sortedBy { it.timeMs }
	}.getOrDefault(emptyList())
}
