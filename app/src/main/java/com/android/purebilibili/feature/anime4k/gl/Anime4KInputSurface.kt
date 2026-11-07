package com.android.purebilibili.feature.anime4k.gl

import android.graphics.SurfaceTexture
import android.view.Surface

/**
 * 解码器输入与 OES 纹理由 GL 线程创建、消费和释放。
 * GL 线程退出后的兜底清理只归还 Surface/SurfaceTexture，不再操作 OES 纹理。
 */
internal class Anime4KInputSurface(
    externalTextureId: Int,
    onFrameAvailable: () -> Unit
) {
    val surfaceTexture = SurfaceTexture(externalTextureId).apply {
        setOnFrameAvailableListener { onFrameAvailable() }
    }
    val surface = Surface(surfaceTexture)

    fun setDefaultBufferSize(width: Int, height: Int) {
        if (width > 0 && height > 0) {
            surfaceTexture.setDefaultBufferSize(width, height)
        }
    }

    /**
     * 把同一个 SurfaceTexture 重新挂到新 GL 上下文。
     *
     * 视图在进出全屏/转屏时会被布局迁移，GLSurfaceView 因此结束旧 GL 线程并新建上下文；
     * 但 Surface 与 SurfaceTexture 都不属于 GL 上下文。重挂而不是重建，可以让解码器的
     * 输入面保持不变，播放器无需更换视频输出面，也就不会出现解码器 disconnect/connect
     * 带来的交接期解码失败与黑屏。
     */
    fun reattachToContext(textureId: Int) {
        try {
            surfaceTexture.attachToGLContext(textureId)
        } catch (firstError: Throwable) {
            surfaceTexture.detachFromGLContext()
            surfaceTexture.attachToGLContext(textureId)
        }
    }

    fun release() {
        surfaceTexture.setOnFrameAvailableListener(null)
        surface.release()
        surfaceTexture.release()
    }
}
