package eu.kanade.domain.download.service

import eu.kanade.tachiyomi.core.preference.PreferenceStore
import eu.kanade.tachiyomi.core.provider.FolderProvider
import eu.kanade.tachiyomi.data.preference.DEVICE_ONLY_ON_WIFI

class DownloadPreferences(
    private val folderProvider: FolderProvider,
    private val preferenceStore: PreferenceStore,
) {

    fun downloadsDirectory() = preferenceStore.getString("download_directory", folderProvider.path())

    fun downloadOnlyOverWifi() = preferenceStore.getBoolean("pref_download_only_over_wifi_key", true)

    fun saveChaptersAsCBZ() = preferenceStore.getBoolean("save_chapter_as_cbz", true)

    fun splitTallImages() = preferenceStore.getBoolean("split_tall_images", false)

    fun autoDownloadWhileReading() = preferenceStore.getInt("auto_download_while_reading", 0)

    fun removeAfterReadSlots() = preferenceStore.getInt("remove_after_read_slots", -1)

    fun removeAfterMarkedAsRead() = preferenceStore.getBoolean("pref_remove_after_marked_as_read_key", false)

    fun removeBookmarkedChapters() = preferenceStore.getBoolean("pref_remove_bookmarked", false)

    fun removeExcludeCategories() = preferenceStore.getStringSet("remove_exclude_categories", emptySet())

    fun downloadNewChapters() = preferenceStore.getInt("download_new_chapters", 0)

    fun downloadNewChapterCategories() = preferenceStore.getStringSet("download_new_categories", emptySet())

    fun downloadNewChapterCategoriesExclude() = preferenceStore.getStringSet("download_new_categories_exclude", emptySet())

    fun downloadNewDeviceRestriction() = preferenceStore.getStringSet(
        "download_new_update_restriction",
        setOf(DEVICE_ONLY_ON_WIFI),
    )

    fun downloadNewSkipUnread() = preferenceStore.getBoolean("download_new_skip_unread", false)
}
