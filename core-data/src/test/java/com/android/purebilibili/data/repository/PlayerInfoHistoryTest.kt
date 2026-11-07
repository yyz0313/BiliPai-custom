package com.android.purebilibili.data.repository

import com.android.purebilibili.data.model.response.PlayerInfoData
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlayerInfoHistoryTest {
    @Test
    fun playerInfoRetainsTheServerHistoryPart() {
        val data = Json.decodeFromString<PlayerInfoData>("""{"cid":101,"last_play_cid":202}""")
        assertEquals(202L, data.lastPlayCid)
    }

    @Test
    fun omittedHistoryIsDistinctFromNoPreviousPart() {
        assertNull(Json.decodeFromString<PlayerInfoData>("""{"cid":101}""").lastPlayCid)
        assertEquals(0L, Json.decodeFromString<PlayerInfoData>("""{"cid":101,"last_play_cid":0}""").lastPlayCid)
    }
}
