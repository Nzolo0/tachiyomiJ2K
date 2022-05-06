package eu.kanade.tachiyomi.ui.setting

import android.app.Activity
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import androidx.core.net.toUri
import androidx.core.text.buildSpannedString
import androidx.preference.PreferenceScreen
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hippo.unifile.UniFile
import eu.kanade.domain.category.interactor.GetCategories
import eu.kanade.domain.download.service.DownloadPreferences
import eu.kanade.presentation.category.visualName
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.DEVICE_BATTERY_NOT_LOW
import eu.kanade.tachiyomi.data.preference.DEVICE_CHARGING
import eu.kanade.tachiyomi.data.preference.DEVICE_ONLY_ON_WIFI
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import eu.kanade.tachiyomi.util.preference.bindTo
import eu.kanade.tachiyomi.util.preference.entriesRes
import eu.kanade.tachiyomi.util.preference.infoPreference
import eu.kanade.tachiyomi.util.preference.intListPreference
import eu.kanade.tachiyomi.util.preference.multiSelectListPreference
import eu.kanade.tachiyomi.util.preference.onClick
import eu.kanade.tachiyomi.util.preference.preference
import eu.kanade.tachiyomi.util.preference.preferenceCategory
import eu.kanade.tachiyomi.util.preference.summaryRes
import eu.kanade.tachiyomi.util.preference.switchPreference
import eu.kanade.tachiyomi.util.preference.titleRes
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.widget.materialdialogs.QuadStateTextView
import eu.kanade.tachiyomi.widget.materialdialogs.setQuadStateMultiChoiceItems
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.io.File

class SettingsDownloadController : SettingsController() {

    private val getCategories: GetCategories by injectLazy()
    private val downloadPreferences: DownloadPreferences by injectLazy()

