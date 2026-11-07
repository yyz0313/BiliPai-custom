package com.android.purebilibili.feature.video.ui.components

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CommentTranslationPolicyTest {
    private fun show(text: String, switch: Int? = null, oid: Long = 1, rpid: Long = 2) =
        shouldOfferCommentTranslation(switch, text, oid, rpid)

    @Test
    fun missingSwitchOffersTranslationForForeignText() {
        assertTrue(show("This is a great video!"))
        assertTrue(show("こんにちは！", switch = 0))
        assertTrue(show("Прекрасное видео"))
        assertTrue(show("中文 mixed with English"))
    }

    @Test
    fun missingSwitchIgnoresChineseUrlsMentionsAndEmotes() {
        assertFalse(show("这个视频真好"))
        assertFalse(show("谢谢 UP 主"))
        assertFalse(show("看这里 https://example.com/path"))
        assertFalse(show("@username [happy] 赞"))
        assertFalse(show("123 !!!"))
        assertFalse(show("Great video", oid = 0))
        assertFalse(show("Great video", rpid = 0))
    }

    @Test
    fun explicitServerSwitchTakesPrecedence() {
        assertTrue(show("中文", switch = 2))
        assertFalse(show("Great video", switch = 1))
        assertFalse(show("Great video", switch = 3))
    }
}
