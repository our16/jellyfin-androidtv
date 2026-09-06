package org.jellyfin.danmaku

import master.flame.danmaku.danmaku.model.BaseDanmaku
import master.flame.danmaku.danmaku.model.Duration
import master.flame.danmaku.danmaku.model.IDanmakus
import master.flame.danmaku.danmaku.model.android.Danmakus
import master.flame.danmaku.danmaku.parser.BaseDanmakuParser
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream

/**
 * Parser for Bilibili-style XML danmaku format.
 * This is the most common danmaku format used by Jellyfin servers.
 * 
 * Format example:
 * <d p="time,type,size,color,timestamp,pool,uid_hash,dmid">text</d>
 * 
 * Parameters:
 * - time: Display time in seconds (float)
 * - type: 1=scroll right-to-left, 4=bottom, 5=top, 6=scroll left-to-right, 7=special
 * - size: Font size (25=small, 36=medium, 45=large)
 * - color: Text color as decimal integer
 * - timestamp: Unix timestamp when posted
 * - pool: Danmaku pool (0=normal, 1=subtitle, 2=special)
 * - uid_hash: User ID hash
 * - dmid: Danmaku ID
 */
class JellyfinDanmakuParser : BaseDanmakuParser() {

    // Diagnostics: how many <d> nodes were seen, how many danmakus were
    // created and how many actually landed in the collection.
    var xmlDCount = 0
        private set
    var createdCount = 0
        private set
    var addedCount = 0
        private set
    var parseErrorCount = 0
        private set

    override fun parse(): IDanmakus {
        val source = mDataSource ?: return Danmakus()
        
        val danmakus = Danmakus()
        
        try {
            val inputStream = source.data() as? InputStream
            if (inputStream != null) {
                parseXmlStream(inputStream, danmakus)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            parseErrorCount++
        }
        
        return danmakus
    }

    /**
     * Parse danmaku from XML input stream.
     */
    private fun parseXmlStream(inputStream: InputStream, danmakus: Danmakus) {
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(inputStream, "UTF-8")
        
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                if (parser.name == "d") {
                    xmlDCount++
                    val pAttribute = parser.getAttributeValue(null, "p")
                    val text = parser.nextText()
                    
                    if (pAttribute != null && text != null) {
                        val danmaku = parseDanmakuItem(pAttribute, text)
                        if (danmaku != null) {
                            createdCount++
                            if (danmakus.addItem(danmaku)) {
                                addedCount++
                            } else {
                                parseErrorCount++
                            }
                        }
                    }
                }
            }
            eventType = parser.next()
        }
    }

    /**
     * Parse a single danmaku item from p attribute and text.
     */
    private fun parseDanmakuItem(pAttribute: String, text: String): BaseDanmaku? {
        try {
            val params = pAttribute.split(",")
            if (params.size < 8) return null
            
            val time = (params[0].toFloat() * 1000).toLong() // Convert to milliseconds
            val type = params[1].toInt()
            val fontSize = params[2].toFloat()
            val color = params[3].toLong().toInt() // Bilibili uses decimal color
            
            // Create danmaku based on type
            val danmaku = when (type) {
                1 -> createDanmaku(BaseDanmaku.TYPE_SCROLL_RL)
                4 -> createDanmaku(BaseDanmaku.TYPE_FIX_BOTTOM)
                5 -> createDanmaku(BaseDanmaku.TYPE_FIX_TOP)
                6 -> createDanmaku(BaseDanmaku.TYPE_SCROLL_LR)
                7 -> createDanmaku(BaseDanmaku.TYPE_SPECIAL)
                else -> createDanmaku(BaseDanmaku.TYPE_SCROLL_RL) // Default to scroll R2L
            }
            
            if (danmaku != null) {
                danmaku.text = text
                danmaku.setTime(time)
                // Without a timer, BaseDanmaku.isTimeOut() is ALWAYS true (mTimer == null)
                // and every danmaku gets skipped by the renderer's first gate.
                danmaku.setTimer(mTimer)
                // Scale the raw font size by display density so it stays readable on TV screens
                danmaku.textSize = fontSize * mDispDensity
                danmaku.textColor = color
                danmaku.textShadowColor = 0 // No shadow for now
                
                // Set duration based on type
                if (type == 1 || type == 6) {
                    // Scroll danmaku - duration is calculated by factory
                } else {
                    // Fixed danmaku - use common duration
                    danmaku.setDuration(Duration(COMMON_DANMAKU_DURATION))
                }
            } else {
                parseErrorCount++
            }
            
            return danmaku
        } catch (e: Exception) {
            e.printStackTrace()
            parseErrorCount++
            return null
        }
    }

    /**
     * Create a danmaku instance using the factory.
     */
    private fun createDanmaku(type: Int): BaseDanmaku? {
        return mContext?.mDanmakuFactory?.createDanmaku(type, mContext)
    }

    companion object {
        private const val COMMON_DANMAKU_DURATION = 3800L // Bilibili default duration
    }
}