    override fun setupPreferenceScreen(screen: PreferenceScreen) = screen.apply {
        titleRes = R.string.pref_category_downloads

        val categories = runBlocking { getCategories.await() }

        preference {
            bindTo(downloadPreferences.downloadsDirectory())
            titleRes = R.string.pref_download_directory
            onClick {
                val ctrl = DownloadDirectoriesDialog()
                ctrl.targetController = this@SettingsDownloadController
                ctrl.showDialog(router)
            }

            downloadPreferences.downloadsDirectory().changes()
                .onEach { path ->
                    val dir = UniFile.fromUri(context, path.toUri())
                    summary = dir.filePath ?: path
                }
                .launchIn(viewScope)
        }
        switchPreference {
            bindTo(downloadPreferences.downloadOnlyOverWifi())
            titleRes = R.string.connected_to_wifi
        }
        switchPreference {
            bindTo(downloadPreferences.saveChaptersAsCBZ())
            titleRes = R.string.save_chapter_as_cbz
        }
        switchPreference {
            bindTo(downloadPreferences.splitTallImages())
            titleRes = R.string.split_tall_images
            summaryRes = R.string.split_tall_images_summary
        }

        preferenceCategory {
            titleRes = R.string.pref_category_delete_chapters

            switchPreference {
                bindTo(downloadPreferences.removeAfterMarkedAsRead())
                titleRes = R.string.pref_remove_after_marked_as_read
            }
            intListPreference {
                bindTo(downloadPreferences.removeAfterReadSlots())
                titleRes = R.string.pref_remove_after_read
                entriesRes = arrayOf(
                    R.string.disabled,
                    R.string.last_read_chapter,
                    R.string.second_to_last,
                    R.string.third_to_last,
                    R.string.fourth_to_last,
                    R.string.fifth_to_last,
                )
                entryValues = arrayOf("-1", "0", "1", "2", "3", "4")
                summary = "%s"
            }
            switchPreference {
                bindTo(downloadPreferences.removeBookmarkedChapters())
                titleRes = R.string.pref_remove_bookmarked_chapters
            }
            multiSelectListPreference {
                bindTo(downloadPreferences.removeExcludeCategories())
                titleRes = R.string.pref_remove_exclude_categories
                entries = categories.map { it.visualName(context) }.toTypedArray()
                entryValues = categories.map { it.id.toString() }.toTypedArray()

                downloadPreferences.removeExcludeCategories().changes()
                    .onEach { mutable ->
                        val selected = mutable
                            .mapNotNull { id -> categories.find { it.id == id.toLong() } }
                            .sortedBy { it.order }

                        summary = if (selected.isEmpty()) {
                            resources?.getString(R.string.none)
                        } else {
                            selected.joinToString { it.visualName(context) }
                        }
                    }.launchIn(viewScope)
            }
        }

        preferenceCategory {
            titleRes = R.string.pref_download_new

            intListPreference {
                bindTo(downloadPreferences.downloadNewChapters())
                titleRes = R.string.pref_download_new
                entries = arrayOf(
                    context.getString(R.string.disabled),
                    context.resources.getQuantityString(R.plurals.download_new_chapters_limit, 1, 1),
                    context.resources.getQuantityString(R.plurals.download_new_chapters_limit, 2, 2),
                    context.resources.getQuantityString(R.plurals.download_new_chapters_limit, 3, 3),
                    context.resources.getQuantityString(R.plurals.download_new_chapters_limit, 5, 5),
                    context.resources.getQuantityString(R.plurals.download_new_chapters_limit, 10, 10),
                    context.getString(R.string.all_new_chapters),
                )
                entryValues = arrayOf("0", "1", "2", "3", "5", "10", "-1")
                summary = "%s"
            }
            preference {
                bindTo(downloadPreferences.downloadNewChapterCategories())
                titleRes = R.string.categories
                onClick {
                    DownloadCategoriesDialog().showDialog(router)
                }

                visibleIf(downloadPreferences.downloadNewChapters()) { it != 0 }

                fun updateSummary() {
                    val selectedCategories = downloadPreferences.downloadNewChapterCategories().get()
                        .mapNotNull { id -> categories.find { it.id == id.toLong() } }
                        .sortedBy { it.order }
                    val includedItemsText = if (selectedCategories.isEmpty()) {
                        context.getString(R.string.all)
                    } else {
                        selectedCategories.joinToString { it.visualName(context) }
                    }

                    val excludedCategories = downloadPreferences.downloadNewChapterCategoriesExclude().get()
                        .mapNotNull { id -> categories.find { it.id == id.toLong() } }
                        .sortedBy { it.order }
                    val excludedItemsText = if (excludedCategories.isEmpty()) {
                        context.getString(R.string.none)
                    } else {
                        excludedCategories.joinToString { it.visualName(context) }
                    }

                    summary = buildSpannedString {
                        append(context.getString(R.string.include, includedItemsText))
                        appendLine()
                        append(context.getString(R.string.exclude, excludedItemsText))
                    }
                }

                downloadPreferences.downloadNewChapterCategories().changes()
                    .onEach { updateSummary() }
                    .launchIn(viewScope)
                downloadPreferences.downloadNewChapterCategoriesExclude().changes()
                    .onEach { updateSummary() }
                    .launchIn(viewScope)
            }
            multiSelectListPreference {
                bindTo(downloadPreferences.downloadNewDeviceRestriction())
                titleRes = R.string.pref_download_new_restriction
                entriesRes = arrayOf(R.string.connected_to_wifi, R.string.charging, R.string.battery_not_low)
                entryValues = arrayOf(DEVICE_ONLY_ON_WIFI, DEVICE_CHARGING, DEVICE_BATTERY_NOT_LOW)

                visibleIf(downloadPreferences.downloadNewChapters()) { it != 0 }

                fun updateSummary() {
                    val restrictions = downloadPreferences.downloadNewDeviceRestriction().get()
                        .sorted()
                        .map {
                            when (it) {
                                DEVICE_ONLY_ON_WIFI -> context.getString(R.string.connected_to_wifi)
                                DEVICE_CHARGING -> context.getString(R.string.charging)
                                DEVICE_BATTERY_NOT_LOW -> context.getString(R.string.battery_not_low)
                                else -> it
                            }
                        }
                    val restrictionsText = if (restrictions.isEmpty()) {
                        context.getString(R.string.none)
                    } else {
                        restrictions.joinToString()
                    }

                    summary = context.getString(R.string.restrictions, restrictionsText)
                }

                downloadPreferences.downloadNewDeviceRestriction().changes()
                    .onEach { updateSummary() }
                    .launchIn(viewScope)
            }
            switchPreference {
                bindTo(downloadPreferences.downloadNewSkipUnread())
                titleRes = R.string.pref_download_new_skip_unread

                visibleIf(downloadPreferences.downloadNewChapters()) { it != 0 }
            }
        }

        preferenceCategory {
            titleRes = R.string.download_ahead

            intListPreference {
                bindTo(downloadPreferences.autoDownloadWhileReading())
                titleRes = R.string.auto_download_while_reading
                entries = arrayOf(
                    context.getString(R.string.disabled),
                    context.resources.getQuantityString(R.plurals.next_unread_chapters, 2, 2),
                    context.resources.getQuantityString(R.plurals.next_unread_chapters, 3, 3),
                    context.resources.getQuantityString(R.plurals.next_unread_chapters, 5, 5),
                    context.resources.getQuantityString(R.plurals.next_unread_chapters, 10, 10),
                )
                entryValues = arrayOf("0", "2", "3", "5", "10")
                summary = "%s"
            }
            infoPreference(R.string.download_ahead_info)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            DOWNLOAD_DIR -> if (data != null && resultCode == Activity.RESULT_OK) {
                val context = applicationContext ?: return
                val uri = data.data
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION

                if (uri != null) {
                    @Suppress("NewApi")
                    context.contentResolver.takePersistableUriPermission(uri, flags)
                }

                val file = UniFile.fromUri(context, uri)
                downloadPreferences.downloadsDirectory().set(file.uri.toString())
            }
        }
    }

