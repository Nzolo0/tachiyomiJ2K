package eu.kanade.tachiyomi.source.online.handlers

import com.skydoves.sandwich.ApiResponse
import com.skydoves.sandwich.getOrNull
import com.skydoves.sandwich.getOrThrow
import com.skydoves.sandwich.onError
import com.skydoves.sandwich.onFailure
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga.SourceManga
import eu.kanade.tachiyomi.data.database.models.MangaSimilar
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.ProxyRetrofitQueryMap
import eu.kanade.tachiyomi.source.online.models.dto.AnilistMangaRecommendationsDto
import eu.kanade.tachiyomi.source.online.models.dto.MUMangaDto
import eu.kanade.tachiyomi.source.online.models.dto.MalMangaRecommendationsDto
import eu.kanade.tachiyomi.source.online.models.dto.MangaDataDto
import eu.kanade.tachiyomi.source.online.models.dto.MangaListDto
import eu.kanade.tachiyomi.source.online.models.dto.RelatedMangaDto
import eu.kanade.tachiyomi.source.online.models.dto.SimilarMangaDatabaseDto
import eu.kanade.tachiyomi.source.online.models.dto.SimilarMangaDto
import eu.kanade.tachiyomi.source.online.utils.MdConstants
import eu.kanade.tachiyomi.source.online.utils.MdUtil
import eu.kanade.tachiyomi.source.online.utils.toBasicManga
import eu.kanade.tachiyomi.util.log
import eu.kanade.tachiyomi.util.manga.MangaMappings
import eu.kanade.tachiyomi.util.system.withIOContext
import eu.kanade.tachiyomi.util.throws
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import uy.kohesive.injekt.injectLazy
import java.util.Locale

class SimilarHandler {

    private val network: NetworkHelper by injectLazy()
    private val db: DatabaseHelper by injectLazy()
    private val mappings: MangaMappings by injectLazy()
    private val json: Json by injectLazy()

    suspend fun fetchRelated(
        dexId: String,
        mangaId: Long?,
        forceRefresh: Boolean,
    ): List<SourceManga> {
        if (forceRefresh && dexId.isNotEmpty()) {
            val related = withIOContext {
                network.mangadexService.relatedManga(dexId)
                    .onFailure {
                        Timber.e("trying to get related manga, $this")
                    }
                    .getOrNull()
            }
            related ?: return emptyList()

            val mangaIdMap = related.data.mapNotNull {
                if (it.relationships.isEmpty()) return@mapNotNull null
                it.relationships.first().id to it.attributes.relation
            }.toMap()

            val mangaList = similarGetMangadexMangaList(mangaIdMap.keys.toList(), false)
            val relatedMangaList = mangaList.data.map {
                it.toRelatedMangaDto(0, mangaIdMap[it.id] ?: "")
            }

            // Update the Manga Similar database
            val mangaDb = db.getSimilar(mangaId.toString()).executeAsBlocking()
            val dbDto = getDbDto(mangaDb)
            dbDto.relatedManga = relatedMangaList
            insertMangaSimilar(mangaId.toString(), dbDto, mangaDb)
        }

        val dbDto = getDbDto(db.getSimilar(mangaId.toString()).executeAsBlocking())
        return dbDto.relatedManga?.map {
            it.toSourceManga()
        } ?: emptyList()
    }

    private fun insertMangaSimilar(
        mangaId: String,
        dbDto: SimilarMangaDatabaseDto,
        mangaDb: MangaSimilar?,
    ) {
        // If we have the manga in our database, then we should update it, otherwise insert as new
        val similarDatabaseDtoString = MdUtil.jsonParser.encodeToString(dbDto)
        val mangaSimilar = MangaSimilar.create().apply {
            id = mangaDb?.id
            manga_id = mangaId
            data = similarDatabaseDtoString
        }

        db.insertSimilar(mangaSimilar).executeAsBlocking()
    }

