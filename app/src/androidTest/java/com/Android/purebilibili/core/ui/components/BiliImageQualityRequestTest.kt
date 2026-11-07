package com.android.purebilibili.core.ui.components

import androidx.test.platform.app.InstrumentationRegistry
import coil3.request.ImageRequest
import com.android.purebilibili.core.util.originalBiliImage
import com.android.purebilibili.core.util.withBiliImageQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Test

class BiliImageQualityRequestTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val url = "https://i0.hdslb.com/bfs/archive/cover.jpg@640w_400h.webp"

    @Test
    fun repeatedPreparationReusesRequestAndCacheIdentity() {
        val request = ImageRequest.Builder(context)
            .data(url)
            .memoryCacheKey("cover")
            .diskCacheKey("cover")
            .placeholderMemoryCacheKey("cover")
            .build()
            .withBiliImageQuality(dataSaver = false)

        assertSame(request, request.withBiliImageQuality(dataSaver = false))
        assertEquals(request.memoryCacheKeyExtras, request.placeholderMemoryCacheKey?.extras)
        assertEquals("cover|bilipai-image-quality-v1:100", request.diskCacheKey)
    }

    @Test
    fun qualitySwitchUpdatesAllCacheIdentitiesWithoutChangingDimensions() {
        val high = ImageRequest.Builder(context)
            .data(url)
            .memoryCacheKey("cover")
            .diskCacheKey("cover")
            .placeholderMemoryCacheKey("cover")
            .build()
            .withBiliImageQuality(dataSaver = false)
        val saver = high.withBiliImageQuality(dataSaver = true)

        assertEquals("https://i0.hdslb.com/bfs/archive/cover.jpg@640w_400h_10q.webp", saver.data)
        assertNotEquals(high.memoryCacheKeyExtras, saver.memoryCacheKeyExtras)
        assertEquals(saver.memoryCacheKeyExtras, saver.placeholderMemoryCacheKey?.extras)
        assertEquals("cover|bilipai-image-quality-v1:10", saver.diskCacheKey)
        assertEquals(high.data, saver.withBiliImageQuality(dataSaver = false).data)
        assertEquals(high.diskCacheKey, saver.withBiliImageQuality(dataSaver = false).diskCacheKey)
    }

    @Test
    fun originalImageRemainsExemptAcrossQualitySwitches() {
        val original = ImageRequest.Builder(context).data(url).originalBiliImage().build()
        assertSame(original, original.withBiliImageQuality(dataSaver = true))
        assertSame(original, original.withBiliImageQuality(dataSaver = false))
    }
}
