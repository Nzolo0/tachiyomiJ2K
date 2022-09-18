package eu.kanade.tachiyomi.ui.manga.stats

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.util.Pair
import androidx.core.view.isVisible
import com.github.mikephil.charting.components.MarkerView
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import com.github.mikephil.charting.utils.MPPointF
import com.google.android.material.datepicker.MaterialDatePicker
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.databinding.StatsMangaControllerBinding
import eu.kanade.tachiyomi.ui.base.controller.BaseCoroutineController
import eu.kanade.tachiyomi.ui.manga.stats.StatsMangaPresenter.StatsSort
import eu.kanade.tachiyomi.ui.more.stats.StatsHelper
import eu.kanade.tachiyomi.ui.more.stats.StatsHelper.getReadDuration
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.system.launchIO
import eu.kanade.tachiyomi.util.system.materialAlertDialog
import eu.kanade.tachiyomi.util.system.roundToTwoDecimal
import eu.kanade.tachiyomi.util.system.toLocalCalendar
import eu.kanade.tachiyomi.util.system.toUtcCalendar
import eu.kanade.tachiyomi.util.system.withIOContext
import eu.kanade.tachiyomi.util.view.compatToolTipText
import eu.kanade.tachiyomi.util.view.scrollViewWith
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlin.math.ceil

