package com.android.purebilibili.feature.anime4k.gl

import android.content.Context
import android.graphics.PixelFormat
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.view.Surface
import com.android.purebilibili.plugin.sdk.Anime4KConfig
import com.android.purebilibili.core.util.Logger

/** Anime4K 的可见输出 SurfaceView，解码器输入 Surface 由 renderer 异步提供。 */
class Anime4KGLSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    initialConfig: Anime4KConfig = Anime4KConfig()
) : GLSurfaceView(context, attrs) {

    var onInputSurfaceChanged: (Surface?) -> Unit = {}
    var onFirstFrameRendered: () -> Unit = {}
    var onPipelineError: (Throwable) -> Unit = {}

    private val pipelineRenderer = Anime4KPipelineRenderer(
        context = context,
        initialConfig = initialConfig,
        onFrameAvailable = { requestRender() },
        onInputSurfaceChanged = { surface -> post { onInputSurfaceChanged(surface) } },
        onFirstFrameRendered = { post { onFirstFrameRendered() } },
        onPipelineError = { error -> post { onPipelineError(error) } }
    )

    init {
        setEGLContextClientVersion(3)
        setEGLConfigChooser(8, 8, 8, 8, 0, 0)
        holder.setFormat(PixelFormat.TRANSLUCENT)
        setZOrderMediaOverlay(true)
        setPreserveEGLContextOnPause(true)
        setRenderer(pipelineRenderer)
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    fun updateConfig(config: Anime4KConfig) {
        queueEvent {
            pipelineRenderer.setConfig(config)
            // 切换算法或模型完成后再请求绘制，避免旧请求先被消费后画面停在透明帧。
            requestRender()
        }
    }

    fun updateInputSize(width: Int, height: Int) {
        queueEvent { pipelineRenderer.setInputSize(width, height) }
    }

    fun updateFlip(horizontal: Boolean, vertical: Boolean) {
        queueEvent { pipelineRenderer.setFlip(horizontal, vertical) }
        requestRender()
    }

    fun updateDisplayScaleMode(scaleMode: Anime4KDisplayScaleMode) {
        queueEvent { pipelineRenderer.setDisplayScaleMode(scaleMode) }
        requestRender()
    }

    override fun onResume() {
        super.onResume()
        queueEvent { pipelineRenderer.ensureInputSurface() }
    }

    override fun onPause() {
        // 转屏/进出全屏会让 GLSurfaceView 走 onPause→onResume。此处若释放解码器输入面，
        // 播放器会被迫 disconnect/connect，交接期解码失败会放大成长时间黑屏（用户侧
        // 表现正是竖屏切横屏黑屏）。输入面不属于 GL 上下文，保留它即可在 onResume 重挂。
        // 真正的释放交给组合层销毁视图时的 onDetachedFromWindow。
        super.onPause()
    }
    override fun onDetachedFromWindow() {
        // 转屏/全屏宿主迁移会 detach 再 attach；此处若释放解码器输入面，播放器会被迫
        // disconnect/connect，交接期解码失败即用户看到的「竖屏切横屏黑屏」。
        // 输入面不属于 GL 上下文，保留它，等 onAttachedToWindow 后重挂即可。
        Logger.w("Anime4KGL", "GL 视图 detach（保留输入面，等待重新挂载）")
        super.onDetachedFromWindow()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        Logger.w("Anime4KGL", "GL 视图 attach")
        // 转屏/全屏宿主迁移后若已有输入面但 GL 线程没有新的帧通知，
        // 视图会一直保持透明/黑面；重新挂载后主动补一帧。
        requestRender()
    }

    /** 组合层显式销毁视图时释放 GL 资源与解码器输入面（幂等）。 */
    fun releaseNow() {
        Logger.w("Anime4KGL", "GL 视图销毁并释放")
        queueEvent { pipelineRenderer.release() }
    }
}
