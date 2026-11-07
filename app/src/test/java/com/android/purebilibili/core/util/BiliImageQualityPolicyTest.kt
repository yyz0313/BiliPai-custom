package com.android.purebilibili.core.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class BiliImageQualityPolicyTest {
    private val image = "https://i0.hdslb.com/bfs/archive/cover.jpg"

    @Test
    fun `normal and saver use distinct quality URLs without changing dimensions`() {
        val sized = "$image@960w_600h_1c.webp"
        assertEquals("$image@960w_600h_1c_100q.webp", BiliImageQualityPolicy.resolveUrl(sized, false))
        assertEquals("$image@960w_600h_1c_10q.webp", BiliImageQualityPolicy.resolveUrl(sized, true))
        assertNotEquals(BiliImageQualityPolicy.resolveUrl(sized, false), BiliImageQualityPolicy.resolveUrl(sized, true))
    }

    @Test
    fun `existing quality is replaced and repeated preparation is idempotent`() {
        val saver = BiliImageQualityPolicy.resolveUrl("$image@640w_400h_80q.webp", true)
        assertEquals("$image@640w_400h_10q.webp", saver)
        assertEquals(saver, BiliImageQualityPolicy.resolveUrl(saver, true))
        assertEquals("$image@640w_400h_100q.webp", BiliImageQualityPolicy.resolveUrl(saver, false))
        assertEquals("$image@640w_400h_100q.webp", BiliImageQualityPolicy.resolveUrl("$image@640w_400h", false))
    }

    @Test
    fun `raw cover and avatar keep their aspect ratio`() {
        assertEquals("$image@100q.webp", BiliImageQualityPolicy.resolveUrl(image, false))
        assertEquals("https://i1.hdslb.com/bfs/face/avatar.jpg@100q.webp",
            BiliImageQualityPolicy.resolveUrl("https://i1.hdslb.com/bfs/face/avatar.jpg", false))
    }

    @Test
    fun `foreign signed animated and local resources remain unchanged`() {
        listOf(
            "https://example.com/image.jpg",
            "https://hdslb.com.example.com/image.jpg",
            "$image?signature=secret",
            "$image@!thumbnail",
            "https://i0.hdslb.com/bfs/face/avatar.gif",
            "https://i0.hdslb.com/bfs/emote/emote.png",
            "https://i0.hdslb.com/bfs/garb/pendant.webp",
            "android.resource://com.android.purebilibili/42",
            "file:///tmp/image.png",
        ).forEach { url ->
            assertEquals(url, BiliImageQualityPolicy.resolveUrl(url, false))
            assertEquals(url, BiliImageQualityPolicy.resolveUrl(url, true))
        }
    }
}