    private fun getDbDto(mangaDb: MangaSimilar?): SimilarMangaDatabaseDto {
        return runCatching {
            MdUtil.jsonParser.decodeFromString<SimilarMangaDatabaseDto>(mangaDb!!.data)
        }.getOrElse {
            SimilarMangaDatabaseDto()
        }
    }

    private fun RelatedMangaDto.toSourceManga() = SourceManga(
        url = this.url,
        currentThumbnail = this.thumbnail,
        title = this.title,
        displayText = this.relation,
    )

    private fun MangaDataDto.toRelatedMangaDto(
        thumbQuality: Int,
        otherText: String,
    ): RelatedMangaDto {
        val manga = this.toBasicManga(thumbQuality)
        return RelatedMangaDto(
            manga.url,
            manga.title,
            manga.thumbnail_url!!,
            otherText,
        )
    }

    /**
     * fetch our similar mangaList
     */
    suspend fun fetchSimilar(
        dexId: String,
        mangaId: Long?,
        forceRefresh: Boolean,
    ): List<SourceManga> {
        if (forceRefresh && dexId.isNotEmpty()) {
            val response = network.similarService.getSimilarMangaString(dexId.substring(0, 2), dexId.substring(0, 3)).onFailure {
                Timber.e("trying to get similar manga, $this")
            }.getOrNull()

            val dto = response?.split("\n")?.firstNotNullOfOrNull { line ->
                val splitLine = line.split(":::||@!@||:::")
                if (splitLine.isNotEmpty() && splitLine.size == 2 && splitLine[0] == dexId) {
                    json.decodeFromString<SimilarMangaDto>(splitLine[1])
                } else {
                    null
                }
            }

            similarMangaParse(mangaId.toString(), dto)
        }

        val mangaDb = db.getSimilar(mangaId.toString()).executeAsBlocking()
        val dbDto = getDbDto(mangaDb)
        // Get data from db
        return dbDto.similarManga?.map { it.toSourceManga() }?.sortedByDescending {
            it.displayText.split("%")[0].toDouble()
        } ?: emptyList()
    }

    private suspend fun similarMangaParse(
        mangaId: String,
        similarDto: SimilarMangaDto?,
    ) {
        similarDto ?: return

        // Get our page of mangaList
        // TODO: We should also remove any that have a bad language here
        val idPairs = similarDto.matches.associate {
            val id = it.id
            val text = String.format(Locale.ROOT, "%.2f", 100.0 * it.score) + "% match"
            id to text
        }
        if (idPairs.isEmpty()) {
            return
        }

        val mangaListDto = similarGetMangadexMangaList(idPairs.mapNotNull { it.key }, false)
        val mangaList = mangaListDto.data.map {
            it.toRelatedMangaDto(0, idPairs[it.id] ?: "")
        }

        // update db
        val mangaDb = db.getSimilar(mangaId).executeAsBlocking()
        val dbDto = getDbDto(mangaDb)
        dbDto.similarApi = similarDto
        dbDto.similarManga = mangaList
        insertMangaSimilar(mangaId, dbDto, mangaDb)
    }

    /**
     * fetch our similar mangaList from external service Anilist
     */
    suspend fun fetchAnilist(
        dexId: String,
        mangaId: Long?,
        forceRefresh: Boolean,
    ): List<SourceManga> {
        if (forceRefresh && dexId.isNotEmpty()) {
            // See if we have a valid mapping for our Anlist service
            val anilistId = mappings.getExternalID(dexId, "al") ?: return emptyList()
            // Main network request
            val graphql =
                """{ Media(id: $anilistId, type: MANGA) { recommendations { edges { node { mediaRecommendation { id format } rating } } } } }"""
            val response = network.thirdPartySimilarService.getAniListGraphql(graphql).onFailure {
                val type = "trying to get Anilist recommendations"
                this.log(type)
                if ((this is ApiResponse.Failure.Error && this.statusCode.code == 404) || this is ApiResponse.Failure.Exception) {
                    this.throws(type)
                }
            }.getOrNull()
            anilistRecommendationParse(mangaId.toString(), response)
        }

        val mangaDb = db.getSimilar(mangaId.toString()).executeAsBlocking()
        val dbDto = getDbDto(mangaDb)

        // Get data from db
        return dbDto.aniListManga?.map { it.toSourceManga() }?.sortedByDescending {
            it.displayText.split(" ")[0].toDouble()
        } ?: emptyList()
    }