class StatsMangaController(val manga: Manga, val chapters: List<Chapter>) :
    BaseCoroutineController<StatsMangaControllerBinding, StatsMangaPresenter>() {

    override var presenter = StatsMangaPresenter(manga)
    private var adapter: StatsMangaAdapter? = null

    /**
     * Selected day in the read duration stat
     */
    private var highlightedDay: Calendar? = null
    private var jobReadDuration: Job? = null

    constructor(mangaId: Long) : this(
        Injekt.get<DatabaseHelper>().getManga(mangaId).executeAsBlocking()!!,
        Injekt.get<DatabaseHelper>().getChapters(mangaId).executeAsBlocking(),
    )

    constructor(bundle: Bundle) : this(bundle.getLong(MANGA_EXTRA))

    /**
     * Returns the toolbar title to show when this controller is attached.
     */
    override fun getTitle() = manga.title

    override fun createBinding(inflater: LayoutInflater) = StatsMangaControllerBinding.inflate(inflater)

    override fun onViewCreated(view: View) {
        super.onViewCreated(view)
        with(binding) {
            statSort.text = activity?.getString(presenter.selectedStatsSort.resourceId)
            scrollViewWith(statsScrollView, true)
            statSort.setOnClickListener {
                activity!!.materialAlertDialog()
                    .setTitle(R.string.sort_by)
                    .setSingleChoiceItems(
                        presenter.getSortDataArray(),
                        StatsSort.values().indexOf(presenter.selectedStatsSort),
                    ) { dialog, which ->
                        val newSelection = StatsSort.values()[which]
                        if (newSelection == presenter.selectedStatsSort) return@setSingleChoiceItems
                        statSort.text = activity?.getString(newSelection.resourceId)
                        presenter.selectedStatsSort = newSelection

                        dialog.dismiss()
                        presenter.sortCurrentStats()
                        updateStats()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
            statsDateText.setOnClickListener {
                val dialog = MaterialDatePicker.Builder.dateRangePicker()
                    .setTitleText(R.string.read_duration)
                    .setSelection(
                        Pair(
                            presenter.startDate.timeInMillis.toUtcCalendar()?.timeInMillis,
                            presenter.endDate.timeInMillis.toUtcCalendar()?.timeInMillis,
                        ),
                    )
                    .build()

                dialog.addOnPositiveButtonClickListener { utcMillis ->
                    val firstDay = utcMillis.first.toLocalCalendar()?.timeInMillis ?: utcMillis.first
                    val lastDay = utcMillis.second.toLocalCalendar()?.timeInMillis ?: utcMillis.second
                    updateReadDurationPeriod(firstDay, lastDay)
                }
                dialog.show(
                    (activity as AppCompatActivity).supportFragmentManager,
                    activity?.getString(R.string.read_duration),
                )
            }
            statsDateStartArrow.setOnClickListener {
                changeDatesReadDurationWithArrow(presenter.startDate, -1)
            }
            statsDateEndArrow.setOnClickListener {
                changeDatesReadDurationWithArrow(presenter.endDate, 1)
            }
        }
        handleGeneralStats()
        if (chapters.isNotEmpty()) {
            presenter.getReadDurationData()
        }
    }

    private fun updateReadDurationPeriod(firstDay: Long, lastDay: Long) {
        viewScope.launch {
            presenter.updateReadDurationPeriod(firstDay, lastDay)
            withIOContext { presenter.updateMangaHistory() }
            binding.statsDateText.text = presenter.getPeriodString()
            binding.statsBarChart.highlightValues(null)
            highlightedDay = null
            presenter.getReadDurationData()
        }
    }

    override fun onDestroyView(view: View) {
        jobReadDuration?.cancel()
        adapter = null
        super.onDestroyView(view)
    }

    private fun handleGeneralStats() {
        val mangaTracks = presenter.getTracks(manga)
        val scoresList = getScoresList(mangaTracks)
        with(binding) {
            statsTotalChaptersText.text = chapters.count().toString()
            statsChaptersReadText.text = chapters.count { it.read }.toString()
            statsMangaMeanScoreText.text = if (scoresList.isEmpty()) {
                statsMangaMeanScoreText.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
                activity?.getString(R.string.none)
            } else {
                scoresList.average().roundToTwoDecimal().toString()
            }
            statsChaptersBookmarkedText.text = chapters.count { it.bookmark }.toString()
            statsChaptersDownloadedText.text = presenter.getDownloadCount(manga).toString()
            statsReadDurationText.text = presenter.getReadDuration()
            statsReadDurationLayout.compatToolTipText = activity?.getString(R.string.read_duration_info)
            statsCategoriesText.text = presenter.getCategories().count().toString()
            statsLastReadChapterText.text = presenter.history.maxOfOrNull { it.last_read }?.run {
                val lastReadCalendar = Calendar.getInstance().apply {
                    timeInMillis = this@run
                }
                val firstDayOfWeek = presenter.getFirstDayOfWeek(lastReadCalendar.timeInMillis).timeInMillis
                val lastDayOfWeek = presenter.getLastDayOfWeek(firstDayOfWeek).timeInMillis
                statsLastReadChapterLayout.setOnClickListener {
                    if (presenter.startDate.after(lastReadCalendar) || presenter.endDate.before(lastReadCalendar)) {
                        updateReadDurationPeriod(firstDayOfWeek, lastDayOfWeek)
                    }
                }
                val currentYear = Calendar.getInstance().get(Calendar.YEAR)
                val showYear = lastReadCalendar.get(Calendar.YEAR) != currentYear
                presenter.convertCalendarToString(lastReadCalendar, showYear)
            } ?: activity?.getString(R.string.none)

            statsTrackersText.text = mangaTracks.count().toString()

            statsChartAllLayout.isVisible = statsReadDurationText.text != activity?.getString(R.string.none)
        }
    }

    /**
     * Changes dates of the read duration stat with the arrows
     * @param referenceDate date used to determine if should change week
     * @param toAdd whether to add or remove
     */
    private fun changeDatesReadDurationWithArrow(referenceDate: Calendar, toAdd: Int) {
        jobReadDuration?.cancel()
        with(binding) {
            if (highlightedDay == null) {
                changeReadDurationPeriod(toAdd)
            } else {
                val daySelected = highlightedDay?.get(Calendar.DAY_OF_YEAR)
                val endDay = referenceDate.get(Calendar.DAY_OF_YEAR)
                statsBarChart.highlightValues(null)
                highlightedDay = Calendar.getInstance().apply {
                    timeInMillis = highlightedDay!!.timeInMillis
                    add(Calendar.DAY_OF_YEAR, toAdd)
                }
                if (daySelected == endDay) {
                    changeReadDurationPeriod(toAdd)
                } else {
                    updateHighlightedValue()
                }
            }
        }
    }

    /**
     * Changes week of the read duration stat
     * @param toAdd whether to add or remove
     */
    private fun changeReadDurationPeriod(toAdd: Int) {
        presenter.changeReadDurationPeriod(toAdd)
        jobReadDuration = viewScope.launchIO {
            presenter.updateMangaHistory()
            presenter.getReadDurationData()
        }
    }

    private fun updateHighlightedValue() {
        with(binding) {
            val highlightValue = presenter.historyByPeriod.keys.toTypedArray().indexOfFirst {
                it.get(Calendar.DAY_OF_YEAR) == highlightedDay?.get(Calendar.DAY_OF_YEAR) &&
                    it.get(Calendar.YEAR) == highlightedDay?.get(Calendar.YEAR)
            }
            if (highlightValue == -1) return
            statsBarChart.highlightValue(highlightValue.toFloat(), 0)
            statsBarChart.marker.refreshContent(
                statsBarChart.data.dataSets[0].getEntryForXValue(highlightValue.toFloat(), 0f),
                statsBarChart.getHighlightByTouchPoint(highlightValue.toFloat(), 0f),
            )
        }
    }

    fun updateStats() {
        with(binding) {
            handleReadDurationLayout()
            totalDurationStatsText.text = presenter.totalReadDuration?.getReadDuration()
            if (highlightedDay != null) updateHighlightedValue() else statsDateText.text = presenter.getPeriodString()
        }
    }

    private fun getScoresList(mangaTracks: List<Track>): List<Float> {
        return mangaTracks
            .filter { track -> track.score > 0 }
            .mapNotNull { track -> presenter.get10PointScore(track) }
    }

    private fun handleReadDurationLayout() {
        val barEntries = ArrayList<BarEntry>()

        presenter.historyByPeriod.entries.forEachIndexed { index, entry ->
            barEntries.add(
                BarEntry(
                    index.toFloat(),
                    entry.value.sumOf { h -> h.time_read }.toFloat(),
                ),
            )
        }

        assignAdapter()
        if (barEntries.all { it.y == 0f }) {
            highlightedDay = null
        }
        val barDataSet = BarDataSet(barEntries, "Read Duration Distribution")
        barDataSet.color = StatsHelper.PIE_CHART_COLOR_LIST[1]
        setupBarChart(
            barDataSet,
            presenter.historyByPeriod.keys.map { presenter.getCalendarShortDay(it) }.toList(),
        )
    }

    private fun assignAdapter() {
        binding.statsRecyclerView.adapter = StatsMangaAdapter(
            activity!!,
            presenter.currentStats ?: ArrayList(),
            presenter.selectedStatsSort,
        ).also { adapter = it }
    }

    private fun setupBarChart(barDataSet: BarDataSet, xAxisLabel: List<String>? = null) {
        with(binding) {
            statsBarChart.data?.clearValues()
            statsBarChart.xAxis.valueFormatter = null
            statsBarChart.notifyDataSetChanged()
            statsBarChart.clear()
            statsBarChart.invalidate()
            statsBarChart.axisLeft.resetAxisMinimum()
            statsBarChart.axisLeft.resetAxisMaximum()

            try {
                val newValueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float) = value.toLong().getReadDuration()
                }

                val barData = BarData(barDataSet)
                barData.setValueTextColor(activity!!.getResourceColor(R.attr.colorOnBackground))
                barData.barWidth = 0.6F
                barData.setValueFormatter(newValueFormatter)
                barData.setValueTextSize(10f)
                barData.setDrawValues(false)
                statsBarChart.axisLeft.isEnabled = true
                statsBarChart.axisRight.isEnabled = false

                statsBarChart.xAxis.apply {
                    setDrawGridLines(false)
                    position = XAxis.XAxisPosition.BOTTOM
                    setLabelCount(barDataSet.entryCount, false)
                    textColor = activity!!.getResourceColor(R.attr.colorOnBackground)

                    if (!xAxisLabel.isNullOrEmpty()) {
                        valueFormatter = object : ValueFormatter() {
                            override fun getFormattedValue(value: Float): String {
                                return if (value < xAxisLabel.size) xAxisLabel[value.toInt()] else ""
                            }
                        }
                    }
                }

                statsBarChart.apply {
                    setTouchEnabled(true)
                    isDragEnabled = false
                    isDoubleTapToZoomEnabled = false
                    description.isEnabled = false
                    legend.isEnabled = false

                    val mv = MyMarkerView(activity, R.layout.custom_marker_view)
                    mv.chartView = this
                    marker = mv

                    axisLeft.apply {
                        textColor = activity!!.getResourceColor(R.attr.colorOnBackground)
                        axisLineColor = activity!!.getResourceColor(R.attr.colorOnBackground)
                        valueFormatter = newValueFormatter
                        val topValue = barData.yMax.getRoundedMaxLabel()
                        axisMaximum = topValue
                        axisMinimum = 0f
                        setLabelCount(4, true)
                    }

                    setOnChartValueSelectedListener(
                        object : OnChartValueSelectedListener {
                            override fun onValueSelected(e: Entry, h: Highlight) {
                                highlightValue(h)
                                highlightedDay = presenter.historyByPeriod.keys.toTypedArray()[e.x.toInt()]
                                statsDateText.text = presenter.convertCalendarToLongString(highlightedDay!!)
                                presenter.setupReadDuration(highlightedDay)
                                assignAdapter()
                                totalDurationStatsText.text = presenter.totalReadDuration?.getReadDuration()
                            }

                            override fun onNothingSelected() {
                                presenter.setupReadDuration()
                                highlightedDay = null
                                statsDateText.text = presenter.getPeriodString()
                                assignAdapter()
                                totalDurationStatsText.text = presenter.totalReadDuration?.getReadDuration()
                            }
                        },
                    )
                    data = barData
                    invalidate()
                }
            } catch (e: Exception) {
                Timber.e(e)
            }
        }
    }

    /**
     * Round the rounded max label of the bar chart to avoid weird values
     */
    private fun Float.getRoundedMaxLabel(): Float {
        val longValue = toLong()
        val hours = TimeUnit.MILLISECONDS.toHours(longValue) % 24
        val minutes = TimeUnit.MILLISECONDS.toMinutes(longValue) % 60

        val multiple = when {
            hours > 1L -> 3600 / 2 // 30min
            minutes >= 15L || hours == 1L -> 300 * 3 // 15min
            else -> 60 * 3 // 3min
        } * 1000
        return ceil(this / multiple) * multiple
    }

    /**
     * Custom MarkerView displayed when a bar is selected in the bar chart
     */
    inner class MyMarkerView(context: Context?, layoutResource: Int) : MarkerView(context, layoutResource) {

        private val markerText: TextView = findViewById(R.id.marker_text)

        override fun refreshContent(e: Entry, highlight: Highlight) {
            markerText.text = e.y.toLong().getReadDuration()
            super.refreshContent(e, highlight)
        }

        override fun getOffset(): MPPointF {
            return MPPointF((-(width / 2)).toFloat(), (-height).toFloat())
        }
    }

    companion object {
        const val MANGA_EXTRA = "manga"
    }
}
