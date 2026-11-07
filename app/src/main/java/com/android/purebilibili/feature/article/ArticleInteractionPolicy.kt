package com.android.purebilibili.feature.article

import com.android.purebilibili.data.model.response.ArticleStats
import java.util.Locale

internal fun formatArticleActionCount(count: Long): String = when {
    count >= 1_000_000_000_000L -> "9999亿+"
    count >= 100_000_000L -> "%.1f亿".format(Locale.ROOT, count / 100_000_000.0)
    count >= 10_000L -> "%.1f万".format(Locale.ROOT, count / 10_000.0)
    else -> count.coerceAtLeast(0L).toString()
}

/** 互动接口未返回的计数继续使用详情种子，真实零值始终保留。 */
internal fun mergeArticleInteractionStats(primary: ArticleStats?, seed: ArticleStats?): ArticleStats? =
    primary?.copy(
        view = primary.view ?: seed?.view,
        like = primary.like ?: seed?.like,
        favorite = primary.favorite ?: seed?.favorite,
        reply = primary.reply ?: seed?.reply,
        share = primary.share ?: seed?.share,
    ) ?: seed