    private suspend fun anilistRecommendationParse(
        mangaId: String,
        similarDto: AnilistMangaRecommendationsDto?,
    ) {
        // Error check http response
        similarDto ?: return

        // Get our page of mangaList
        val idPairs = similarDto.data.Media.recommendations.edges.map {
            if (it.node.mediaRecommendation.format != "MANGA") {
                return@map null
            }
            val id = mappings.getMangadexUUID(it.node.mediaRecommendation.id.toString(), "al")
            val text = it.node.rating.toString() + " user votes"
            id to text
        }.filterNotNull().toMap()
        if (idPairs.isEmpty()) {
            return
        }

        val mangaListDto = similarGetMangadexMangaList(idPairs.mapNotNull { it.key }, false)
        val mangaList = mangaListDto.data.map {
            it.toRelatedMangaDto(0, idPairs[it.id] ?: "")
        }

        // update db
        val mangaDb = db.getSimilar(mangaId).executeAsBlocking()
        val dbDto = getDbDto(mangaDb)
        dbDto.aniListApi = similarDto
        dbDto.aniListManga = mangaList
        insertMangaSimilar(mangaId, dbDto, mangaDb)
    }

    /**
     * fetch our similar mangaList from external service myanimelist
     */
    suspend fun fetchSimilarExternalMalManga(
        dexId: String,
        mangaId: Long?,
        forceRefresh: Boolean,
    ): List<SourceManga> {
        if (forceRefresh && dexId.isNotEmpty()) {
            // See if we have a valid mapping for our MAL service
            val malId = mappings.getExternalID(dexId, "mal") ?: return emptyList()
            val response = network.thirdPartySimilarService.getSimilarMalManga(malId).onFailure {
                val type = "trying to get MAL similar manga"
                this.log(type)
                if ((this is ApiResponse.Failure.Error && this.statusCode.code == 404) || this is ApiResponse.Failure.Exception) {
                    this.throws(type)
                }
            }.getOrNull()
            similarMangaExternalMalParse(mangaId.toString(), response)
        }

        val mangaDb = db.getSimilar(mangaId.toString()).executeAsBlocking()
        val dbDto = getDbDto(mangaDb)
        // Get data from db
        return dbDto.myAnimeListManga?.map { it.toSourceManga() }?.sortedByDescending {
            it.displayText.split(" ")[0].toDouble()
        } ?: emptyList()
    }

    private suspend fun similarMangaExternalMalParse(
        mangaId: String,
        similarDto: MalMangaRecommendationsDto?,
    ) {
        // Error check http response
        similarDto ?: return

        // Get our page of mangaList
        val idPairs = similarDto.data.associate {
            val id = mappings.getMangadexUUID(it.entry.mal_id.toString(), "mal")
            val text = it.votes.toString() + " user votes"
            id to text
        }
        if (idPairs.isEmpty()) {
            return
        }

        val mangaListDto = similarGetMangadexMangaList(idPairs.mapNotNull { it.key }, false)

        // Convert to lookup array
        // TODO: Also filter out manga here that are already presented
        val mangaList = mangaListDto.data.map {
            it.toRelatedMangaDto(0, idPairs[it.id] ?: "")
        }

        // update db
        val mangaDb = db.getSimilar(mangaId).executeAsBlocking()
        val dbDto = getDbDto(mangaDb)
        dbDto.myAnimelistApi = similarDto
        dbDto.myAnimeListManga = mangaList
        insertMangaSimilar(mangaId, dbDto, mangaDb)
    }

