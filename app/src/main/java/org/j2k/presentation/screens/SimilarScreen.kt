package org.j2k.presentation.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.SwipeRefreshIndicator
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import com.google.android.material.snackbar.Snackbar
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Manga.DisplayManga
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.manga.similar.SimilarController
import eu.kanade.tachiyomi.ui.manga.similar.SimilarScreenState
import eu.kanade.tachiyomi.util.addOrRemoveToFavorites
import eu.kanade.tachiyomi.util.toManga
import eu.kanade.tachiyomi.util.view.snack
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import org.j2k.presentation.components.ListGridActionButton
import org.j2k.presentation.components.MangaGridWithHeader
import org.j2k.presentation.components.MangaListWithHeader
import org.j2k.presentation.components.TachiScaffold
import org.j2k.presentation.functions.numberOfColumns
import uy.kohesive.injekt.injectLazy

@Composable
fun SimilarScreen(
    controller: SimilarController,
    similarScreenState: State<SimilarScreenState>,
    switchDisplayClick: () -> Unit,
    onBackPress: () -> Unit,
    mangaClick: (Long) -> Unit,
    onRefresh: () -> Unit,
) {
    val presenter = controller.presenter
    var snack: Snackbar? = null
    val sourceManager: SourceManager by injectLazy()

    val scope = rememberCoroutineScope()

    TachiScaffold(
        title = stringResource(id = R.string.similar),
        onNavigationIconClicked = onBackPress,
        actions = {
            ListGridActionButton(
                isList = similarScreenState.value.isList,
                buttonClicked = switchDisplayClick,
            )
        },
    ) { incomingPaddingValues ->
        SwipeRefresh(
            state = rememberSwipeRefreshState(similarScreenState.value.isRefreshing),
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
            indicator = { state, trigger ->
                SwipeRefreshIndicator(
                    state = state,
                    refreshTriggerDistance = trigger,
                    refreshingOffset = (incomingPaddingValues.calculateTopPadding() * 2),
                    backgroundColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                )
            },
        ) {
            val haptic = LocalHapticFeedback.current

            SimilarContent(
                similarScreenState = similarScreenState,
                paddingValues = incomingPaddingValues,
                refreshing = onRefresh,
                mangaClick = mangaClick,
                mangaLongClick = { displayManga: DisplayManga ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    val manga = displayManga.toManga()
                    scope.launch {
                        snack?.dismiss()
                        snack = manga.addOrRemoveToFavorites(
                            presenter.db,
                            presenter.preferences,
                            controller.view!!,
                            controller.activity!!,
                            sourceManager,
                            controller,
                            onMangaAdded = {
                                presenter.updateDisplayManga(manga.id!!, true)
                                snack = controller.view!!.snack(R.string.added_to_library)
                            },
                            onMangaMoved = { presenter.updateDisplayManga(manga.id!!, manga.favorite) },
                            onMangaDeleted = {
                                presenter.updateDisplayManga(manga.id!!, false)
                                presenter.confirmDeletion(manga)
                            },
                        )
                        if (snack?.duration == Snackbar.LENGTH_INDEFINITE) {
                            (controller.activity as? MainActivity)?.setUndoSnackBar(snack)
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun SimilarContent(
    similarScreenState: State<SimilarScreenState>,
    paddingValues: PaddingValues = PaddingValues(),
    refreshing: () -> Unit,
    mangaClick: (Long) -> Unit,
    mangaLongClick: (DisplayManga) -> Unit,
) {
    if (!similarScreenState.value.isRefreshing) {
        if (similarScreenState.value.displayManga.isEmpty()) {
            EmptyScreen(
                iconicImage = CommunityMaterial.Icon.cmd_compass_off,
                iconSize = 176.dp,
                message = stringResource(id = R.string.no_results_found),
                actions = persistentListOf(Action(R.string.retry, refreshing)),
            )
        } else {
            val contentPadding = PaddingValues(
                bottom = WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)
                    .asPaddingValues().calculateBottomPadding(),
                top = paddingValues.calculateTopPadding(),
            )

            if (similarScreenState.value.isList) {
                MangaListWithHeader(
                    groupedManga = similarScreenState.value.displayManga,
                    shouldOutlineCover = similarScreenState.value.outlineCovers,
                    contentPadding = contentPadding,
                    onClick = mangaClick,
                    onLongClick = mangaLongClick,
                )
            } else {
                MangaGridWithHeader(
                    groupedManga = similarScreenState.value.displayManga,
                    shouldOutlineCover = similarScreenState.value.outlineCovers,
                    columns = numberOfColumns(rawValue = similarScreenState.value.rawColumnCount),
                    isComfortable = similarScreenState.value.isComfortableGrid,
                    contentPadding = contentPadding,
                    onClick = mangaClick,
                    onLongClick = mangaLongClick,
                )
            }
        }
    }
}
