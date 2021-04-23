package eu.kanade.tachiyomi.ui.manga.track

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.core.net.toUri
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.track.TrackService
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.databinding.TrackingBottomSheetBinding
import eu.kanade.tachiyomi.ui.manga.MangaDetailsController
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.view.RecyclerWindowInsetsListener
import eu.kanade.tachiyomi.util.view.setEdgeToEdge
import timber.log.Timber

class TrackingBottomSheet(private val controller: MangaDetailsController) :
    BottomSheetDialog
    (controller.activity!!, R.style.BottomSheetDialogTheme),
    TrackAdapter.OnClickListener,
    SetTrackStatusDialog.Listener,
    SetTrackChaptersDialog.Listener,
    SetTrackScoreDialog.Listener,
    TrackRemoveDialog.Listener,
    SetTrackReadingDatesDialog.Listener {

    val activity = controller.activity!!

    private var sheetBehavior: BottomSheetBehavior<*>

    val presenter = controller.presenter

    private var adapter: TrackAdapter? = null

    private val binding = TrackingBottomSheetBinding.inflate(activity.layoutInflater)

    init {
        // Use activity theme for this layout
        setContentView(binding.root)

        sheetBehavior = BottomSheetBehavior.from(binding.root.parent as ViewGroup)
        setEdgeToEdge(activity, binding.root, 0)
        val height = activity.window.decorView.rootWindowInsets.systemWindowInsetBottom
        sheetBehavior.peekHeight = 500.dpToPx + height

        sheetBehavior.addBottomSheetCallback(
            object : BottomSheetBehavior.BottomSheetCallback() {
                override fun onSlide(bottomSheet: View, progress: Float) { }

                override fun onStateChanged(p0: View, state: Int) {
                    if (state == BottomSheetBehavior.STATE_EXPANDED) {
                        sheetBehavior.skipCollapsed = true
                    }
                }
            }
        )
    }

    override fun onStart() {
        super.onStart()
        sheetBehavior.skipCollapsed = true
    }

    /**
     * Called when the sheet is created. It initializes the listeners and values of the preferences.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        adapter = TrackAdapter(this)
        binding.trackRecycler.layoutManager = LinearLayoutManager(context)
        binding.trackRecycler.adapter = adapter
        binding.trackRecycler.setOnApplyWindowInsetsListener(RecyclerWindowInsetsListener)

        adapter?.items = presenter.trackList
    }

    fun onNextTrackings(trackings: List<TrackItem>) {
        onRefreshDone()
        adapter?.items = trackings
        controller.refreshTracker()
    }

    fun onSearchResults(results: List<TrackSearch>) {
        getSearchDialog()?.onSearchResults(results)
    }

    fun onSearchResultsError(error: Throwable) {
        Timber.e(error)
        activity.toast(error.message)
        getSearchDialog()?.onSearchResultsError()
    }

    private fun getSearchDialog(): TrackSearchDialog? {
        return controller.router.getControllerWithTag(TAG_SEARCH_CONTROLLER) as? TrackSearchDialog
    }

    fun onRefreshDone() {
        for (i in adapter!!.items.indices) {
            (binding.trackRecycler.findViewHolderForAdapterPosition(i) as? TrackHolder)?.setProgress(false)
        }
    }

    fun onRefreshError(error: Throwable) {
        for (i in adapter!!.items.indices) {
            (binding.trackRecycler.findViewHolderForAdapterPosition(i) as? TrackHolder)?.setProgress(false)
        }
        activity.toast(error.message)
    }

    override fun onLogoClick(position: Int) {
        val track = adapter?.getItem(position)?.track ?: return
        if (controller.isNotOnline()) {
            dismiss()
            return
        }

        if (track.tracking_url.isBlank()) {
            activity.toast(R.string.url_not_set_click_again)
        } else {
            activity.startActivity(Intent(Intent.ACTION_VIEW, track.tracking_url.toUri()))
            controller.refreshTracker = position
        }
    }

    override fun onSetClick(position: Int) {
        val item = adapter?.getItem(position) ?: return
        if (controller.isNotOnline()) {
            dismiss()
            return
        }

        TrackSearchDialog(this, item.service, item.track != null).showDialog(
            controller.router,
            TAG_SEARCH_CONTROLLER
        )
    }

    override fun onStatusClick(position: Int) {
        val item = adapter?.getItem(position) ?: return
        if (item.track == null) return
        if (controller.isNotOnline()) {
            dismiss()
            return
        }

        SetTrackStatusDialog(this, item).showDialog(controller.router)
    }

    override fun onRemoveClick(position: Int) {
        val item = adapter?.getItem(position) ?: return
        if (item.track == null) return

        if (controller.isNotOnline()) {
            dismiss()
            return
        }

        TrackRemoveDialog(this, item).showDialog(controller.router)
    }

    override fun onChaptersClick(position: Int) {
        val item = adapter?.getItem(position) ?: return
        if (item.track == null) return
        if (controller.isNotOnline()) {
            dismiss()
            return
        }
        SetTrackChaptersDialog(this, item).showDialog(controller.router)
    }

    override fun onScoreClick(position: Int) {
        val item = adapter?.getItem(position) ?: return
        if (item.track == null) return
        if (controller.isNotOnline()) {
            dismiss()
            return
        }

        SetTrackScoreDialog(this, item).showDialog(controller.router)
    }

    override fun onStartDateClick(position: Int) {
        val item = adapter?.getItem(position) ?: return
        if (item.track == null) return

        val suggestedDate = presenter.getSuggestedDate(SetTrackReadingDatesDialog.ReadingDate.Start)
        SetTrackReadingDatesDialog(
            controller,
            this,
            SetTrackReadingDatesDialog.ReadingDate.Start,
            item,
            suggestedDate
        )
            .showDialog(controller.router)
    }

    override fun onFinishDateClick(position: Int) {
        val item = adapter?.getItem(position) ?: return
        if (item.track == null) return

        val suggestedDate = presenter.getSuggestedDate(SetTrackReadingDatesDialog.ReadingDate.Finish)
        SetTrackReadingDatesDialog(
            controller,
            this,
            SetTrackReadingDatesDialog.ReadingDate.Finish,
            item,
            suggestedDate
        )
            .showDialog(controller.router)
    }

    override fun setStatus(item: TrackItem, selection: Int) {
        presenter.setStatus(item, selection)
        refreshItem(item)
    }

    private fun refreshItem(item: TrackItem) {
        refreshTrack(item.service)
    }

    fun refreshItem(index: Int) {
        (binding.trackRecycler.findViewHolderForAdapterPosition(index) as? TrackHolder)?.setProgress(true)
    }

    fun refreshTrack(item: TrackService?) {
        val index = adapter?.indexOf(item) ?: -1
        if (index > -1) {
            (binding.trackRecycler.findViewHolderForAdapterPosition(index) as? TrackHolder)
                ?.setProgress(true)
        }
    }

    override fun setScore(item: TrackItem, score: Int) {
        presenter.setScore(item, score)
        refreshItem(item)
    }

    override fun setChaptersRead(item: TrackItem, chaptersRead: Int) {
        presenter.setLastChapterRead(item, chaptersRead)
        refreshItem(item)
    }

    override fun removeTracker(item: TrackItem, fromServiceAlso: Boolean) {
        refreshTrack(item.service)
        presenter.removeTracker(item, fromServiceAlso)
    }

    override fun setReadingDate(item: TrackItem, type: SetTrackReadingDatesDialog.ReadingDate, date: Long) {
        when (type) {
            SetTrackReadingDatesDialog.ReadingDate.Start -> controller.presenter.setTrackerStartDate(item, date)
            SetTrackReadingDatesDialog.ReadingDate.Finish -> controller.presenter.setTrackerFinishDate(item, date)
        }
    }

    private companion object {
        const val TAG_SEARCH_CONTROLLER = "track_search_controller"
    }
}