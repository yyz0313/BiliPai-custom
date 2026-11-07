@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.android.purebilibili.feature.video.ui.section

import android.view.Surface
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import com.android.purebilibili.core.util.Logger
import com.android.purebilibili.feature.video.player.MiniWindowSurfaceOwnership

/**
 * 将播放器的唯一视频输出绑定集中到这里。
 * 直出模式保留 PlayerView 原有行为；Anime4K 只在输入 surface 就绪后接管输出。
 *
 * 输入 Surface 由 GL 渲染线程创建/释放，而本类只在主线程被调用：释放通知
 * （onInputSurfaceChanged(null)）送达前 Surface 可能已被释放。因此任何
 * player.setVideoSurface(...) 之前都必须校验 isValid；已绑定的输入 Surface 一旦失效
 * 必须立刻解绑——否则播放器换片重建解码器时会把已释放 Surface 交给
 * MediaCodec.setOutputSurface，抛 IllegalArgumentException: The surface has been released。
 */
internal class VideoOutputRouter(
    private val player: Player
) {
    private var directPlayerView: PlayerView? = null
    private var anime4kInputSurface: Surface? = null
    private var boundAnime4kSurface: Surface? = null
    private var shouldBindDirectPlayerView = false
    private var shouldUseAnime4K = false
    private var usingAnime4K = false
    private var released = false
    private val directRebindDedupe = DirectSurfaceRebindDedupe()

    fun update(
        playerView: PlayerView?,
        inputSurface: Surface?,
        shouldBindDirectPlayerView: Boolean,
        shouldUseAnime4K: Boolean
    ) {
        if (released) return
        directPlayerView = playerView
        anime4kInputSurface = inputSurface
        this.shouldBindDirectPlayerView = shouldBindDirectPlayerView
        this.shouldUseAnime4K = shouldUseAnime4K
        applyRoute()
    }

    fun rebindDirectSurfaceIfNeeded() {
        if (usingAnime4K || !shouldBindDirectPlayerView) return
        val playerView = directPlayerView ?: return
        performDirectSurfaceRebind(playerView)
    }

    /**
     * GL 输入面被销毁或丢弃后调用：播放器此刻没有任何视频输出面，必须立刻重绑直出。
     *
     * 这与 [detachAnime4kOutput] 的区别在于它是「撤销后必须补回」：宿主迁移、输入面
     * 自然退出、GL 线程交接都会清空播放器输出，若只清不绑，声音会继续而画面永久黑屏。
     * 去重记账在这里必须失效——上一次重绑用的还是 GL 面，不能吞掉这次真实重绑。
     */
    fun reattachDirectOutputAfterGlLoss() {
        // 无论当前有没有可绑定的直出视图，都必须先遗忘 GL 输出：GL 输入面已经丢了，
        // 路由若继续记着它，后续 detach/release 会再清一次播放器输出。
        usingAnime4K = false
        boundAnime4kSurface = null
        directRebindDedupe.invalidate()
        if (!shouldBindDirectPlayerView) return
        val playerView = directPlayerView ?: return
        playerView.player = null
        playerView.player = player
    }

    /**
     * 直出重绑的唯一入口：判定 → 执行 → 记账。
     * 横竖屏/小窗切换会连续触发多次重绑（前台恢复 + 换片 + 重试），同一 PlayerView 的
     * 极短重复调用直接跳过；只有真正执行了重绑才刷新去重记账，被跳过的调用保持原值——
     * 否则一次「未执行」的调用也会把时间戳推后，使紧随其后的真实重绑被误吞。
     */
    private fun performDirectSurfaceRebind(playerView: PlayerView) {
        val nowMs = android.os.SystemClock.elapsedRealtime()
        // 「同一个 PlayerView + 同一个输出面 + 已绑定到本播放器 + 播放器正在正常出画」时，
        // Media3 已经持有视频输出：再重复 clear+set 只会把视频解码器中途摘离输出面，
        // 4K HEVC 上直接报 0x80000000，随后被误判为编码不兼容而整片降级重载
        // （用户体感：横竖屏 / 进出全屏「重复加载 + 清晰度失败 + 黑屏」）。
        // 只有输出面实例真的换了（PlayerView 重建 / 表面重建）或播放器不在稳定出画状态时
        // 才真正重绑；「没画面」的恢复由前台恢复超时与首帧重试兜底（它们同样走本入口）。
        val isDirectBindingCurrent = playerView.player === player && isPlayerOutputHealthy()
        if (!directRebindDedupe.shouldRebind(
                currentTarget = playerView,
                nowMs = nowMs,
                currentSurface = playerView.videoSurfaceView,
                isDirectBindingCurrent = isDirectBindingCurrent
            )
        ) return
        rebindPlayerSurfaceIfNeeded(playerView, player)
    }

    /** 播放器已在稳定出画：状态 READY 且尺寸已知，说明当前绑定不需要人为干预。 */
    private fun isPlayerOutputHealthy(): Boolean {
        val size = player.videoSize
        return player.playbackState == Player.STATE_READY && size.width > 0 && size.height > 0
    }

    /**
     * 在 GL 输入面被释放之前显式解绑视频输出。
     *
     * 顺序必须是「先解绑、再释放」：GL 线程释放 Surface 是异步的，若播放器仍持有该面，
     * 解码器随后切换输出面时会拿到已释放的 Surface，MediaCodec.setOutputSurface 直接抛
     * "The surface has been released"（日志里的 0x80000000 与解码器失败即由此而来）。
     * 解绑在主线程完成，释放紧随其后，因此播放器不会经过「持有失效面」的中间状态。
     *
     * [releasingSurface] 是即将被释放的那个输入面。只有它仍是当前绑定的输出面时才解绑：
     * 全屏/竖屏切换会先建新面再销毁旧面，按实例判定可确保旧面的释放不会误清新绑定。
     */
    fun detachAnime4kOutput(releasingSurface: Surface) {
        if (boundAnime4kSurface !== releasingSurface) return
        if (usingAnime4K) {
            player.clearVideoSurface()
        }
        usingAnime4K = false
        boundAnime4kSurface = null
        directRebindDedupe.invalidate()
    }

    fun release() {
        if (released) return
        released = true
        // 只要当前输出仍归本路由器（GL），就必须解绑：Surface 的释放通知可能因视图已经
        // 离开组合而不再送达，若这里依赖 isValid 判断，播放器会继续持有即将释放的面，
        // 随后换片/换宿主时触发 MediaCodec.setOutputSurface 的 released surface 异常。
        // 但小窗激活时输出面已经归小窗所有，此时清屏会连带清掉小窗已绑定的 Surface。
        if (usingAnime4K && !MiniWindowSurfaceOwnership.isMiniWindowActive) {
            player.clearVideoSurface()
        }
        usingAnime4K = false
        anime4kInputSurface = null
        boundAnime4kSurface = null
        directPlayerView = null
        directRebindDedupe.invalidate()
    }

    private fun applyRoute() {
        val inputSurface = anime4kInputSurface
        val usableInputSurface = inputSurface?.takeIf { it.isValid }
        val action = resolveVideoOutputRouteAction(
                shouldUseAnime4K = shouldUseAnime4K,
                hasUsableInputSurface = usableInputSurface != null,
                isAnime4KSurfaceBound = usingAnime4K,
                isBoundSurfaceUsable = boundAnime4kSurface?.isValid == true,
                isSameInputSurfaceBound = usingAnime4K && boundAnime4kSurface === inputSurface,
                shouldBindDirectPlayerView = shouldBindDirectPlayerView,
                hasDirectPlayerView = directPlayerView != null
            )
        Logger.w(
            "VideoOutputRouter",
            "输出面路由: action=$action, wantGl=$shouldUseAnime4K, " +
                "inputUsable=${usableInputSurface != null}, boundGl=$usingAnime4K, " +
                "sameSurface=${usingAnime4K && boundAnime4kSurface === inputSurface}, " +
                "directView=${directPlayerView != null}, bindDirect=$shouldBindDirectPlayerView"
        )
        when (action) {
            VideoOutputRouteAction.KEEP -> Unit
            VideoOutputRouteAction.BIND_ANIME4K_SURFACE -> {
                directPlayerView?.takeIf { it.player === player }?.player = null
                // 不先 clearVideoSurface：GL 上下文重建后输入面会被换成新实例，
                // 「先清空、再绑定」会让解码器经历一次无输出面的中间状态，随即
                // disconnect → connect 输出面，交接期的 updateTexImage 连续失败
                //（日志里的「输入帧解码失败」风暴与转屏黑屏即由此而来）。
                // 直接把新面交给播放器，Media3 内部会一次性切到新输出面。
                usableInputSurface?.let { player.setVideoSurface(it) }
                Logger.w("VideoOutputRouter", "已绑定 Anime4K 输入面并解绑直出")
                usingAnime4K = true
                boundAnime4kSurface = usableInputSurface
                // 输出通路已切到 GL 输入 Surface，直出记账随之失效：之后切回直出的那次
                // 重绑是必需的，不能被上一次直出重绑的 50ms 窗口误吞（目标可能是同一个 PlayerView）。
                directRebindDedupe.invalidate()
            }
            VideoOutputRouteAction.RELEASE_ANIME4K_SURFACE -> releaseAnime4kBinding()
            VideoOutputRouteAction.RELEASE_ANIME4K_AND_REBIND_DIRECT -> {
                releaseAnime4kBinding()
                Logger.w("VideoOutputRouter", "已释放 Anime4K 绑定，补绑直出 PlayerView")
                // 普通直出由 AndroidView 同步设置 PlayerView.player，保持 9.9.8.8 的稳定路径。
                // Router 只负责从 Anime4K Surface 切回直出时，补做一次显式 Surface 重绑。
                // 与外部 rebindDirectSurfaceIfNeeded() 共用同一份去重记账，同帧内的后续调用会被跳过。
                directPlayerView?.let { performDirectSurfaceRebind(it) }
            }
        }
    }

    private fun releaseAnime4kBinding() {
        if (usingAnime4K) player.clearVideoSurface()
        usingAnime4K = false
        boundAnime4kSurface = null
    }
}

