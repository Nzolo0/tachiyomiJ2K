package eu.kanade.tachiyomi.ui.reader.chapter

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.adapters.ItemAdapter
import com.mikepenz.fastadapter.listeners.ClickEventHook
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.ReaderChaptersSheetBinding
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.reader.ReaderPresenter
import eu.kanade.tachiyomi.ui.reader.settings.TabbedReaderSettingsSheet
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.system.launchUI
import eu.kanade.tachiyomi.util.view.collapse
import eu.kanade.tachiyomi.util.view.expand
import eu.kanade.tachiyomi.util.view.gone
import eu.kanade.tachiyomi.util.view.invisible
import eu.kanade.tachiyomi.util.view.isCollapsed
import eu.kanade.tachiyomi.util.view.isExpanded
import eu.kanade.tachiyomi.util.view.visible
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class ReaderChapterSheet @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    LinearLayout(context, attrs) {

    var sheetBehavior: BottomSheetBehavior<View>? = null
    lateinit var presenter: ReaderPresenter
    var adapter: FastAdapter<ReaderChapterItem>? = null
    private val itemAdapter = ItemAdapter<ReaderChapterItem>()
    var selectedChapterId = -1L

    var loadingPos = 0
    lateinit var binding: ReaderChaptersSheetBinding

    override fun onFinishInflate() {
        super.onFinishInflate()
        binding = ReaderChaptersSheetBinding.bind(this)
    }

    fun setup(activity: ReaderActivity) {
        presenter = activity.presenter
        val fullPrimary = activity.getResourceColor(R.attr.colorSecondary)
        val primary = ColorUtils.setAlphaComponent(fullPrimary, 200)

        sheetBehavior = BottomSheetBehavior.from(this)
        binding.chaptersButton.setOnClickListener {
            if (sheetBehavior.isExpanded()) {
                sheetBehavior?.collapse()
            } else {
                sheetBehavior?.expand()
            }
        }

        binding.webviewButton.setOnClickListener {
            activity.openMangaInBrowser()
        }

        binding.displayOptions.setOnClickListener {
            TabbedReaderSettingsSheet(activity).show()
        }

        post {
            binding.chapterRecycler.alpha = if (sheetBehavior.isExpanded()) 1f else 0f
            binding.chapterRecycler.isClickable = sheetBehavior.isExpanded()
            binding.chapterRecycler.isFocusable = sheetBehavior.isExpanded()
            activity.binding.readerNav.root.isVisible = sheetBehavior.isCollapsed()
        }

        sheetBehavior?.addBottomSheetCallback(
            object : BottomSheetBehavior.BottomSheetCallback() {
                override fun onSlide(bottomSheet: View, progress: Float) {
                    binding.pill.alpha = (1 - max(0f, progress)) * 0.25f
                    val trueProgress = max(progress, 0f)
                    activity.binding.readerNav.root.alpha = (1 - abs(progress)).coerceIn(0f, 1f)
                    backgroundTintList =
                        ColorStateList.valueOf(lerpColor(primary, fullPrimary, trueProgress))
                    binding.chapterRecycler.alpha = trueProgress
                    if (activity.sheetManageNavColor && progress > 0f) {
                        activity.window.navigationBarColor =
                            lerpColor(ColorUtils.setAlphaComponent(primary, 0), primary, trueProgress)
                    }
                }

                override fun onStateChanged(p0: View, state: Int) {
                    if (state == BottomSheetBehavior.STATE_COLLAPSED) {
                        sheetBehavior?.isHideable = false
                        (binding.chapterRecycler.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(
                            adapter?.getPosition(presenter.getCurrentChapter()?.chapter?.id ?: 0L) ?: 0,
                            binding.chapterRecycler.height / 2 - 30.dpToPx
                        )
                        activity.binding.readerNav.root.visible()
                        activity.binding.readerNav.root.alpha = 1f
                    }
                    if (state == BottomSheetBehavior.STATE_DRAGGING || state == BottomSheetBehavior.STATE_SETTLING) {
                        activity.binding.readerNav.root.visible()
                    }
                    if (state == BottomSheetBehavior.STATE_EXPANDED) {
                        activity.binding.readerNav.root.invisible()
                        activity.binding.readerNav.root.alpha = 0f
                        binding.chapterRecycler.alpha = 1f
                        if (activity.sheetManageNavColor) activity.window.navigationBarColor = primary
                    }
                    if (state == BottomSheetBehavior.STATE_HIDDEN) {
                        activity.binding.readerNav.root.alpha = 0f
                        activity.binding.readerNav.root.invisible()
                    }
                    binding.chapterRecycler.isClickable = state == BottomSheetBehavior.STATE_EXPANDED
                    binding.chapterRecycler.isFocusable = state == BottomSheetBehavior.STATE_EXPANDED
                }
            }
        )

        adapter = FastAdapter.with(itemAdapter)
        binding.chapterRecycler.adapter = adapter
        adapter?.onClickListener = { _, _, item, position ->
            if (!sheetBehavior.isExpanded() || activity.isLoading) {
                false
            } else {
                if (item.chapter.id != presenter.getCurrentChapter()?.chapter?.id) {
                    activity.binding.readerNav.leftChapter.invisible()
                    activity.binding.readerNav.rightChapter.invisible()

                    presenter.loadChapter(item.chapter)
                    loadingPos = position
                    val itemView = (binding.chapterRecycler.findViewHolderForAdapterPosition(position) as? ReaderChapterItem.ViewHolder)?.binding
                    itemView?.bookmarkImage?.gone()
                    itemView?.progress?.visible()
                }
                true
            }
        }
        adapter?.addEventHook(
            object : ClickEventHook<ReaderChapterItem>() {
                override fun onBind(viewHolder: RecyclerView.ViewHolder): View? {
                    return if (viewHolder is ReaderChapterItem.ViewHolder) {
                        viewHolder.binding.bookmarkButton
                    } else {
                        null
                    }
                }

                override fun onClick(
                    v: View,
                    position: Int,
                    fastAdapter: FastAdapter<ReaderChapterItem>,
                    item: ReaderChapterItem
                ) {
                    if (!activity.isLoading && sheetBehavior.isExpanded()) {
                        presenter.toggleBookmark(item.chapter)
                        refreshList()
                    }
                }
            }
        )

        backgroundTintList = ColorStateList.valueOf(
            if (!sheetBehavior.isExpanded()) primary
            else fullPrimary
        )

        binding.chapterRecycler.layoutManager = LinearLayoutManager(context)
        refreshList()
    }

    fun resetChapter() {
        val itemView = (binding.chapterRecycler.findViewHolderForAdapterPosition(loadingPos) as? ReaderChapterItem.ViewHolder)?.binding
        itemView?.bookmarkImage?.visible()
        itemView?.progress?.gone()
    }

    fun refreshList() {
        launchUI {
            val chapters = presenter.getChapters()

            selectedChapterId = chapters.find { it.isCurrent }?.chapter?.id ?: -1L
            itemAdapter.clear()
            itemAdapter.add(chapters)

            (binding.chapterRecycler.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(
                adapter?.getPosition(presenter.getCurrentChapter()?.chapter?.id ?: 0L) ?: 0,
                binding.chapterRecycler.height / 2 - 30.dpToPx
            )
        }
    }

    fun lerpColor(colorStart: Int, colorEnd: Int, percent: Float): Int {
        val perc = (percent * 100).roundToInt()
        return Color.argb(
            lerpColorCalc(Color.alpha(colorStart), Color.alpha(colorEnd), perc),
            lerpColorCalc(Color.red(colorStart), Color.red(colorEnd), perc),
            lerpColorCalc(Color.green(colorStart), Color.green(colorEnd), perc),
            lerpColorCalc(Color.blue(colorStart), Color.blue(colorEnd), perc)
        )
    }

    fun lerpColorCalc(colorStart: Int, colorEnd: Int, percent: Int): Int {
        return (
            min(colorStart, colorEnd) * (100 - percent) + max(
                colorStart,
                colorEnd
            ) * percent
            ) / 100
    }
}