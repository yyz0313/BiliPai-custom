package com.android.purebilibili.feature.profile

import androidx.compose.runtime.staticCompositionLocalOf
import com.android.purebilibili.data.model.AccountLedgerKind

/** 个人中心的多个布局共用同一明细入口，由页面宿主提供导航回调。 */
internal val LocalProfileLedgerNavigation = staticCompositionLocalOf<((AccountLedgerKind) -> Unit)?> { null }

internal fun ledgerMenuActions(open: ((AccountLedgerKind) -> Unit)?): List<com.android.purebilibili.core.ui.components.AppWindowAction> =
    if (open == null) emptyList() else AccountLedgerKind.entries.map { kind ->
        com.android.purebilibili.core.ui.components.AppWindowAction(label = kind.title, onClick = { open(kind) })
    }
