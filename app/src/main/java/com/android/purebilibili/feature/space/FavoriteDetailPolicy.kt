package com.android.purebilibili.feature.space

internal enum class FavoriteDetailSortOrder(val label: String) {
    NEWEST_FIRST("倒序（最新在前）"),
    OLDEST_FIRST("正序（最早在前）"),
}

internal fun favoriteLastServerPage(resourceCount: Int): Int =
    ((resourceCount.coerceAtLeast(0).toLong() + 19L) / 20L).coerceAtLeast(1L).toInt()
