package eu.kanade.tachiyomi.ui.manga.stats

import android.text.format.DateUtils
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.History
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.ui.base.presenter.BaseCoroutinePresenter
import eu.kanade.tachiyomi.ui.more.stats.StatsHelper.getReadDuration
import eu.kanade.tachiyomi.util.system.launchIO
import eu.kanade.tachiyomi.util.system.withUIContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Presenter of [StatsMangaController].
 */
class StatsMangaPresenter(
    private val manga: Manga,
    private val db: DatabaseHelper = Injekt.get(),
    private val trackManager: TrackManager = Injekt.get(),
    private val downloadManager: DownloadManager = Injekt.get(),
) : BaseCoroutinePresenter<StatsMangaController>() {

    var context = downloadManager.context

    var startDate: Calendar = getFirstDayOfWeek()
    var endDate: Calendar = getLastDayOfWeek(startDate.timeInMillis)
    private var daysRange = getDaysRange()
    var history = getMangaHistory()
    var historyByPeriod = getMangaHistoryGroupedByDay()
    var totalReadDuration: Long? = null
    var currentStats: ArrayList<StatsData>? = null
    var selectedStatsSort: StatsSort = StatsSort.CHAPTER_NUMBER
    private var chapters: MutableList<Chapter> = db.getChapters(manga).executeAsBlocking()

    fun getFirstDayOfWeek(calendar: Long = Calendar.getInstance().timeInMillis): Calendar {
        return Calendar.getInstance().apply {
            timeInMillis = calendar
            set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
            set(Calendar.HOUR_OF_DAY, 0)
            clear(Calendar.MINUTE)
            clear(Calendar.SECOND)
            clear(Calendar.MILLISECOND)
        }
    }

    fun getLastDayOfWeek(calendar: Long): Calendar {
        return Calendar.getInstance().apply {
            timeInMillis = calendar - 1
            add(Calendar.WEEK_OF_YEAR, 1)
        }
    }

    fun getTracks(manga: Manga): MutableList<Track> {
        return db.getTracks(manga).executeAsBlocking()
    }

    fun getDownloadCount(manga: Manga): Int {
        return downloadManager.getDownloadCount(manga)
    }

    fun get10PointScore(track: Track): Float? {
        val service = trackManager.getService(track.sync_id)
        return service?.get10PointScore(track.score)
    }

    fun getReadDuration(): String {
        val chaptersTime = db.getHistoryByMangaId(manga.id!!).executeAsBlocking().sumOf { it.time_read }
        return chaptersTime.getReadDuration(context.getString(R.string.none))
    }

    fun getCategories(): List<Category> {
        return db.getCategoriesForManga(manga).executeAsBlocking()
    }

    /**
     * Get the data of the selected stat
     */
    fun getReadDurationData() {
        presenterScope.launchIO {
            setupReadDuration()
            withUIContext { view?.updateStats() }
        }
    }

    fun setupReadDuration(day: Calendar? = null) {
        currentStats = ArrayList()

        val historyManga = if (day == null) {
            historyByPeriod.values.flatten()
        } else {
            historyByPeriod[day]
        }

        historyManga?.forEach { h ->
            val chapter = chapters.firstOrNull { it.id == h.chapter_id }
            currentStats?.add(
                StatsData(
                    chapterNumber = chapter?.chapter_number ?: 0f,
                    label = chapter?.name,
                    subLabel = chapter?.scanlator,
                    readDuration = h.time_read,
                ),
            )
        }

        sortCurrentStats()
        totalReadDuration = historyManga?.sumOf { it.time_read } ?: 0L
    }

    fun sortCurrentStats() {
        when (selectedStatsSort) {
            StatsSort.CHAPTER_NUMBER -> currentStats?.sortWith(
                compareByDescending<StatsData> { it.chapterNumber }.thenByDescending { it.readDuration },
            )
            StatsSort.READ_DURATION_COUNT -> currentStats?.sortWith(
                compareByDescending<StatsData> { it.readDuration }.thenByDescending { it.chapterNumber },

            )
        }
    }

    fun changeReadDurationPeriod(toAdd: Int) {
        startDate.add(Calendar.DAY_OF_YEAR, toAdd * daysRange.toInt())
        endDate.add(Calendar.DAY_OF_YEAR, toAdd * daysRange.toInt())
    }

    private fun getDaysRange(): Long {
        return TimeUnit.MILLISECONDS.toDays(endDate.timeInMillis - startDate.timeInMillis) + 1
    }

    /**
     * Update the start date and end date according to time selected
     */
    fun updateReadDurationPeriod(startMillis: Long, endMillis: Long) {
        startDate = Calendar.getInstance().apply {
            timeInMillis = startMillis
            set(Calendar.HOUR_OF_DAY, 0)
            clear(Calendar.MINUTE)
            clear(Calendar.SECOND)
            clear(Calendar.MILLISECOND)
        }
        endDate = Calendar.getInstance().apply {
            timeInMillis = endMillis
            set(Calendar.HOUR_OF_DAY, 0)
            clear(Calendar.MINUTE)
            clear(Calendar.SECOND)
            clear(Calendar.MILLISECOND)
            add(Calendar.DAY_OF_YEAR, 1)
            timeInMillis -= 1
        }
        daysRange = getDaysRange()
    }

    private fun getMangaHistory(): MutableList<History> {
        return db.getHistoryByMangaId(manga.id!!).executeAsBlocking()
    }

    fun updateMangaHistory() {
        historyByPeriod = getMangaHistoryGroupedByDay()
    }

    /**
     * Get the manga and history grouped by day during the selected period
     */
    private fun getMangaHistoryGroupedByDay(): Map<Calendar, List<History>> {
        val periodHistory = history.filter { it.last_read in startDate.timeInMillis..endDate.timeInMillis }
        val calendar = Calendar.getInstance().apply {
            timeInMillis = startDate.timeInMillis
        }

        return (0 until daysRange).associate { _ ->
            Calendar.getInstance().apply { timeInMillis = calendar.timeInMillis } to periodHistory.filter {
                val calH = Calendar.getInstance().apply { timeInMillis = it.last_read }
                calH.get(Calendar.DAY_OF_YEAR) == calendar.get(Calendar.DAY_OF_YEAR) &&
                    calH.get(Calendar.YEAR) == calendar.get(Calendar.YEAR)
            }.also { calendar.add(Calendar.DAY_OF_WEEK, 1) }
        }
    }

    fun getCalendarShortDay(calendar: Calendar): String {
        return if (historyByPeriod.size > 14) {
            ""
        } else {
            calendar.getDisplayName(
                Calendar.DAY_OF_WEEK,
                Calendar.SHORT,
                Locale.getDefault(),
            )
        } ?: context.getString(R.string.unknown)
    }

    fun convertCalendarToLongString(calendar: Calendar): String {
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val showYear = calendar.get(Calendar.YEAR) != currentYear
        val flagYear = if (showYear) DateUtils.FORMAT_ABBREV_MONTH else 0
        val flags = DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_WEEKDAY or flagYear
        return DateUtils.formatDateTime(context, calendar.timeInMillis, flags)
    }

    fun convertCalendarToString(calendar: Calendar, showYear: Boolean): String {
        val flagYear = if (showYear) DateUtils.FORMAT_ABBREV_MONTH or DateUtils.FORMAT_SHOW_YEAR else 0
        val flags = DateUtils.FORMAT_SHOW_DATE or flagYear
        return DateUtils.formatDateTime(context, calendar.timeInMillis, flags)
    }

    fun getPeriodString(): String {
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val showYear = listOf(startDate, endDate).any { it.get(Calendar.YEAR) != currentYear }
        val startDateString = convertCalendarToString(startDate, showYear)
        val endDateString = convertCalendarToString(endDate, showYear)
        return "$startDateString - $endDateString"
    }

    fun getSortDataArray(): Array<String> {
        return StatsSort.values().sortedArray().map { context.getString(it.resourceId) }.toTypedArray()
    }

    class StatsData(
        val chapterNumber: Float = 0f,
        var label: String? = null,
        var subLabel: String? = null,
        var readDuration: Long = 0,
    )

    enum class StatsSort(val resourceId: Int) {
        CHAPTER_NUMBER(R.string.chapter_number),
        READ_DURATION_COUNT(R.string.read_duration),
    }
}