/** 同一个 PlayerView 在该时间窗内的重复重绑视为同一次切换的抖动。 */
internal const val DIRECT_SURFACE_REBIND_DEDUPE_WINDOW_MS = 50L

/**
 * 横竖屏/小窗切换时前台恢复、换片重绑与首帧重试会在同一帧前后连续触发，
 * 对同一目标的重复重绑没有收益（第二次解绑/重绑只会多一次 Surface 抖动）。
 */
internal fun shouldSkipDuplicateSurfaceRebind(
    lastRebindTarget: Any?,
    currentTarget: Any?,
    elapsedSinceLastRebindMs: Long,
    dedupeWindowMs: Long = DIRECT_SURFACE_REBIND_DEDUPE_WINDOW_MS
): Boolean {
    if (lastRebindTarget == null || currentTarget == null) return false
    if (lastRebindTarget !== currentTarget) return false
    return elapsedSinceLastRebindMs in 0 until dedupeWindowMs
}

/**
 * 直出重绑去重记账，供 [VideoOutputRouter] 的两条重绑路径（外部入口与 Anime4K 切回直出）
 * 共用。只有 [shouldRebind] 返回 true（真正执行重绑）时才刷新标记；被窗口跳过的调用必须
 * 保持原值，否则一次「未执行」的调用也会把时间戳推后，使紧随其后的真实重绑被误吞。
 *
 * 输出通路切换（直出 → Anime4K 输入 Surface）时必须调用 [invalidate]：切回直出这一次重绑
 * 是必需的，不能被上一次直出重绑的 50ms 窗口误吞，即使目标 PlayerView 没有变化。
 */
