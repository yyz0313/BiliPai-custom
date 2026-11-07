@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.android.purebilibili.feature.bangumi

import com.android.purebilibili.navigation.tabSelectedPage
import com.android.purebilibili.navigation.tabIndicatorPosition
import com.android.purebilibili.core.ui.AppContentColors

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import com.android.purebilibili.core.ui.components.AppLazyColumn as LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.AppDialogAction
import com.android.purebilibili.core.ui.AppChromeSizeTokens
import com.android.purebilibili.core.ui.ImmersiveAppScaffold as AppScaffold
import com.android.purebilibili.core.ui.AppSpacingTokens
import com.android.purebilibili.core.ui.AppTopBar
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.components.AppButton
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.components.AppListItem
import com.android.purebilibili.core.ui.components.AppSlider
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.core.ui.components.AppTextField
import com.android.purebilibili.core.ui.components.AppSwitch
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.core.ui.rememberAppBackIcon
import com.android.purebilibili.data.model.response.BangumiReviewItem
import com.android.purebilibili.data.model.response.BangumiReviewType
import com.android.purebilibili.data.repository.BangumiReviewRepository
import com.android.purebilibili.feature.home.components.BottomBarLiquidSegmentedControl
import com.android.purebilibili.core.ui.components.AppHorizontalPager
import com.android.purebilibili.navigation.animatePagerSelection
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop

private data class CachedBangumiReviews(
    val items: List<BangumiReviewItem>,
    val nextCursor: String,
    val hasMore: Boolean,
)