    /**
     * fetch our similar mangaList from external service mangaupdates
     */
    suspend fun fetchSimilarExternalMUManga(
        dexId: String,
        mangaId: Long?,
        forceRefresh: Boolean,
    ): List<SourceManga> {
        if (forceRefresh && dexId.isNotEmpty()) {
            // See if we have a valid mapping for our MU service
            val muId = mappings.getExternalID(dexId, "mu_new") ?: return emptyList()
            val response = network.thirdPartySimilarService.getSimilarMUManga(muId).onFailure {
                val type = "trying to get MU similar manga"
                this.log(type)
                if ((this is ApiResponse.Failure.Error && this.statusCode.code == 404) || this is ApiResponse.Failure.Exception) {
                    this.throws(type)
                }
            }.getOrNull()
            similarMangaExternalMUParse(mangaId.toString(), response)
        }
        val mangaDb = db.getSimilar(mangaId.toString()).executeAsBlocking()
        val dbDto = getDbDto(mangaDb)
        // Get data from db
        return dbDto.mangaUpdatesListManga?.map { it.toSourceManga() }?.sortedByDescending {
            when (it.displayText == "Similar") {
                true -> -1.0
                false -> it.displayText.split(" ")[0].toDouble()
            }
        } ?: emptyList()
    }

    private suspend fun similarMangaExternalMUParse(
        mangaId: String,
        similarDto: MUMangaDto?,
    ) {
        // Error check http response
        similarDto ?: return

        // Get our page of mangaList
        val idPairs = similarDto.recommendations.associate {
            val id = mappings.getMangadexUUID(it.series_id.toString(), "mu_new")
            val text = it.weight.toString() + " user votes"
            id to text
        }.toMutableMap()
        idPairs += similarDto.category_recommendations.associate {
            val id = mappings.getMangadexUUID(it.series_id.toString(), "mu_new")
            val text = "Similar"
            id to text
        }
        if (idPairs.isEmpty()) {
            return
        }

        val mangaListDto = similarGetMangadexMangaList(idPairs.mapNotNull { it.key }, false)

        // Convert to lookup array
        // TODO: Also filter out manga here that are already presented
        val mangaList = mangaListDto.data.map {
            it.toRelatedMangaDto(0, idPairs[it.id] ?: "")
        }

        // update db
        val mangaDb = db.getSimilar(mangaId).executeAsBlocking()
        val dbDto = getDbDto(mangaDb)
        dbDto.mangaUpdatesApi = similarDto
        dbDto.mangaUpdatesListManga = mangaList
        insertMangaSimilar(mangaId, dbDto, mangaDb)
    }

    /**
     * this will get the manga objects with cover_art for all the specified ids
     */
    private suspend fun similarGetMangadexMangaList(
        mangaIds: List<String>,
        strictMatch: Boolean = true,
    ): MangaListDto {
        val queryMap = mutableMapOf(
            "limit" to mangaIds.size,
            "ids[]" to mangaIds,
            "contentRating[]" to listOf(
                MdConstants.ContentRating.safe,
                MdConstants.ContentRating.suggestive,
                MdConstants.ContentRating.erotica,
                MdConstants.ContentRating.pornographic,
            ),
        )
        val responseBody = network.mangadexService.search(ProxyRetrofitQueryMap(queryMap)).onError {
            val type = "searching for manga in similar handler"
            this.log(type)
            this.throws(type)
        }.getOrThrow()

        if (strictMatch && responseBody.data.size != mangaIds.size) {
            Timber.e("manga returned doesn't match number of manga expected")
            throw Exception("Unable to complete response ${responseBody.data.size} of ${mangaIds.size} returned")
        }
        return responseBody
    }
}
