package com.android.purebilibili.feature.story

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.android.purebilibili.data.model.response.StoryItem
import com.android.purebilibili.data.repository.StoryRepository
import com.android.purebilibili.data.repository.VideoRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StoryUiState(
    val items: List<StoryItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val currentIndex: Int = 0
)

/** Owns the Story queue and its cursor; the pager only requests more near the visible tail. */
class StoryViewModel(application: Application) : AndroidViewModel(application) {
    private enum class FeedSource { STORY, HOME }

    private val _uiState = MutableStateFlow(StoryUiState(isLoading = true))
    val uiState: StateFlow<StoryUiState> = _uiState.asStateFlow()
    private var source = FeedSource.STORY
    private var seedBvid = ""
    private var started = false
    private var nextDisplayId = 1
    private var nextHomePage = 0
    private var requestGeneration = 0
    private var loadJob: Job? = null

    fun startFeed(bvid: String = "") {
        val normalized = bvid.trim()
        if (started && seedBvid == normalized) return
        seedBvid = normalized
        started = true
        refresh()
    }

    fun refresh() {
        requestGeneration += 1
        loadJob?.cancel()
        source = FeedSource.STORY
        nextDisplayId = 1
        nextHomePage = 0
        _uiState.value = StoryUiState()
        loadMoreStories()
    }

    fun loadMoreStories() {
        if (_uiState.value.isLoading) return
        val generation = requestGeneration
        _uiState.update { it.copy(isLoading = true, error = null) }
        loadJob = viewModelScope.launch {
            var result = if (source == FeedSource.STORY) {
                StoryRepository.getStoryFeed(bvid = seedBvid, displayId = nextDisplayId)
                    .map { items -> items.filter { storyItemToRelatedVideo(it) != null } }
            } else {
                VideoRepository.getHomeVideos(idx = nextHomePage).map(::videoItemsToStoryItems)
            }
            currentCoroutineContext().ensureActive()
            if (generation != requestGeneration) return@launch

            // Keep a single queue and cursor owner when the client Story endpoint is unavailable.
            if (source == FeedSource.STORY && result.getOrNull().isNullOrEmpty()) {
                source = FeedSource.HOME
                result = VideoRepository.getHomeVideos(idx = nextHomePage).map(::videoItemsToStoryItems)
                currentCoroutineContext().ensureActive()
                if (generation != requestGeneration) return@launch
            }
            result.fold(
                onSuccess = { items ->
                    if (source == FeedSource.STORY) nextDisplayId += 1 else nextHomePage += 1
                    _uiState.update { state ->
                        val merged = mergeStoryFeedItems(state.items, items)
                        state.copy(
                            items = merged,
                            isLoading = false,
                            error = if (merged.size == state.items.size) "暂时没有新的推荐视频" else null
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update { it.copy(isLoading = false, error = error.message ?: "推荐加载失败") }
                }
            )
        }
    }

    fun updateCurrentIndex(index: Int) {
        _uiState.update { it.copy(currentIndex = index) }
    }
}
