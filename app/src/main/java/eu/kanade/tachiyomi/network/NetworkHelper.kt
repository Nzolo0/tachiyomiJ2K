package eu.kanade.tachiyomi.network

import android.content.Context
import com.chuckerteam.chucker.api.ChuckerCollector
import com.chuckerteam.chucker.api.ChuckerInterceptor
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.skydoves.sandwich.adapters.ApiResponseCallAdapterFactory
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.network.interceptor.CloudflareInterceptor
import eu.kanade.tachiyomi.network.interceptor.UncaughtExceptionInterceptor
import eu.kanade.tachiyomi.network.interceptor.UserAgentInterceptor
import eu.kanade.tachiyomi.network.services.MangaDexService
import eu.kanade.tachiyomi.network.services.SimilarService
import eu.kanade.tachiyomi.network.services.ThirdPartySimilarService
import eu.kanade.tachiyomi.source.online.utils.MdApi
import eu.kanade.tachiyomi.source.online.utils.MdConstants
import eu.kanade.tachiyomi.source.online.utils.MdUtil
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.brotli.BrotliInterceptor
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.util.concurrent.TimeUnit

class NetworkHelper(val context: Context) {

    private val preferences: PreferencesHelper by injectLazy()

    private val cacheDir = File(context.cacheDir, "network_cache")

    private val cacheSize = 5L * 1024 * 1024 // 5 MiB

    val cookieJar = AndroidCookieJar()

    private val userAgentInterceptor by lazy { UserAgentInterceptor(::defaultUserAgent) }
    private val cloudflareInterceptor by lazy {
        CloudflareInterceptor(context, cookieJar, ::defaultUserAgent)
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val baseClientBuilder: OkHttpClient.Builder
        get() {
            val builder = OkHttpClient.Builder()
                .cookieJar(cookieJar)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .callTimeout(2, TimeUnit.MINUTES)
                .addInterceptor(BrotliInterceptor)
                .addInterceptor(UncaughtExceptionInterceptor())
                .addInterceptor(userAgentInterceptor)
                .apply {
                    if (BuildConfig.DEBUG) {
                        addInterceptor(
                            ChuckerInterceptor.Builder(context)
                                .collector(ChuckerCollector(context))
                                .maxContentLength(250000L)
                                .redactHeaders(emptySet())
                                .alwaysReadResponseBody(false)
                                .build(),
                        )
                    }

                    when (preferences.dohProvider()) {
                        PREF_DOH_CLOUDFLARE -> dohCloudflare()
                        PREF_DOH_GOOGLE -> dohGoogle()
                        PREF_DOH_ADGUARD -> dohAdGuard()
                        PREF_DOH_QUAD9 -> dohQuad9()
                    }
                }

            return builder
        }

    val client by lazy { baseClientBuilder.cache(Cache(cacheDir, cacheSize)).build() }

    @Suppress("UNUSED")
    val cloudflareClient by lazy {
        client.newBuilder()
            .addInterceptor(cloudflareInterceptor)
            .build()
    }

    private val scalarsRetrofitClient = Retrofit.Builder().addConverterFactory(
        ScalarsConverterFactory.create(),
    ).baseUrl(MdConstants.baseUrl)
        .addCallAdapterFactory(ApiResponseCallAdapterFactory.create())
        .client(client)

    private val jsonRetrofitClient = Retrofit.Builder().addConverterFactory(
        json.asConverterFactory("application/json".toMediaType()),
    )
        .baseUrl(MdConstants.baseUrl)
        .addCallAdapterFactory(ApiResponseCallAdapterFactory.create())
        .client(client)

    val mangadexService: MangaDexService =
        jsonRetrofitClient.baseUrl(MdApi.baseUrl)
            .client(client.newBuilder().addNetworkInterceptor(HeadersInterceptor()).build()).build()
            .create(MangaDexService::class.java)

    val thirdPartySimilarService: ThirdPartySimilarService =
        jsonRetrofitClient.client(
            client.newBuilder().connectTimeout(2, TimeUnit.SECONDS)
                .readTimeout(2, TimeUnit.SECONDS).build(),
        )
            .build()
            .create(ThirdPartySimilarService::class.java)

    val similarService: SimilarService =
        scalarsRetrofitClient.client(
            client.newBuilder().connectTimeout(2, TimeUnit.SECONDS)
                .readTimeout(2, TimeUnit.SECONDS).build(),
        )
            .build()
            .create(SimilarService::class.java)

    class HeadersInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request().newBuilder()
                .removeHeader("User-Agent")
                .header("User-Agent", "J " + System.getProperty("http.agent"))
                .header("Referer", MdUtil.baseUrl)
                .header("Content-Type", "application/json")
                .build()

            return chain.proceed(request)
        }
    }

    val defaultUserAgent
        get() = preferences.defaultUserAgent().get().replace("\n", " ").trim()

    companion object {
        const val DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/118.0"
    }
}
