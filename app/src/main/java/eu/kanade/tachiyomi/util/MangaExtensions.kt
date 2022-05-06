package eu.kanade.tachiyomi.util

import android.content.Context
import eu.kanade.domain.chapter.model.Chapter
import eu.kanade.domain.download.service.DownloadPreferences
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.manga.model.hasCustomCover
import eu.kanade.domain.manga.model.isLocal
import eu.kanade.domain.manga.model.toDbManga
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.toDomainManga
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.chapter.getChapterSort
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.InputStream
import java.util.Date
import eu.kanade.domain.manga.model.Manga as DomainManga

/**
 * Call before updating [Manga.thumbnail_url] to ensure old cover can be cleared from cache
 */
fun DomainManga.prepUpdateCover(coverCache: CoverCache, remoteManga: SManga, refreshSameUrl: Boolean): DomainManga {
    // Never refresh covers if the new url is null, as the current url has possibly become invalid
    val newUrl = remoteManga.thumbnail_url ?: return this

    // Never refresh covers if the url is empty to avoid "losing" existing covers
    if (newUrl.isEmpty()) return this

    if (!refreshSameUrl && thumbnailUrl == newUrl) return this

    return when {
        isLocal() -> {
            this.copy(coverLastModified = Date().time)
        }
        hasCustomCover(coverCache) -> {
            coverCache.deleteFromCache(this, false)
            this
        }
        else -> {
            coverCache.deleteFromCache(this, false)
            this.copy(coverLastModified = Date().time)
        }
    }
}

fun Manga.removeCovers(coverCache: CoverCache = Injekt.get()): Int {
    if (toDomainManga()!!.isLocal()) return 0

    cover_last_modified = Date().time
    return coverCache.deleteFromCache(this, true)
}

fun DomainManga.removeCovers(coverCache: CoverCache = Injekt.get()): DomainManga {
    if (isLocal()) return this
    coverCache.deleteFromCache(this, true)
    return copy(coverLastModified = Date().time)
}

fun DomainManga.shouldDownloadNewChapters(dbCategories: List<Long>, preferences: DownloadPreferences): Boolean {
    if (!favorite) return false

    val categories = dbCategories.ifEmpty { listOf(0L) }

    // Boolean to determine if user wants to automatically download new chapters.
    val downloadNewChapters = preferences.downloadNewChapters().get()
    if (downloadNewChapters == 0) return false

    val includedCategories = preferences.downloadNewChapterCategories().get().map { it.toLong() }
    val excludedCategories = preferences.downloadNewChapterCategoriesExclude().get().map { it.toLong() }

    // Default: Download from all categories
    if (includedCategories.isEmpty() && excludedCategories.isEmpty()) return true

    // In excluded category
    if (categories.any { it in excludedCategories }) return false

    // Included category not selected
    if (includedCategories.isEmpty()) return true

    // In included category
    return categories.any { it in includedCategories }
}

suspend fun DomainManga.editCover(
    context: Context,
    stream: InputStream,
    updateManga: UpdateManga = Injekt.get(),
    coverCache: CoverCache = Injekt.get(),
) {
    if (isLocal()) {
        LocalSource.updateCover(context, toSManga(), stream)
        updateManga.awaitUpdateCoverLastModified(id)
    } else if (favorite) {
        coverCache.setCustomCoverToCache(toDbManga(), stream)
        updateManga.awaitUpdateCoverLastModified(id)
    }
}

/**
 * Filter the chapters to download among the new chapters of a manga
 */
fun DomainManga.getChaptersToDownload(
    newChapters: List<Chapter>,
    hasUnreadChapters: Boolean,
    downloadedUnreadChaptersCount: Int,
    downloadPreferences: DownloadPreferences,
): List<Chapter> {
    val skipWhenUnreadChapters = downloadPreferences.downloadNewSkipUnread().get()
    val downloadNewChaptersLimit = downloadPreferences.downloadNewChapters().get()

    if (skipWhenUnreadChapters && hasUnreadChapters) return emptyList()

    return if (downloadNewChaptersLimit != -1) {
        newChapters.sortedWith(getChapterSort(this, false))
            .take((downloadNewChaptersLimit - downloadedUnreadChaptersCount).coerceAtLeast(0))
    } else {
        newChapters
    }
}