@Composable
fun BangumiReviewScreen(
    mediaId: Long,
    title: String,
    onBack: () -> Unit,
    onOpenWeb: (String, String) -> Unit = { _, _ -> },
    onOpenArticle: (Long, String) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val darkTheme = com.android.purebilibili.core.ui.isAppInDarkTheme()
    val scope = rememberCoroutineScope()
    val reviewTypes = remember { BangumiReviewType.entries.toList() }
    val pagerState = rememberPagerState { reviewTypes.size }
    val pagerDragHeld by pagerState.interactionSource.collectIsDraggedAsState()
    val nativePagerGestures = com.android.purebilibili.core.theme.LocalAppUiStyle.current !=
        com.android.purebilibili.core.theme.AppUiStyle.MIUIX
    val shortListState = rememberLazyListState()
    val longListState = rememberLazyListState()
    var reviewType by remember { mutableStateOf(BangumiReviewType.SHORT) }
    val listState = if (reviewType == BangumiReviewType.SHORT) shortListState else longListState
    var sort by remember { mutableIntStateOf(0) }
    var items by remember { mutableStateOf<List<BangumiReviewItem>>(emptyList()) }
    var nextCursor by remember { mutableStateOf("") }
    var hasMore by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showComposer by remember { mutableStateOf(false) }
    var editingReview by remember { mutableStateOf<BangumiReviewItem?>(null) }
    var deletingReview by remember { mutableStateOf<BangumiReviewItem?>(null) }
    var busyReviews by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var submittingReview by remember { mutableStateOf(false) }
    var reloadGeneration by remember { mutableIntStateOf(0) }
    val reviewCache = remember(mediaId) { mutableStateMapOf<Pair<BangumiReviewType, Int>, CachedBangumiReviews>() }
    val visualWorkActive = com.android.purebilibili.core.ui.rememberVisualWorkActive()
    val reduceMotion = com.android.purebilibili.core.ui.motion.rememberSystemReduceMotion()

    fun reload() {
        reviewCache.remove(reviewType to sort)
        loading = true
        loadingMore = false
        errorMessage = null
        reloadGeneration++
    }

    fun selectQuery(type: BangumiReviewType, order: Int) {
        if (type == reviewType && order == sort) return
        val sortChanged = type == reviewType && order != sort
        if (!loading && errorMessage == null && busyReviews.isEmpty()) {
            reviewCache[reviewType to sort] = CachedBangumiReviews(items, nextCursor, hasMore)
        } else if (busyReviews.isNotEmpty()) {
            reviewCache.remove(reviewType to sort)
        }
        val cached = reviewCache[type to order]
        items = cached?.items.orEmpty()
        nextCursor = cached?.nextCursor.orEmpty()
        hasMore = cached?.hasMore ?: false
        loading = cached == null
        loadingMore = false
        errorMessage = null
        reviewType = type
        sort = order
        if (sortChanged) scope.launch { listState.scrollToItem(0) }
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { if (pagerState.isScrollInProgress) null else pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page -> page?.let { reviewTypes.getOrNull(it)?.let { type -> selectQuery(type, sort) } } }
    }

    LaunchedEffect(mediaId, reviewType, sort, reloadGeneration) {
        if (!loading) return@LaunchedEffect
        val result = BangumiReviewRepository.getReviews(mediaId = mediaId, type = reviewType, sort = sort)
        currentCoroutineContext().ensureActive()
        result.fold(
            onSuccess = { page ->
                items = page.items
                nextCursor = page.next
                hasMore = page.hasMore
                reviewCache[reviewType to sort] = CachedBangumiReviews(items, nextCursor, hasMore)
            },
            onFailure = { error ->
                if (items.isEmpty()) errorMessage = error.message ?: "点评加载失败"
                else Toast.makeText(context, error.message ?: "点评刷新失败", Toast.LENGTH_SHORT).show()
            },
        )
        loading = false
    }

    // Prepare the adjacent page after the visible page has loaded; cancelled swipes never
    // replace the selected page, and the cached result is keyed by type and sort.
    LaunchedEffect(mediaId, reviewType, sort, loading) {
        if (loading) return@LaunchedEffect
        val other = reviewTypes.firstOrNull { it != reviewType } ?: return@LaunchedEffect
        val query = other to sort
        if (reviewCache.containsKey(query)) return@LaunchedEffect
        val result = BangumiReviewRepository.getReviews(mediaId = mediaId, type = other, sort = sort)
        currentCoroutineContext().ensureActive()
        result.onSuccess { page ->
            if (!reviewCache.containsKey(query)) reviewCache[query] = CachedBangumiReviews(page.items, page.next, page.hasMore)
        }
    }

    LaunchedEffect(mediaId, reviewType, sort, listState, hasMore, nextCursor, loading) {
        snapshotFlow {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            Triple(lastVisible, listState.layoutInfo.totalItemsCount, pagerState.isScrollInProgress)
        }.distinctUntilChanged().collect { (lastVisible, total, switchingPage) ->
            if (switchingPage || !hasMore || loading || loadingMore || nextCursor.isBlank()) return@collect
            if (total > 0 && lastVisible >= total - 3) {
                loadingMore = true
                val result = BangumiReviewRepository.getReviews(
                    mediaId = mediaId,
                    type = reviewType,
                    cursor = nextCursor,
                    sort = sort
                )
                currentCoroutineContext().ensureActive()
                result.onSuccess { page ->
                    items = items + page.items
                    nextCursor = page.next
                    hasMore = page.hasMore
                    reviewCache[reviewType to sort] = CachedBangumiReviews(items, nextCursor, hasMore)
                }
                loadingMore = false
            }
        }
    }

    AppScaffold(
        blurContentReady = !loading,
        topBar = {
            AppTopBar(
                title = title.ifBlank { "点评" },
                navigationIcon = {
                    AppIconButton(onClick = onBack) {
                        AppIcon(rememberAppBackIcon(), contentDescription = "返回")
                    }
                },
                actions = {
                    if (reviewType == BangumiReviewType.SHORT) {
                        AppTextButton(onClick = { editingReview = null; showComposer = true }) {
                            AppText("写短评")
                        }
                    } else {
                        AppTextButton(
                            onClick = {
                                onOpenWeb(
                                    "https://member.bilibili.com/article-text/mobile?theme=${if (darkTheme) 1 else 0}&media_id=$mediaId",
                                    "写长评"
                                )
                            }
                        ) {
                            AppText("写长评")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppSurfaceTokens.groupedListContainer(),
                    scrolledContainerColor = AppSurfaceTokens.groupedListContainer(),
                )
            )
        }
    ) { paddingValues ->
        val reviewChromeBackdrop = rememberLayerBackdrop()
        val sortLabels = remember { listOf("默认", "最新") }
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(reviewChromeBackdrop)
                    .background(MaterialTheme.colorScheme.background),
            )
            Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = AppSpacingTokens.Medium,
                        top = paddingValues.calculateTopPadding() + AppSpacingTokens.Small,
                        end = AppSpacingTokens.Medium,
                        bottom = AppSpacingTokens.Small,
                    ),
                horizontalArrangement = Arrangement.spacedBy(AppSpacingTokens.Small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BottomBarLiquidSegmentedControl(
                    // Glass indicator fills the measured slot; native geometry keeps its defaults.
                    liquidGlassAdaptIndicatorToLabel = !com.android.purebilibili.core.ui.LocalAppThemeConfig.current.liquidGlassEnabled,
                    items = reviewTypes.map { it.label },
                    selectedIndex = pagerState.tabSelectedPage,
                    onSelected = { index ->
                        if (index in reviewTypes.indices) scope.launch {
                            if (reduceMotion) pagerState.scrollToPage(index) else animatePagerSelection(pagerState, index)
                        }
                    },
                    indicatorPositionProvider = { pagerState.tabIndicatorPosition },
                    isScrollInProgressProvider = { pagerState.isScrollInProgress },
                    // Miuix uses a custom pager gesture; its scroll state supplies the
                    // fallback drag signal instead of Foundation's interaction source.
                    isPagerDraggingProvider = if (nativePagerGestures) ({ pagerDragHeld }) else null,
                    pagerTargetPositionProvider = { pagerState.targetPage.toFloat() },
                    externalPagerMotionEffectsEnabled = true,
                    itemWidth = if (com.android.purebilibili.core.ui.LocalAppThemeConfig.current.liquidGlassEnabled) null else 66.dp,
                    height = AppChromeSizeTokens.BottomBarMatchedSegmentedControlHeightDp.dp,
                    indicatorHeight = AppChromeSizeTokens.BottomBarMatchedSegmentedIndicatorHeightDp.dp,
                    labelFontSize = MaterialTheme.typography.labelMedium.fontSize,
                    miuixBackdrop = reviewChromeBackdrop,
                    liquidGlassEffectsEnabled = true,
                    dragSelectionEnabled = reviewTypes.size > 1,
                    tapPressRefractionEnabled = true,
                )
                Spacer(modifier = Modifier.weight(1f))
                BottomBarLiquidSegmentedControl(
                    // Glass indicator fills the measured slot; native geometry keeps its defaults.
                    liquidGlassAdaptIndicatorToLabel = !com.android.purebilibili.core.ui.LocalAppThemeConfig.current.liquidGlassEnabled,
                    items = sortLabels,
                    selectedIndex = sort.coerceIn(0, sortLabels.lastIndex),
                    onSelected = { index -> selectQuery(reviewType, index) },
                    itemWidth = if (com.android.purebilibili.core.ui.LocalAppThemeConfig.current.liquidGlassEnabled) null else 66.dp,
                    height = AppChromeSizeTokens.BottomBarMatchedSegmentedControlHeightDp.dp,
                    indicatorHeight = AppChromeSizeTokens.BottomBarMatchedSegmentedIndicatorHeightDp.dp,
                    labelFontSize = MaterialTheme.typography.labelMedium.fontSize,
                    miuixBackdrop = reviewChromeBackdrop,
                    liquidGlassEffectsEnabled = true,
                    dragSelectionEnabled = sortLabels.size > 1,
                    tapPressRefractionEnabled = true,
                )
            }

            AppHorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                beyondViewportPageCount = 1,
                key = { reviewTypes[it].name },
            ) { page ->
            val pageType = reviewTypes[page]
            if (pageType != reviewType) {
                val cached = reviewCache[pageType to sort]
                BangumiReviewTransitionPage(
                    reviewType = pageType,
                    items = cached?.items,
                    listState = if (pageType == BangumiReviewType.SHORT) shortListState else longListState,
                )
            } else when {
                loading && items.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (visualWorkActive && !reduceMotion && !pagerState.isScrollInProgress && pagerState.settledPage == page) {
                            com.android.purebilibili.core.ui.CutePersonLoadingIndicator()
                        } else {
                            AppText("加载中…", color = AppContentColors.secondaryText())
                        }
                    }
                }
                errorMessage != null && items.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            AppText(errorMessage.orEmpty())
                            Spacer(modifier = Modifier.height(AppSpacingTokens.Medium))
                            AppButton(onClick = { reload() }) { AppText("重试") }
                        }
                    }
                }
                items.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        AppText("还没有点评")
                    }
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize(),
                        contentPadding = PaddingValues(
                            horizontal = AppSpacingTokens.Medium,
                            vertical = AppSpacingTokens.Small
                        )
                    ) {
                        items(items, key = { it.review_id.takeIf { id -> id > 0L } ?: it.article_id }, contentType = { reviewType }) { item ->
                          Column {
                            AppListItem(
                                headlineContent = {
                                    AppText(
                                        item.author?.uname.orEmpty().ifBlank { "用户" },
                                        fontWeight = FontWeight.Medium
                                    )
                                },
                                supportingContent = {
                                    Column(verticalArrangement = Arrangement.spacedBy(AppSpacingTokens.ExtraSmall)) {
                                        if (item.title.isNotBlank()) {
                                            AppText(item.title, fontWeight = FontWeight.SemiBold)
                                        }
                                        if (item.content.isNotBlank()) {
                                            AppText(
                                                item.content,
                                                maxLines = if (reviewType == BangumiReviewType.LONG) 5 else Int.MAX_VALUE,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        AppText(
                                            listOfNotNull(
                                                item.push_time_str.takeIf { it.isNotBlank() },
                                                (item.score / 2).takeIf { it > 0 }?.let { "$it 分" }
                                            ).joinToString(" · "),
                                            color = AppContentColors.secondaryText()
                                        )
                                    }
                                },
                            )
                            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                if (reviewType == BangumiReviewType.LONG && item.article_id > 0L) {
                                    AppTextButton(onClick = { onOpenArticle(item.article_id, item.title) }) { AppText("查看长评") }
                                } else if (reviewType == BangumiReviewType.SHORT && item.review_id > 0L) {
                                    AppIconButton(enabled = item.review_id !in busyReviews, onClick = {
                                        val requestQuery = reviewType to sort
                                        busyReviews = busyReviews + item.review_id
                                        scope.launch {
                                            BangumiReviewRepository.dislikeReview(mediaId, item.review_id)
                                                .onSuccess {
                                                    if (requestQuery != (reviewType to sort)) {
                                                        reviewCache.remove(requestQuery)
                                                        return@onSuccess
                                                    }
                                                    items = items.map { current ->
                                                        if (current.review_id != item.review_id) current else {
                                                            val stat = current.stat ?: com.android.purebilibili.data.model.response.BangumiReviewStat()
                                                            val disliked = stat.disliked == 1
                                                            current.copy(stat = stat.copy(
                                                                disliked = if (disliked) 0 else 1,
                                                                liked = if (disliked) stat.liked else 0,
                                                                likes = (stat.likes - if (!disliked && stat.liked == 1) 1 else 0).coerceAtLeast(0),
                                                            ))
                                                        }
                                                    }
                                                }.onFailure { Toast.makeText(context, it.message ?: "操作失败", Toast.LENGTH_SHORT).show() }
                                            busyReviews = busyReviews - item.review_id
                                        }
                                    }) {
                                        AppIcon(if (item.stat?.disliked == 1) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown, contentDescription = if (item.stat?.disliked == 1) "取消点踩" else "点踩", tint = if (item.stat?.disliked == 1) AppContentColors.accent() else AppContentColors.secondaryText(), modifier = Modifier.size(20.dp))
                                    }
                                    AppTextButton(
                                        onClick = {
                                            if (item.review_id <= 0L || item.review_id in busyReviews) return@AppTextButton
                                            val requestQuery = reviewType to sort
                                            busyReviews = busyReviews + item.review_id
                                            scope.launch {
                                                BangumiReviewRepository.likeReview(mediaId, item.review_id)
                                                    .onSuccess {
                                                        if (requestQuery != (reviewType to sort)) {
                                                            reviewCache.remove(requestQuery)
                                                            return@onSuccess
                                                        }
                                                        items = items.map { current ->
                                                            if (current.review_id != item.review_id) {
                                                                current
                                                            } else {
                                                                val stat = current.stat ?: com.android.purebilibili.data.model.response.BangumiReviewStat()
                                                                val liked = stat.liked == 1
                                                                current.copy(
                                                                    stat = stat.copy(
                                                                        liked = if (liked) 0 else 1,
                                                                        disliked = if (liked) stat.disliked else 0,
                                                                        likes = (stat.likes + if (liked) -1 else 1)
                                                                            .coerceAtLeast(0)
                                                                    )
                                                                )
                                                            }
                                                        }
                                                    }
                                                    .onFailure { error ->
                                                        Toast.makeText(
                                                            context,
                                                            error.message ?: "点赞失败",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    }
                                                busyReviews = busyReviews - item.review_id
                                            }
                                        },
                                        enabled = item.review_id > 0 && item.review_id !in busyReviews,
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpacingTokens.ExtraSmall)) {
                                            AppIcon(if (item.stat?.liked == 1) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp, contentDescription = if (item.stat?.liked == 1) "取消点赞" else "点赞", tint = if (item.stat?.liked == 1) AppContentColors.accent() else AppContentColors.secondaryText(), modifier = Modifier.size(20.dp))
                                            item.stat?.let { AppText(it.likes.toString(), color = if (it.liked == 1) AppContentColors.accent() else AppContentColors.secondaryText()) }
                                        }
                                    }
                                    if (item.author?.mid?.let { it > 0L && it == TokenManager.midCache } == true) {
                                        AppTextButton(onClick = { editingReview = item; showComposer = true }, enabled = item.review_id !in busyReviews) { AppText("编辑") }
                                        AppTextButton(onClick = { deletingReview = item }, enabled = item.review_id !in busyReviews) { AppText("删除") }
                                    }
                                    AppTextButton(onClick = { onOpenWeb("https://www.bilibili.com/appeal/?reviewId=${item.review_id}&type=shortComment&mediaId=$mediaId", "举报短评") }) { AppText("举报") }
                                }
                            }
                          }
                        }
                        if (loadingMore) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(AppSpacingTokens.Medium),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AppText("正在加载更多…")
                                }
                            }
                        }
                    }
                }
            }
            }
            }
        }
    }

    if (showComposer) {
        BangumiShortReviewComposer(
            initialReview = editingReview,
            submitting = submittingReview,
            onDismiss = { if (!submittingReview) { showComposer = false; editingReview = null } },
            onSubmit = { score, content, shareFeed ->
                val editing = editingReview
                submittingReview = true
                scope.launch {
                    val result = if (editing == null) BangumiReviewRepository.postShortReview(mediaId, score, content, shareFeed)
                        else BangumiReviewRepository.modifyShortReview(mediaId, editing.review_id, score, content)
                    result.fold(
                        onSuccess = {
                            Toast.makeText(context, if (editing == null) "已发布短评" else "已更新短评", Toast.LENGTH_SHORT).show()
                            showComposer = false
                            editingReview = null
                            reload()
                        },
                        onFailure = { error ->
                            Toast.makeText(context, error.message ?: "操作失败", Toast.LENGTH_SHORT).show()
                        }
                    )
                    submittingReview = false
                }
            }
        )
    }
    deletingReview?.let { review ->
        AppAlertDialog(
            onDismissRequest = { deletingReview = null },
            title = { AppText("删除短评") },
            text = { AppText("删除短评时会同时删除评分。") },
            confirmButton = {
                AppDialogAction(onClick = {
                    deletingReview = null
                    busyReviews = busyReviews + review.review_id
                    scope.launch {
                        BangumiReviewRepository.deleteShortReview(mediaId, review.review_id)
                            .onSuccess { items = items.filterNot { it.review_id == review.review_id } }
                            .onFailure { Toast.makeText(context, it.message ?: "删除失败", Toast.LENGTH_SHORT).show() }
                        busyReviews = busyReviews - review.review_id
                    }
                }) { AppText("删除") }
            },
            dismissButton = { AppDialogAction(onClick = { deletingReview = null }) { AppText("取消") } },
        )
    }
}