internal class DirectSurfaceRebindDedupe {
    private var lastRebindTarget: Any? = null
    private var lastRebindSurface: Any? = null
    private var lastRebindAtMs: Long = 0L

    val target: Any?
        get() = lastRebindTarget

    val rebindAtMs: Long
        get() = lastRebindAtMs

    /**
     * true=需要真正执行重绑并已记账；false=本次不需要（窗口内重复，或输出面与绑定都没变）。
     *
     * [isDirectBindingCurrent] 表示 PlayerView 已绑定到本播放器且播放器在稳定出画。
     * 此时只要输出面实例还是上次真正重绑用的那一个，就说明输出通路完好：
     * 正在解码的会话不该被 clear+set 打断（4K HEVC 会因此报 0x80000000）。
     */
    fun shouldRebind(
        currentTarget: Any?,
        nowMs: Long,
        currentSurface: Any? = null,
        isDirectBindingCurrent: Boolean = false
    ): Boolean {
        if (isDirectBindingCurrent &&
            currentTarget != null &&
            currentSurface != null &&
            lastRebindTarget === currentTarget &&
            lastRebindSurface === currentSurface
        ) {
            return false
        }
        if (shouldSkipDuplicateSurfaceRebind(
                lastRebindTarget = lastRebindTarget,
                currentTarget = currentTarget,
                elapsedSinceLastRebindMs = nowMs - lastRebindAtMs
            )
        ) {
            return false
        }
        lastRebindTarget = currentTarget
        lastRebindSurface = currentSurface
        lastRebindAtMs = nowMs
        return true
    }

