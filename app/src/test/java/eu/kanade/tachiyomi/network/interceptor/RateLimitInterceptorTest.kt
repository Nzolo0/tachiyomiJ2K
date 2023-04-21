package eu.kanade.tachiyomi.network.interceptor

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.concurrent.TimeUnit

class RateLimitInterceptorTest {

    @ParameterizedTest
    @MethodSource("rateLimit")
    fun keepRateLimitAt1RequestPer1SecondAtMost(permits: Int, period: Long, unit: TimeUnit, expectedRateLimit: Long) {
        /*        val permitsInSeconds = unit.convert(permits.toLong(), TimeUnit.SECONDS)
                val rateLimitInMilli = TimeUnit.SECONDS.toMillis(minOf(period / permitsInSeconds, period))*/

        val periodInSeconds = unit.toSeconds(period)
        val obtainedRateLimit = TimeUnit.SECONDS.toMillis(minOf(permits.toLong(), periodInSeconds))

        assertEquals(obtainedRateLimit, expectedRateLimit)
    }

    companion object {
        @JvmStatic
        fun rateLimit() = listOf(
            Arguments.of(50, 1, TimeUnit.MINUTES, 50000),
            Arguments.of(50, 5, TimeUnit.MINUTES, 50000),
            Arguments.of(1, 2, TimeUnit.SECONDS, 1000),
            Arguments.of(4, 3, TimeUnit.SECONDS, 3000),
            Arguments.of(1, 1, TimeUnit.SECONDS, 1000),
        )
    }
}
