package eu.kanade.tachiyomi.ui.manga.similar

import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.data.database.models.Manga.DisplayManga
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf

data class SimilarScreenState(
    val isRefreshing: Boolean = false,
    val displayManga: ImmutableMap<Int, ImmutableList<DisplayManga>> = persistentMapOf(),
    val error: String? = null,
    val isList: Boolean,
    val outlineCovers: Boolean,
    val isComfortableGrid: Boolean,
    val rawColumnCount: Float,
    val promptForCategories: Boolean,
    val categories: ImmutableList<Category> = persistentListOf(),
)