    fun invalidate() {
        lastRebindTarget = null
        lastRebindSurface = null
        lastRebindAtMs = 0L
    }
}

/** 路由动作：由 [resolveVideoOutputRouteAction] 按当前绑定状态裁决。 */
internal enum class VideoOutputRouteAction {
    /** 当前绑定仍然正确（GL 输入 Surface 是同一个有效 Surface），保持现状。 */
    KEEP,
    /** GL 门控开启且输入 Surface 有效：解绑直出并绑定 GL 输入 Surface。 */
    BIND_ANIME4K_SURFACE,
    /** 门控关闭或输入 Surface 失效：只解除 GL 绑定，直出交给 PlayerView 同步路径。 */
    RELEASE_ANIME4K_SURFACE,
    /** GL 绑定失效/门控关闭且允许直出：解绑并显式重绑 PlayerView Surface，避免黑屏。 */
    RELEASE_ANIME4K_AND_REBIND_DIRECT
}

/**
 * 纯逻辑裁决，覆盖“已释放的输入 Surface 绝不复用”的边界：
 * [isBoundSurfaceUsable] 为 false 表示已绑定的 GL 输入 Surface 已被 GL 渲染线程释放，
 * 此时无论门控如何都必须先解绑，否则播放器换片重建解码器时会把已释放 Surface 交给
 * MediaCodec.setOutputSurface 崩溃。
 */
internal fun resolveVideoOutputRouteAction(
    shouldUseAnime4K: Boolean,
    hasUsableInputSurface: Boolean,
    isAnime4KSurfaceBound: Boolean,
    isBoundSurfaceUsable: Boolean,
    isSameInputSurfaceBound: Boolean,
    shouldBindDirectPlayerView: Boolean,
    hasDirectPlayerView: Boolean
): VideoOutputRouteAction {
    if (shouldUseAnime4K && hasUsableInputSurface) {
        return if (isSameInputSurfaceBound && isBoundSurfaceUsable) {
            VideoOutputRouteAction.KEEP
        } else {
            VideoOutputRouteAction.BIND_ANIME4K_SURFACE
        }
    }
    if (isAnime4KSurfaceBound) {
        return if (hasDirectPlayerView && shouldBindDirectPlayerView) {
            VideoOutputRouteAction.RELEASE_ANIME4K_AND_REBIND_DIRECT
        } else {
            VideoOutputRouteAction.RELEASE_ANIME4K_SURFACE
        }
    }
    return VideoOutputRouteAction.KEEP
}