/** Sliding neighbor uses the cached list without starting background loading animations. */
@Composable
private fun BangumiReviewTransitionPage(
    reviewType: BangumiReviewType,
    items: List<BangumiReviewItem>?,
    listState: LazyListState,
) {
    when {
        items == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            AppText("加载中…", color = AppContentColors.secondaryText())
        }
        items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            AppText("还没有点评")
        }
        else -> LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = AppSpacingTokens.Medium, vertical = AppSpacingTokens.Small),
        ) {
            items(items, key = { it.review_id.takeIf { id -> id > 0L } ?: it.article_id }, contentType = { reviewType }) { item ->
                Column {
                    AppListItem(
                        headlineContent = { AppText(item.author?.uname.orEmpty().ifBlank { "用户" }, fontWeight = FontWeight.Medium) },
                        supportingContent = {
                            Column(verticalArrangement = Arrangement.spacedBy(AppSpacingTokens.ExtraSmall)) {
                                if (item.title.isNotBlank()) AppText(item.title, fontWeight = FontWeight.SemiBold)
                                if (item.content.isNotBlank()) AppText(item.content,
                                    maxLines = if (reviewType == BangumiReviewType.LONG) 5 else Int.MAX_VALUE,
                                    overflow = TextOverflow.Ellipsis)
                                AppText(listOfNotNull(item.push_time_str.takeIf { it.isNotBlank() },
                                    (item.score / 2).takeIf { it > 0 }?.let { "$it 分" }).joinToString(" · "),
                                    color = AppContentColors.secondaryText())
                            }
                        },
                    )
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        if (reviewType == BangumiReviewType.LONG && item.article_id > 0L) {
                            AppTextButton(onClick = {}) { AppText("查看长评") }
                        } else if (reviewType == BangumiReviewType.SHORT && item.review_id > 0L) {
                            AppIconButton(onClick = {}) {
                                AppIcon(if (item.stat?.disliked == 1) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown, contentDescription = null,
                                    tint = if (item.stat?.disliked == 1) AppContentColors.accent() else AppContentColors.secondaryText(), modifier = Modifier.size(20.dp))
                            }
                            AppTextButton(onClick = {}) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpacingTokens.ExtraSmall)) {
                                    AppIcon(if (item.stat?.liked == 1) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp, contentDescription = null,
                                        tint = if (item.stat?.liked == 1) AppContentColors.accent() else AppContentColors.secondaryText(), modifier = Modifier.size(20.dp))
                                    item.stat?.let { AppText(it.likes.toString(), color = if (it.liked == 1) AppContentColors.accent() else AppContentColors.secondaryText()) }
                                }
                            }
                            if (item.author?.mid?.let { it > 0L && it == TokenManager.midCache } == true) {
                                AppTextButton(onClick = {}) { AppText("编辑") }
                                AppTextButton(onClick = {}) { AppText("删除") }
                            }
                            AppTextButton(onClick = {}) { AppText("举报") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BangumiShortReviewComposer(
    initialReview: BangumiReviewItem?,
    submitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (Int, String, Boolean) -> Unit
) {
    var content by remember(initialReview) { mutableStateOf(initialReview?.content.orEmpty()) }
    var score by remember(initialReview) { mutableIntStateOf(initialReview?.score?.coerceIn(2, 10) ?: 8) }
    var shareFeed by remember { mutableStateOf(false) }
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { AppText(if (initialReview == null) "写短评" else "编辑短评") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacingTokens.Small)) {
                AppText("评分 ${score / 2} 分")
                AppSlider(
                    value = score.toFloat(),
                    onValueChange = { score = (kotlin.math.round(it / 2f).toInt() * 2).coerceIn(2, 10) },
                    valueRange = 2f..10f,
                    steps = 3
                )
                AppTextField(
                    value = content,
                    onValueChange = { content = it.take(100) },
                    placeholder = "写下你的观后感",
                    singleLine = false,
                    minLines = 3
                )
                AppText("${content.length}/100", style = MaterialTheme.typography.bodySmall)
                if (initialReview == null) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        AppText("分享到动态")
                        AppSwitch(checked = shareFeed, onCheckedChange = { shareFeed = it })
                    }
                }
            }
        },
        confirmButton = {
            AppDialogAction(
                onClick = {
                    if (submitting || content.isBlank()) return@AppDialogAction
                    onSubmit(score, content, shareFeed)
                }
            ) {
                AppText(if (initialReview == null) "发布" else "保存")
            }
        },
        dismissButton = {
            AppDialogAction(onClick = onDismiss) { AppText("取消") }
        }
    )
}
