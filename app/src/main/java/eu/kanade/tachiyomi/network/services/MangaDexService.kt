package eu.kanade.tachiyomi.network.services

import com.skydoves.sandwich.ApiResponse
import eu.kanade.tachiyomi.network.ProxyRetrofitQueryMap
import eu.kanade.tachiyomi.source.online.models.dto.MangaListDto
import eu.kanade.tachiyomi.source.online.models.dto.RelationListDto
import eu.kanade.tachiyomi.source.online.utils.MdApi
import eu.kanade.tachiyomi.source.online.utils.MdConstants
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.QueryMap

interface MangaDexService {

    @GET("${MdApi.manga}?includes[]=${MdConstants.Types.coverArt}")
    suspend fun search(@QueryMap options: ProxyRetrofitQueryMap): ApiResponse<MangaListDto>

    @GET("${MdApi.manga}/{id}/relation")
    suspend fun relatedManga(@Path("id") id: String): ApiResponse<RelationListDto>
}
