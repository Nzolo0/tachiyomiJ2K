package eu.kanade.tachiyomi.ui.setting.database

import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.ui.base.presenter.BaseCoroutinePresenter
import eu.kanade.tachiyomi.ui.source.browse.BrowseSourceItem
import eu.kanade.tachiyomi.util.system.launchIO
import eu.kanade.tachiyomi.util.system.withUIContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Presenter of [BrowseClearSourceController].
 */
open class BrowseClearSourcePresenter(
    private val sourceId: Long,
    val sourceManager: SourceManager = Injekt.get(),
    val db: DatabaseHelper = Injekt.get(),
    val prefs: PreferencesHelper = Injekt.get(),
    private val coverCache: CoverCache = Injekt.get(),
) : BaseCoroutinePresenter<BrowseClearSourceController>() {

    /**
     * Selected source.
     */
    val source = sourceManager.getOrStub(sourceId)

    fun initializeItems() {
        val browseAsList = prefs.browseAsList()
        val sourceListType = prefs.libraryLayout()
        val outlineCovers = prefs.outlineOnCovers()

        presenterScope.launchIO {
            val mangas = getReadMangaPerSource(sourceId)
            val items = mangas.map { BrowseSourceItem(it, browseAsList, sourceListType, outlineCovers) }
            withUIContext { view?.updateAdapter(items) }
        }
    }

    private fun getReadMangaPerSource(sourceId: Long): MutableList<Manga> {
        return db.getReadNotInLibraryMangasPerSource(sourceId).executeAsBlocking()
    }

    fun deleteManga(manga: Manga) {
        coverCache.deleteFromCache(manga)
        db.deleteManga(manga).executeAsBlocking()
    }

    fun confirmDeletion(manga: Manga) {
        launchIO {
            val downloadManager: DownloadManager = Injekt.get()
            downloadManager.deleteManga(manga, source)
        }
    }
}
