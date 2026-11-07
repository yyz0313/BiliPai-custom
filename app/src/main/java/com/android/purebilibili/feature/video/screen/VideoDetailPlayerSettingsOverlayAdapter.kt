package com.android.purebilibili.feature.video.screen

import android.content.Context
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.feature.video.danmaku.DanmakuManager
import com.android.purebilibili.feature.video.ui.section.resolveVideoPlayerDanmakuSettingsScope
import com.android.purebilibili.feature.video.viewmodel.VideoPlaybackViewModel

@Composable
internal fun VideoDetailPlayerSettingsOverlayAdapter(
    context: Context,
    viewModel: VideoPlaybackViewModel,
    isFullscreenMode: Boolean,
    isPortraitFullscreen: Boolean,
    danmakuManager: DanmakuManager,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val qualitySwitchFailureDialog by viewModel.qualitySwitchFailureDialog.collectAsStateWithLifecycle()
    val playerDiagnosticLoggingEnabled by SettingsManager
        .getPlayerDiagnosticLoggingEnabled(context)
        .collectAsStateWithLifecycle(initialValue = true, lifecycle = lifecycle)
    val qualitySwitchFailureDialogEnabled by SettingsManager
        .getQualitySwitchFailureDialogEnabled(context)
        .collectAsStateWithLifecycle(initialValue = true, lifecycle = lifecycle)
    val qualitySwitchFailureDialogOnceEnabled by SettingsManager
        .getQualitySwitchFailureDialogOnceEnabled(context)
        .collectAsStateWithLifecycle(initialValue = false, lifecycle = lifecycle)
    val qualitySwitchFailureDialogShown by SettingsManager
        .getQualitySwitchFailureDialogShown(context)
        .collectAsStateWithLifecycle(initialValue = false, lifecycle = lifecycle)
    val qualitySwitchDialogScope = rememberCoroutineScope()

    VideoDetailQualitySwitchFailureDialog(
        context = context,
        viewModel = viewModel,
        qualitySwitchFailureDialog = qualitySwitchFailureDialog,
        qualitySwitchFailureDialogEnabled = qualitySwitchFailureDialogEnabled,
        qualitySwitchFailureDialogOnceEnabled = qualitySwitchFailureDialogOnceEnabled,
        qualitySwitchFailureDialogShown = qualitySwitchFailureDialogShown,
        playerDiagnosticLoggingEnabled = playerDiagnosticLoggingEnabled,
        qualitySwitchDialogScope = qualitySwitchDialogScope,
    )

    val activeDanmakuScope = remember(isFullscreenMode, isPortraitFullscreen) {
        resolveVideoPlayerDanmakuSettingsScope(
            isFullscreen = isFullscreenMode,
            isPortraitFullscreen = isPortraitFullscreen
        )
    }
    val activeDanmakuBlockRulesRaw by SettingsManager
        .getDanmakuBlockRulesRaw(context, activeDanmakuScope)
        .collectAsStateWithLifecycle(initialValue = "", lifecycle = lifecycle)
    val danmakuPreferenceScope = rememberCoroutineScope()

    VideoDetailDanmakuContextMenu(
        context = context,
        viewModel = viewModel,
        activeDanmakuBlockRulesRaw = activeDanmakuBlockRulesRaw,
        activeDanmakuScope = activeDanmakuScope,
        sortPreferenceScope = danmakuPreferenceScope,
    )

    val selectedDanmakuMenu by viewModel.danmakuMenuState.collectAsStateWithLifecycle()
    LaunchedEffect(danmakuManager, selectedDanmakuMenu.visible) {
        if (!selectedDanmakuMenu.visible) danmakuManager.clearTapSelection()
    }
    LaunchedEffect(danmakuManager, selectedDanmakuMenu, danmakuManager.isTapSelectionActive) {
        if (selectedDanmakuMenu.visible &&
            selectedDanmakuMenu.tapAnchor?.owner === danmakuManager.tapSelectionOwner
        ) {
            if (selectedDanmakuMenu.expandedActions) danmakuManager.clearTapSelection()
            else if (!danmakuManager.isTapSelectionActive) viewModel.hideDanmakuMenu()
        }
    }
    DisposableEffect(danmakuManager, isFullscreenMode, isPortraitFullscreen) {
        onDispose {
            danmakuManager.clearTapSelection()
            viewModel.hideDanmakuMenu()
        }
    }
    DisposableEffect(danmakuManager, viewModel) {
        danmakuManager.setOnDanmakuClickListener { text, dmid, userHash, isSelf, anchor ->
            android.util.Log.d("VideoDetailScreen", "👆 Danmaku clicked: $text")
            viewModel.showDanmakuMenu(dmid, text, userHash, isSelf, anchor)
        }
        onDispose { danmakuManager.setOnDanmakuClickListener(null) }
    }
}
