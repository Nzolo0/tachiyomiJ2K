package eu.kanade.tachiyomi.ui.setting.database

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.databinding.BrowseSourceControllerBinding
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.ui.base.controller.BaseCoroutineController
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.manga.MangaDetailsController
import eu.kanade.tachiyomi.ui.source.browse.BrowseSourceItem
import eu.kanade.tachiyomi.util.addOrRemoveToFavorites
import eu.kanade.tachiyomi.util.lang.asButton
import eu.kanade.tachiyomi.util.system.materialAlertDialog
import eu.kanade.tachiyomi.util.system.setCustomTitleAndMessage
import eu.kanade.tachiyomi.util.view.activityBinding
import eu.kanade.tachiyomi.util.view.fullAppBarHeight
import eu.kanade.tachiyomi.util.view.inflate
import eu.kanade.tachiyomi.util.view.scrollViewWith
import eu.kanade.tachiyomi.util.view.snack
import eu.kanade.tachiyomi.util.view.withFadeTransaction
import eu.kanade.tachiyomi.widget.AutofitRecyclerView
import eu.kanade.tachiyomi.widget.LinearLayoutManagerAccurateOffset
import kotlin.math.roundToInt

/**
 * Controller to manage the catalogues available in the app.
 */
open class BrowseClearSourceController(bundle: Bundle) :
    BaseCoroutineController<BrowseSourceControllerBinding, BrowseClearSourcePresenter>(bundle),
    FlexibleAdapter.OnItemClickListener,
    FlexibleAdapter.OnItemLongClickListener {

    constructor(source: Source) : this(
        Bundle().apply {
            putLong(SOURCE_ID_KEY, source.id)
        },
    )

    /**
     * Adapter containing the list of manga from the catalogue.
     */
    private var adapter: FlexibleAdapter<IFlexible<*>>? = null

    /**
     * Snackbar containing an error message when a request fails.
     */
    private var snack: Snackbar? = null

    /**
     * Recycler view with the list of results.
     */
    private var recycler: RecyclerView? = null

    init {
        setHasOptionsMenu(true)
    }

    override val mainRecycler: RecyclerView?
        get() = recycler

    override fun getTitle(): String? {
        return presenter.source.name
    }

    override val presenter = BrowseClearSourcePresenter(args.getLong(SOURCE_ID_KEY))

    override fun createBinding(inflater: LayoutInflater) = BrowseSourceControllerBinding.inflate(inflater)

    override fun onViewCreated(view: View) {
        super.onViewCreated(view)
        binding.fab.isVisible = false
        showProgressBar()
        presenter.initializeItems()

        activityBinding?.appBar?.y = 0f
        activityBinding?.appBar?.updateAppBarAfterY(recycler)
        activityBinding?.appBar?.lockYPos = true
    }

    fun updateAdapter(items: List<BrowseSourceItem>) {
        // Initialize adapter, scroll listener and recycler views
        adapter = FlexibleAdapter(items, this)
        setupRecycler()
        hideProgressBar()
    }

    override fun onDestroyView(view: View) {
        adapter = null
        snack = null
        recycler = null
        super.onDestroyView(view)
    }

    private fun setupRecycler() {
        var oldPosition = RecyclerView.NO_POSITION
        var oldOffset = 0f
        val oldRecycler = binding.catalogueView.getChildAt(1)
        if (oldRecycler is RecyclerView) {
            oldPosition = (oldRecycler.layoutManager as LinearLayoutManager).findFirstCompletelyVisibleItemPosition()
                .takeIf { it != RecyclerView.NO_POSITION }
                ?: (oldRecycler.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()
            oldOffset =
                oldRecycler.layoutManager?.findViewByPosition(oldPosition)?.y?.minus(oldRecycler.paddingTop) ?: 0f
            oldRecycler.adapter = null

            binding.catalogueView.removeView(oldRecycler)
        }

        val recycler = if (presenter.prefs.browseAsList().get()) {
            RecyclerView(activity!!).apply {
                id = R.id.recycler
                layoutManager = LinearLayoutManagerAccurateOffset(context)
                layoutParams =
                    RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
            }
        } else {
            (binding.catalogueView.inflate(R.layout.manga_recycler_autofit) as AutofitRecyclerView).apply {
                setGridSize(presenter.prefs)

                (layoutManager as androidx.recyclerview.widget.GridLayoutManager).spanSizeLookup =
                    object : androidx.recyclerview.widget.GridLayoutManager.SpanSizeLookup() {
                        override fun getSpanSize(position: Int): Int {
                            return when (adapter?.getItemViewType(position)) {
                                R.layout.manga_grid_item, null -> 1
                                else -> spanCount
                            }
                        }
                    }
            }
        }
        recycler.clipToPadding = false
        recycler.setHasFixedSize(true)
        recycler.adapter = adapter

        binding.catalogueView.addView(recycler, 1)
        scrollViewWith(
            recycler,
            true,
            afterInsets = { insets ->
                val bigToolbarHeight = fullAppBarHeight ?: 0
                binding.progress.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    topMargin = (bigToolbarHeight + insets.getInsets(systemBars()).top) / 2
                }
                binding.emptyView.updatePadding(
                    top = (bigToolbarHeight + insets.getInsets(systemBars()).top),
                    bottom = insets.getInsets(systemBars()).bottom,
                )
            },
        )

        if (oldPosition != RecyclerView.NO_POSITION) {
            (recycler.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(
                oldPosition,
                oldOffset.roundToInt(),
            )
            if (oldPosition > 0 && (activity as? MainActivity)?.currentToolbar != activityBinding?.searchToolbar) {
                activityBinding?.appBar?.useSearchToolbarForMenu(true)
            }
        }
        this.recycler = recycler
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.browse_source, menu)
        menu.findItem(R.id.action_search).isVisible = false
        menu.findItem(R.id.action_open_in_web_view).isVisible = false
        menu.findItem(R.id.action_local_source_help).isVisible = false
        // Show next display mode
        updateDisplayMenuItem(menu)
    }

    private fun updateDisplayMenuItem(menu: Menu?, isListMode: Boolean? = null) {
        menu?.findItem(R.id.action_display_mode)?.apply {
            val icon = if (isListMode ?: presenter.prefs.browseAsList().get()) {
                R.drawable.ic_view_module_24dp
            } else {
                R.drawable.ic_view_list_24dp
            }
            setIcon(icon)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_display_mode -> swapDisplayMode()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    /**
     * Swaps the current display mode.
     */
    private fun swapDisplayMode() {
        val isListMode = !presenter.prefs.browseAsList().get()
        presenter.prefs.browseAsList().set(isListMode)
        listOf(activityBinding?.toolbar?.menu, activityBinding?.searchToolbar?.menu).forEach {
            updateDisplayMenuItem(it, isListMode)
        }
        setupRecycler()
    }

    /**
     * Shows the binding.progress bar.
     */
    private fun showProgressBar() {
        binding.emptyView.isVisible = false
        binding.progress.isVisible = true
        snack?.dismiss()
        snack = null
    }

    /**
     * Hides active binding.progress bars.
     */
    private fun hideProgressBar() {
        binding.emptyView.isVisible = false
        binding.progress.isVisible = false
    }

    /**
     * Called when a manga is clicked.
     *
     * @param position the position of the element clicked.
     * @return true if the item should be selected, false otherwise.
     */
    override fun onItemClick(view: View?, position: Int): Boolean {
        val item = adapter?.getItem(position) as? BrowseSourceItem ?: return false
        router.pushController(MangaDetailsController(item.manga, true).withFadeTransaction())

        return false
    }

    override fun onItemLongClick(position: Int) {
        val manga = (adapter?.getItem(position) as? BrowseSourceItem?)?.manga ?: return
        snack?.dismiss()
        showAddOrClearDialog(manga, position)
    }

    private fun showAddOrClearDialog(manga: Manga, position: Int) {
        val libraryText = activity!!.getString(
            if (manga.favorite) {
                R.string.remove_from_library
            } else {
                R.string.add_to_library
            },
        )

        activity!!.materialAlertDialog().apply {
            setCustomTitleAndMessage(0, activity!!.getString(R.string.clear_title_data_confirmation, libraryText.lowercase()))
            setItems(
                arrayOf(
                    activity!!.getString(R.string.clear_title_data).asButton(activity!!),
                    libraryText.asButton(activity!!),
                ),
            ) { dialog, i ->
                when (i) {
                    0 -> showClearDialog(manga, position)
                    1 -> addManga(manga, position)
                    else -> {}
                }
                dialog.dismiss()
            }
            setNegativeButton(activity?.getString(android.R.string.cancel)) { _, _ -> }
        }.show()
    }

    private fun showClearDialog(manga: Manga, position: Int) {
        activity!!.materialAlertDialog()
            .setMessage(R.string.clear_database_confirmation)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                presenter.deleteManga(manga)
                adapter?.removeItem(position)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun addManga(manga: Manga, position: Int) {
        val view = view ?: return
        val activity = activity ?: return
        snack = manga.addOrRemoveToFavorites(
            presenter.db,
            presenter.prefs,
            view,
            activity,
            presenter.sourceManager,
            this,
            onMangaAdded = {
                adapter?.notifyItemChanged(position)
                snack = view.snack(R.string.added_to_library)
            },
            onMangaMoved = { adapter?.notifyItemChanged(position) },
            onMangaDeleted = { presenter.confirmDeletion(manga) },
        )
        if (snack?.duration == Snackbar.LENGTH_INDEFINITE) {
            (activity as? MainActivity)?.setUndoSnackBar(snack)
        }
    }

    companion object {
        const val SOURCE_ID_KEY = "sourceId"
    }
}
