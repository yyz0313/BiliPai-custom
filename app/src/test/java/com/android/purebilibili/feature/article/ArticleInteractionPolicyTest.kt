package com.android.purebilibili.feature.article

import com.android.purebilibili.data.model.response.ArticleStats
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ArticleInteractionPolicyTest {
    @Test
    fun `partial interaction stats retain detail seed and preserve real zero`() {
        val merged = mergeArticleInteractionStats(
            primary = ArticleStats(like = 0),
            seed = ArticleStats(like = 15, reply = 40, favorite = 6),
        )
        assertEquals(0L, merged?.like)
        assertEquals(40L, merged?.reply)
        assertEquals(6L, merged?.favorite)
        assertNull(merged?.share)
        assertNull(mergeArticleInteractionStats(null, null))
    }

    @Test
    fun `action count remains bounded for large counts`() {
        assertEquals("0", formatArticleActionCount(0))
        assertEquals("9999", formatArticleActionCount(9999))
        assertEquals("1.0万", formatArticleActionCount(10000))
        assertEquals("1.0亿", formatArticleActionCount(100000000))
        assertEquals("9999亿+", formatArticleActionCount(Long.MAX_VALUE))
    }
}
