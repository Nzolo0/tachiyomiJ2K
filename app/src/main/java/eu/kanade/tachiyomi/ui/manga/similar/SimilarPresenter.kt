package eu.kanade.tachiyomi.ui.manga.similar

import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.ui.base.presenter.BaseCoroutinePresenter
import eu.kanade.tachiyomi.util.system.launchIO
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Presenter of [SimilarController]
 */
class SimilarPresenter(
    private val manga: Manga?,
    private val repo: SimilarRepository = Injekt.get(),
    val db: DatabaseHelper = Injekt.get(),
    val preferences: PreferencesHelper = Injekt.get(),
    private val coverCache: CoverCache = Injekt.get(),
) : BaseCoroutinePresenter<SimilarController>() {

    private val _similarScreenState = MutableStateFlow(
        SimilarScreenState(
            isList = preferences.browseAsList().get(),
            outlineCovers = preferences.outlineOnCovers().get(),
            isComfortableGrid = preferences.libraryLayout().get() == 2,
            rawColumnCount = preferences.gridSize().get(),
            promptForCategories = preferences.defaultCategory() == -1,
        ),
    )

    val similarScreenState: StateFlow<SimilarScreenState> = _similarScreenState.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        getSimilarManga()

        presenterScope.launch {
            if (similarScreenState.value.promptForCategories) {
                val categories = db.getCategories().executeAsBlocking()
                _similarScreenState.update {
                    it.copy(
                        categories = categories.toImmutableList(),
                    )
                }
            }
        }
        presenterScope.launch {
            preferences.browseAsList().asFlow().collectLatest {
                _similarScreenState.update { state ->
                    state.copy(isList = it)
                }
            }
        }
    }

    fun refresh() {
        getSimilarManga(true)
    }

    private fun getSimilarManga(forceRefresh: Boolean = false) {
        presenterScope.launch {
            if (manga != null) {
                _similarScreenState.update {
                    it.copy(isRefreshing = true, displayManga = persistentMapOf())
                }

                val list = repo.fetchSimilar(manga, forceRefresh)
                _similarScreenState.update { sim ->
                    sim.copy(
                        isRefreshing = false,
                        displayManga = list.associate { it.type to it.manga.toImmutableList() }.toImmutableMap(),
                    )
                }
            }
        }
    }

    fun updateDisplayManga(mangaId: Long, favorite: Boolean) {
        presenterScope.launch {
            val listOfKeyIndex = _similarScreenState.value.displayManga.mapNotNull { entry ->
                val tempListIndex = entry.value.indexOfFirst { it.mangaId == mangaId }
                when (tempListIndex == -1) {
                    true -> null
                    false -> entry.key to tempListIndex
                }
            }

            val tempMap = _similarScreenState.value.displayManga.toMutableMap()

            listOfKeyIndex.forEach { pair ->
                val mapKey = pair.first
                val mangaIndex = pair.second
                val tempList = _similarScreenState.value.displayManga[mapKey]!!.toMutableList()
                val tempDisplayManga = tempList[mangaIndex].copy(inLibrary = favorite)
                tempList[mangaIndex] = tempDisplayManga

                tempMap[mapKey] = tempList.toImmutableList()
            }

            _similarScreenState.update {
                it.copy(
                    displayManga = tempMap.toImmutableMap(),
                )
            }
        }
    }

    fun switchDisplayMode() {
        preferences.browseAsList().set(!similarScreenState.value.isList)
    }

    fun confirmDeletion(manga: Manga) {
        launchIO {
            coverCache.deleteFromCache(manga)
            val downloadManager: DownloadManager = Injekt.get()
            val source = repo.sourceManager.getOrStub(manga.source)
            downloadManager.deleteManga(manga, source)
        }
    }
}
