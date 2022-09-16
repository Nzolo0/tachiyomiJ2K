package eu.kanade.tachiyomi.ui.manga.similar

import androidx.annotation.StringRes
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.Manga.DisplayManga
import eu.kanade.tachiyomi.data.database.models.Manga.SourceManga
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.online.handlers.SimilarHandler
import eu.kanade.tachiyomi.util.toDisplayManga
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import timber.log.Timber
import uy.kohesive.injekt.injectLazy
import java.util.Locale

class SimilarRepository {

    private val similarHandler: SimilarHandler by injectLazy()
    private val db: DatabaseHelper by injectLazy()
    val sourceManager: SourceManager by injectLazy()

    suspend fun fetchSimilar(
        manga: Manga,
        forceRefresh: Boolean = false,
    ): List<SimilarMangaGroup> {
        return withContext(Dispatchers.IO) {
            val similarDbEntry = db.getSimilar(manga.id.toString()).executeAsBlocking()
            val actualRefresh = when (similarDbEntry == null) {
                true -> true
                false -> forceRefresh
            }
            val lang = sourceManager.getOrStub(manga.source).lang.ifBlank { "en" }
                .uppercase(Locale.getDefault())

            val dexId = getDexId(manga)

            val related = async {
                kotlin.runCatching {
                    createGroup(
                        R.string.related_type,
                        similarHandler.fetchRelated(dexId, manga.id, actualRefresh),
                        lang,
                    )
                }.onFailure { Timber.e(it, "Failed to get related") }
                    .getOrNull()
            }

            val similar = async {
                runCatching {
                    createGroup(
                        R.string.similar_type,
                        similarHandler.fetchSimilar(dexId, manga.id, actualRefresh),
                        lang,
                    )
                }.onFailure { Timber.e(it, "Failed to get similar") }
                    .getOrNull()
            }

            val mu = async {
                runCatching {
                    createGroup(
                        R.string.manga_updates,
                        similarHandler.fetchSimilarExternalMUManga(dexId, manga.id, actualRefresh),
                        lang,
                    )
                }.onFailure { Timber.e(it, "Failed to get MU recs") }
                    .getOrNull()
            }

            val anilist = async {
                runCatching {
                    createGroup(
                        R.string.anilist,
                        similarHandler.fetchAnilist(dexId, manga.id, actualRefresh),
                        lang,
                    )
                }.onFailure { Timber.e(it, "Failed to get anilist recs") }
                    .getOrNull()
            }

            val mal = async {
                runCatching {
                    createGroup(
                        R.string.myanimelist,
                        similarHandler.fetchSimilarExternalMalManga(dexId, manga.id, actualRefresh),
                        lang,
                    )
                }.onFailure { Timber.e(it, "Failed to get mal recs") }
                    .getOrNull()
            }

            listOfNotNull(related.await(), similar.await(), mu.await(), anilist.await(), mal.await())
        }
    }

    private fun getDexId(manga: Manga): String {
        return db.getTracks(manga).executeAsBlocking().firstNotNullOfOrNull {
            val service = when (it.sync_id) {
                TrackManager.MANGA_UPDATES -> "mu_new"
                TrackManager.ANILIST -> "al"
                TrackManager.MYANIMELIST -> "mal"
                TrackManager.KITSU -> "kt"
                else -> null
            }
            Timber.d("media_id: ${it.media_id}")
            similarHandler.mappings.getMangadexID(it.media_id.toString(), service)
        } ?: ""
    }

    private fun createGroup(@StringRes id: Int, manga: List<SourceManga>, lang: String): SimilarMangaGroup? {
        return if (manga.isEmpty()) {
            null
        } else {
            val delegate = sourceManager.getOnlineSources().find { it.toString() == "MangaDex ($lang)" }
                ?: error("Source not found")
            SimilarMangaGroup(
                id,
                manga.map {
                    it.toDisplayManga(db, delegate.id)
                },
            )
        }
    }
}

data class SimilarMangaGroup(@StringRes val type: Int, val manga: List<DisplayManga>)
