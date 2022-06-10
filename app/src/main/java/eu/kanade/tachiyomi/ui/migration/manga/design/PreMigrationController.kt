package eu.kanade.tachiyomi.ui.migration.manga.design

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.forEach
import androidx.core.view.marginBottom
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePaddingRelative
import androidx.recyclerview.widget.LinearLayoutManager
import com.bluelinelabs.conductor.Router
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.databinding.PreMigrationControllerBinding
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.base.SmallToolbarInterface
import eu.kanade.tachiyomi.ui.base.controller.BaseController
import eu.kanade.tachiyomi.ui.migration.manga.process.MigrationListController
import eu.kanade.tachiyomi.ui.migration.manga.process.MigrationProcedureConfig
import eu.kanade.tachiyomi.util.system.LocaleHelper
import eu.kanade.tachiyomi.util.view.doOnApplyWindowInsetsCompat
import eu.kanade.tachiyomi.util.view.expand
import eu.kanade.tachiyomi.util.view.liftAppbarWith
import eu.kanade.tachiyomi.util.view.withFadeTransaction
import uy.kohesive.injekt.injectLazy

class PreMigrationController(bundle: Bundle? = null) :
    BaseController<PreMigrationControllerBinding>(bundle),
    FlexibleAdapter.OnItemClickListener,
    SmallToolbarInterface,
    StartMigrationListener {
    private val sourceManager: SourceManager by injectLazy()
    private val prefs: PreferencesHelper by injectLazy()

    private var adapter: MigrationSourceAdapter? = null

    private val config: LongArray = args.getLongArray(MANGA_IDS_EXTRA) ?: LongArray(0)

    private var showingOptions = false

    private var dialog: BottomSheetDialog? = null

    private var onlyEnabledSources = prefs.onlyEnabledSources().get()
    private var onlyPinnedSources = prefs.onlyPinnedSources().get()
    private val hiddenSources = prefs.hiddenSources().get().mapNotNull { it.toLongOrNull() }
    private val pinnedSources = prefs.pinnedCatalogues().get().mapNotNull { it.toLongOrNull() }
    private var enabledLanguages = prefs.enabledLanguages().get().minus("all").toMutableSet()

    override fun getTitle() = view?.context?.getString(R.string.select_sources)

    override fun createBinding(inflater: LayoutInflater) = PreMigrationControllerBinding.inflate(inflater)
    override fun onViewCreated(view: View) {
        super.onViewCreated(view)
        liftAppbarWith(binding.recycler)

        val ourAdapter = adapter ?: MigrationSourceAdapter(
            getEnabledSources().map { MigrationSourceItem(it, isEnabled(it.id)) },
            this,
        )
        adapter = ourAdapter
        binding.recycler.layoutManager = LinearLayoutManager(view.context)
        binding.recycler.setHasFixedSize(true)
        binding.recycler.adapter = ourAdapter
        ourAdapter.itemTouchHelperCallback = null // Reset adapter touch adapter to fix drag after rotation
        ourAdapter.isHandleDragEnabled = true
        dialog = null
        val fabBaseMarginBottom = binding.fab.marginBottom
        binding.recycler.doOnApplyWindowInsetsCompat { v, insets, _ ->

            binding.fab.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = fabBaseMarginBottom + insets.getInsets(systemBars()).bottom
            }
            v.post {
                // offset the binding.recycler by the binding.fab's inset + some inset on top
                v.updatePaddingRelative(
                    bottom = insets.getInsets(systemBars()).bottom + binding.fab.marginBottom +
                        (binding.fab.height),
                )
            }
        }

        binding.fab.setOnClickListener {
            if (dialog?.isShowing != true) {
                dialog = MigrationBottomSheetDialog(activity!!, this)
                dialog?.show()
                val bottomSheet = dialog?.findViewById<FrameLayout>(
                    com.google.android.material.R.id.design_bottom_sheet,
                )
                if (bottomSheet != null) {
                    val behavior: BottomSheetBehavior<*> = BottomSheetBehavior.from(bottomSheet)
                    behavior.expand()
                    behavior.skipCollapsed = true
                }
            }
        }
    }

    override fun startMigration(extraParam: String?) {
        val enabledSources = adapter?.items?.filter { it.sourceEnabled } ?: emptyList()
        prefs.migrationSources().set(enabledSources.joinToString("/") { it.source.id.toString() })
        prefs.onlyEnabledSources().set(onlyEnabledSources && enabledSources.none { it.source.id in hiddenSources })
        prefs.onlyPinnedSources().set(onlyPinnedSources && enabledSources.none { it.source.id !in pinnedSources })

        router.replaceTopController(
            MigrationListController.create(
                MigrationProcedureConfig(
                    config.toList(),
                    extraSearchParams = extraParam,
                ),
            ).withFadeTransaction().tag(MigrationListController.TAG),
        )
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        adapter?.onSaveInstanceState(outState)
    }

    // TODO Still incorrect, why is this called before onViewCreated?
    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        adapter?.onRestoreInstanceState(savedInstanceState)
    }

    override fun onItemClick(view: View, position: Int): Boolean {
        adapter?.getItem(position)?.let {
            it.sourceEnabled = !it.sourceEnabled
        }
        adapter?.notifyItemChanged(position)
        return false
    }

    /**
     * Returns a list of enabled sources ordered by language and name.
     *
     * @return list containing enabled sources.
     */
    private fun getEnabledSources(): List<HttpSource> {
        val sourcesSaved = prefs.migrationSources().get().split("/")
        val sources = sourceManager.getCatalogueSources()
            .filterIsInstance<HttpSource>()
            .filter { it.lang in enabledLanguages }
            .sortedBy { "(${it.lang}) ${it.name}" }

        return sources.filter { isEnabled(it.id) }
            .sortedBy { sourcesSaved.indexOf(it.id.toString()) } +
            sources.filterNot { isEnabled(it.id) }
    }

    fun isEnabled(id: Long): Boolean {
        val sourcesSaved = prefs.migrationSources().get()
        return if (sourcesSaved.isEmpty()) {
            id !in hiddenSources
        } else {
            sourcesSaved.split("/").contains(id.toString())
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.pre_migration, menu)
        val langItem = menu.findItem(R.id.action_lang)
        val subMenu = langItem.subMenu
        langItem.isVisible = enabledLanguages.size > 1
        enabledLanguages.forEachIndexed { index, lang ->
            subMenu?.add(
                R.id.action_lang_group,
                index,
                Menu.NONE,
                LocaleHelper.getSourceDisplayName(lang, prefs.context),
            )?.titleCondensed = lang
        }

        langItem.setOnMenuItemClickListener {
            onPrepareOptionsMenu(menu)
            true
        }
        subMenu?.setGroupCheckable(R.id.action_lang_group, true, false)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)

        val enabledItems = adapter?.currentItems?.filter { it.sourceEnabled } ?: return
        val langItem = menu.findItem(R.id.action_lang)
        langItem.subMenu?.forEach { item ->
            item.isChecked = enabledItems.any { it.source.lang == item.titleCondensed }
            enabledLanguages.apply {
                if (item.isChecked) add(item.titleCondensed.toString()) else remove(item.titleCondensed)
            }
        }
        menu.findItem(R.id.action_only_enabled).isChecked =
            onlyEnabledSources && enabledItems.none { it.source.id in hiddenSources }
        menu.findItem(R.id.action_only_pinned).isChecked =
            onlyPinnedSources && enabledItems.none { it.source.id !in pinnedSources }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_select_all -> {
                val items = adapter?.currentItems?.apply {
                    val shouldSelect = any { !it.sourceEnabled }
                    forEach { it.sourceEnabled = shouldSelect }
                }
                items.updateDataSetForSelect()
            }
            R.id.action_select_inverse -> {
                val items = adapter?.currentItems?.onEach { it.sourceEnabled = !it.sourceEnabled }
                items.updateDataSetForSelect()
            }
            R.id.action_only_enabled, R.id.action_only_pinned -> {
                item.isChecked = !item.isChecked
                if (item.itemId == R.id.action_only_enabled) {
                    onlyEnabledSources = item.isChecked
                } else {
                    onlyPinnedSources = item.isChecked
                }
                return item.updateEnabledSources(true)
            }
            else -> return when (item.groupId) {
                R.id.action_lang_group -> {
                    item.isChecked = !item.isChecked
                    enabledLanguages.apply {
                        if (item.isChecked) add(item.titleCondensed.toString()) else remove(item.titleCondensed)
                    }
                    item.updateEnabledSources()
                }
                else -> super.onOptionsItemSelected(item)
            }
        }
        return true
    }

    private fun List<MigrationSourceItem>?.updateDataSetForSelect() {
        onlyEnabledSources = false
        onlyPinnedSources = false
        val sortedItems = this?.sortedBy { it.source.name }?.sortedBy { !it.sourceEnabled }
        adapter?.updateDataSet(sortedItems)
    }

    private fun MenuItem.updateEnabledSources(enabledOrPinned: Boolean = false): Boolean {
        val items = adapter?.currentItems?.toList() ?: return true
        items.forEach {
            if (it.source.lang == titleCondensed || enabledOrPinned) {
                val isCheckLanguage = it.source.lang in enabledLanguages
                val isCheckEnabled = !onlyEnabledSources || it.source.id !in hiddenSources
                val isCheckPinned = !onlyPinnedSources || it.source.id in pinnedSources
                it.sourceEnabled = isCheckLanguage && isCheckEnabled && isCheckPinned
            }
        }
        val sortedItems = items.sortedBy { it.source.name }.sortedBy { !it.sourceEnabled }

        adapter?.updateDataSet(sortedItems)
        return fixCollapsedMenu()
    }

    private fun MenuItem.fixCollapsedMenu(): Boolean {
        setShowAsAction(MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW)
        actionView = View(prefs.context)
        setOnActionExpandListener(
            object : MenuItem.OnActionExpandListener {
                override fun onMenuItemActionExpand(item: MenuItem): Boolean = false
                override fun onMenuItemActionCollapse(item: MenuItem): Boolean = false
            },
        )
        return false
    }

    companion object {
        private const val MANGA_IDS_EXTRA = "manga_ids"

        fun navigateToMigration(skipPre: Boolean, router: Router, mangaIds: List<Long>) {
            router.pushController(
                if (skipPre) {
                    MigrationListController.create(
                        MigrationProcedureConfig(mangaIds, null),
                    )
                } else {
                    create(mangaIds)
                }.withFadeTransaction().tag(if (skipPre) MigrationListController.TAG else null),
            )
        }

        fun create(mangaIds: List<Long>): PreMigrationController {
            return PreMigrationController(
                Bundle().apply {
                    putLongArray(MANGA_IDS_EXTRA, mangaIds.toLongArray())
                },
            )
        }
    }
}