    fun predefinedDirectorySelected(selectedDir: String) {
        val path = File(selectedDir).toUri()
        downloadPreferences.downloadsDirectory().set(path.toString())
    }

    fun customDirectorySelected() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        try {
            startActivityForResult(intent, DOWNLOAD_DIR)
        } catch (e: ActivityNotFoundException) {
            activity?.toast(R.string.file_picker_error)
        }
    }

    class DownloadDirectoriesDialog : DialogController() {

        private val downloadPreferences: DownloadPreferences = Injekt.get()

        override fun onCreateDialog(savedViewState: Bundle?): Dialog {
            val activity = activity!!
            val currentDir = downloadPreferences.downloadsDirectory().get()
            val externalDirs = listOf(getDefaultDownloadDir(), File(activity.getString(R.string.custom_dir))).map(File::toString)
            var selectedIndex = externalDirs.indexOfFirst { it in currentDir }

            return MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.pref_download_directory)
                .setSingleChoiceItems(externalDirs.toTypedArray(), selectedIndex) { _, which ->
                    selectedIndex = which
                }
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    val target = targetController as? SettingsDownloadController
                    if (selectedIndex == externalDirs.lastIndex) {
                        target?.customDirectorySelected()
                    } else {
                        target?.predefinedDirectorySelected(externalDirs[selectedIndex])
                    }
                }
                .create()
        }

        private fun getDefaultDownloadDir(): File {
            val defaultDir = Environment.getExternalStorageDirectory().absolutePath +
                File.separator + resources?.getString(R.string.app_name) +
                File.separator + "downloads"

            return File(defaultDir)
        }
    }

    class DownloadCategoriesDialog : DialogController() {

        private val downloadPreferences: DownloadPreferences = Injekt.get()
        private val getCategories: GetCategories = Injekt.get()

        override fun onCreateDialog(savedViewState: Bundle?): Dialog {
            val categories = runBlocking { getCategories.await() }

            val items = categories.map { it.visualName(activity!!) }
            var selected = categories
                .map {
                    when (it.id.toString()) {
                        in downloadPreferences.downloadNewChapterCategories().get() -> QuadStateTextView.State.CHECKED.ordinal
                        in downloadPreferences.downloadNewChapterCategoriesExclude().get() -> QuadStateTextView.State.INVERSED.ordinal
                        else -> QuadStateTextView.State.UNCHECKED.ordinal
                    }
                }
                .toIntArray()

            return MaterialAlertDialogBuilder(activity!!)
                .setTitle(R.string.categories)
                .setQuadStateMultiChoiceItems(
                    message = R.string.pref_download_new_categories_details,
                    items = items,
                    initialSelected = selected,
                ) { selections ->
                    selected = selections
                }
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    val included = selected
                        .mapIndexed { index, value -> if (value == QuadStateTextView.State.CHECKED.ordinal) index else null }
                        .filterNotNull()
                        .map { categories[it].id.toString() }
                        .toSet()
                    val excluded = selected
                        .mapIndexed { index, value -> if (value == QuadStateTextView.State.INVERSED.ordinal) index else null }
                        .filterNotNull()
                        .map { categories[it].id.toString() }
                        .toSet()

                    downloadPreferences.downloadNewChapterCategories().set(included)
                    downloadPreferences.downloadNewChapterCategoriesExclude().set(excluded)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .create()
        }
    }
}

private const val DOWNLOAD_DIR = 104
