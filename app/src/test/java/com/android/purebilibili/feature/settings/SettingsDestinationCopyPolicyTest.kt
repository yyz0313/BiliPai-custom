package com.android.purebilibili.feature.settings

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsDestinationCopyPolicyTest {

    private fun readSource(relativePath: String): String {
        return listOf(
            File("app/src/main/java/com/android/purebilibili/$relativePath"),
            File("src/main/java/com/android/purebilibili/$relativePath")
        ).first { it.exists() }.readText().replace("\r\n", "\n")
    }

    @Test
    fun destinationTitles_areUniqueAcrossTargets() {
        val titles = SettingsSearchTarget.entries
            .map { settingsDestinationCopy(it).title }
        assertEquals(titles.size, titles.distinct().size)
    }

    @Test
    fun destinationCopy_neverBlank() {
        SettingsSearchTarget.entries.forEach { target ->
            val copy = settingsDestinationCopy(target)
            assertTrue(copy.title.isNotBlank(), "blank title for $target")
            assertTrue(copy.summary.isNotBlank(), "blank summary for $target")
        }
    }

    @Test
    fun knownDestinations_keepUnifiedTitles() {
        assertEquals("外观设置", settingsDestinationCopy(SettingsSearchTarget.APPEARANCE).title)
        assertEquals("播放器与画质", settingsDestinationCopy(SettingsSearchTarget.PLAYBACK).title)
        assertEquals("导航设置", settingsDestinationCopy(SettingsSearchTarget.BOTTOM_BAR).title)
        assertEquals("WebDAV 云备份", settingsDestinationCopy(SettingsSearchTarget.WEBDAV_BACKUP).title)
        assertEquals("首页与推荐", settingsDestinationCopy(SettingsSearchTarget.HOME_FEED).title)
        assertEquals("互动与评论", settingsDestinationCopy(SettingsSearchTarget.INTERACTION_COMMENT).title)
        assertEquals("动画与触感", settingsDestinationCopy(SettingsSearchTarget.ANIMATION).title)
    }

    @Test
    fun rowsAndSearchIndex_renderTitlesFromPolicyInsteadOfDriftedLiterals() {
        val sections = readSource("feature/settings/ui/SettingsSections.kt")
        val search = readSource("feature/settings/SettingsSearchPolicy.kt")

        // 漂移过的旧标题不应再以字面量出现在行/索引里
        assertFalse(sections.contains("title = \"播放器设置\""))
        assertFalse(sections.contains("title = \"首页样式与壁纸\""))
        assertFalse(sections.contains("title = \"动效与图标\""))
        assertFalse(sections.contains("title = \"WebDAV 备份\""))
        assertFalse(search.contains("\"WebDAV 备份\""))
        assertFalse(search.contains("section = \"外观设置\","))
        assertFalse(search.contains("section = \"播放设置\","))
        assertFalse(search.contains("section = \"导航设置\","))

        // 行与索引统一引用 policy
        assertTrue(sections.contains("settingsDestinationCopy(SettingsSearchTarget.APPEARANCE).title"))
        assertTrue(search.contains("settingsDestinationCopy(SettingsSearchTarget.APPEARANCE).title"))
        assertTrue(search.contains("settingsDestinationCopy(SettingsSearchTarget.BOTTOM_BAR).title"))
    }
}
