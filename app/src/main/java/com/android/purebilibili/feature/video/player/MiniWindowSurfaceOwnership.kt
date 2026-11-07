package com.android.purebilibili.feature.video.player

/**
 * 小窗输出所有权信号（进程内、无 Context 依赖）。
 *
 * 小窗激活期间，详情页的 [com.android.purebilibili.feature.video.ui.section.VideoOutputRouter]
 * 不得在销毁时清空播放器视频输出：详情页退出动画晚于小窗入场，若此时清屏会连带清掉
 * 小窗 PlayerView 已绑定的 Surface；而 Media3 PlayerView 对同一 player 实例的重复赋值
 * 是提前返回的 no-op，小窗无法自动重绑，画面会一直黑到下次切页。
 *
 * 该状态必须与 [MiniPlayerManager.isMiniMode] 的每次赋值同步，
 * 由 MiniPlayerManager 私有的 setMiniModeActive 统一写入，避免漏点。
 */
internal object MiniWindowSurfaceOwnership {

    @Volatile
    var isMiniWindowActive: Boolean = false
        private set

    /** 小窗状态唯一写入点。 */
    fun update(isMiniWindowActive: Boolean) {
        this.isMiniWindowActive = isMiniWindowActive
    }
}
