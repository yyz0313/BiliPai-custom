package com.android.purebilibili.data.repository

import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.core.network.WbiKeyManager
import com.android.purebilibili.core.network.WbiUtils
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.data.model.response.*

/** Network results only. Each frontend owns navigation and success feedback. */
object UserContentRepository {
    suspend fun followingVideos(offset: String = ""): Result<Pair<List<VideoItem>, DynamicFeedData>> = dataRequest {
        val response = DynamicFeedPageDataSource.fetch { NetworkModule.dynamicApi.getDynamicFeed(type = "video", offset = offset) }.getOrThrow()
        if (response.code != 0) throw ContentRequestException(response.code, response.message)
        val data = response.data ?: error("关注视频数据为空")
        data.items.mapNotNull { item ->
            val archive = item.modules.module_dynamic?.major?.archive ?: return@mapNotNull null
            if (archive.bvid.isBlank() || archive.epid > 0 || archive.season_id > 0) return@mapNotNull null
            val author = item.modules.module_author
            VideoItem(bvid = archive.bvid, aid = archive.aid.toLongOrNull() ?: 0,
                dynamicId = item.id_str, title = archive.title, pic = archive.cover,
                owner = Owner(mid = author?.mid ?: 0, name = author?.name.orEmpty(), face = author?.face.orEmpty()),
                duration = durationSeconds(archive.duration_text), pubdate = author?.pub_ts ?: 0, isFollowed = true)
        } to data
    }

    suspend fun followings(page: Int): Result<FollowingsData> = dataRequest {
        val response = NetworkModule.api.getFollowings(TokenManager.midCache ?: throw ContentRequestException(-101, ""), pn = page)
        if (response.code != 0) throw ContentRequestException(response.code, response.message)
        response.data ?: FollowingsData()
    }

    suspend fun space(mid: Long): Result<SpaceUserInfo> = dataRequest {
        val keys = WbiKeyManager.getWbiKeys().getOrThrow()
        val response = NetworkModule.spaceApi.getSpaceInfo(WbiUtils.sign(mapOf("mid" to mid.toString()), keys.first, keys.second))
        if (response.code != 0) throw ContentRequestException(response.code, response.message)
        response.data ?: error("UP 主资料为空")
    }

    suspend fun videos(mid: Long, page: Int, pageSize: Int = 30, order: String = "pubdate", tid: Int = 0,
        keyword: String = "", imgKey: String? = null, subKey: String? = null): Result<SpaceVideoData> = dataRequest {
        val keys = if (imgKey != null && subKey != null) imgKey to subKey else WbiKeyManager.getWbiKeys().getOrThrow()
        val params = buildMap {
            put("mid", mid.toString()); put("pn", page.toString()); put("ps", pageSize.toString()); put("order", order)
            if (tid > 0) put("tid", tid.toString())
            if (keyword.isNotBlank()) put("keyword", keyword)
        }
        val response = NetworkModule.spaceApi.getSpaceVideos(WbiUtils.sign(params, keys.first, keys.second))
        if (response.code != 0) throw ContentRequestException(response.code, response.message)
        response.data ?: error("UP 主投稿数据为空")
    }
}

fun durationSeconds(text: String): Int = text.split(':').fold(0) { total, part -> total * 60 + (part.toIntOrNull() ?: 0) }

fun SpaceVideoItem.asVideoItem(mid: Long): VideoItem = VideoItem(aid = aid, bvid = bvid, title = title, pic = pic,
    cooperation = isCooperation, isUnionVideo = isUnionVideo,
    isSteins = isSteins, isLessonVideo = isLessonVideo,
    isChargingArc = isChargingArc || elecArcType == 1 || isUgcpay || ugcPay > 0 || ugcPayPreview > 0,
    badges = badges.map { VideoContentBadge(it.text) },
    owner = Owner(mid = mid, name = author), duration = durationSeconds(length), pubdate = created,
    stat = Stat(view = play, reply = comment))
