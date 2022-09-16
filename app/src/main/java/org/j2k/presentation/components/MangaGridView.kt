package org.j2k.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ripple.LocalRippleTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.data.database.models.Manga.DisplayManga
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import org.j2k.presentation.screens.PrimaryColorRippleTheme
import org.j2k.presentation.theme.Shapes
import org.j2k.presentation.theme.TachiColors

@Composable
fun MangaGridWithHeader(
    groupedManga: ImmutableMap<Int, ImmutableList<DisplayManga>>,
    shouldOutlineCover: Boolean,
    columns: Int,
    modifier: Modifier = Modifier,
    isComfortable: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(),
    onClick: (Long) -> Unit = {},
    onLongClick: (DisplayManga) -> Unit = {},
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier,
        contentPadding = contentPadding,
    ) {
        groupedManga.forEach { (stringRes, allGrids) ->
            stickyHeader {
                HeaderCard(stringResource(id = stringRes))
            }
            gridItems(
                items = allGrids,
                columns = columns,
                modifier = Modifier.padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) { displayManga ->
                MangaGridItem(
                    displayManga = displayManga,
                    shouldOutlineCover = shouldOutlineCover,
                    isComfortable = isComfortable,
                    onClick = { onClick(displayManga.mangaId) },
                    onLongClick = { onLongClick(displayManga) },
                )
            }
        }
    }
}

@Composable
private fun MangaGridItem(
    displayManga: DisplayManga,
    shouldOutlineCover: Boolean,
    isComfortable: Boolean = true,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
) {
    CompositionLocalProvider(LocalRippleTheme provides PrimaryColorRippleTheme) {
        Box {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(Shapes.coverRadius))
                    .combinedClickable(
                        onClick = onClick,
                        onLongClick = onLongClick,
                    )
                    .padding(2.dp),
            ) {
                if (isComfortable) {
                    Column {
                        ComfortableGridItem(
                            displayManga,
                            displayManga.displayText,
                            shouldOutlineCover,
                        )
                    }
                } else {
                    Box {
                        CompactGridItem(
                            displayManga,
                            displayManga.displayText,
                            shouldOutlineCover,
                        )
                    }
                }
            }

            if (displayManga.inLibrary) {
                val offset = (-2).dp
                InLibraryBadge(offset, shouldOutlineCover)
            }
        }
    }
}

@Composable
private fun ColumnScope.ComfortableGridItem(
    manga: DisplayManga,
    displayText: String,
    shouldOutlineCover: Boolean,
    modifier: Modifier = Modifier,
) {
    MangaCover.Book.invoke(
        manga = manga,
        shouldOutlineCover = shouldOutlineCover,
        modifier = modifier,
    )
    MangaTitle(
        title = manga.title,
        hasSubtitle = displayText.isNotBlank(),

    )

    DisplayText(displayText = displayText)
}

@Composable
private fun BoxScope.CompactGridItem(
    manga: DisplayManga,
    displayText: String,
    shouldOutlineCover: Boolean,
    modifier: Modifier = Modifier,
) {
    MangaCover.Book.invoke(
        manga = manga,
        shouldOutlineCover = shouldOutlineCover,
        modifier = modifier,
    )
    Box(
        modifier = Modifier
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.Black.copy(TachiColors.veryLowContrast),
                        Color.Black.copy(TachiColors.highAlphaLowContrast),
                    ),
                ),
                shape = RoundedCornerShape(
                    bottomStart = Shapes.coverRadius,
                    bottomEnd = Shapes.coverRadius,
                ),
            )
            .matchParentSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomStart),
        ) {
            MangaTitle(
                title = manga.title,
                hasSubtitle = displayText.isNotBlank(),
                isComfortable = false,
            )
            DisplayText(
                displayText = displayText,
                isComfortable = false,
            )
        }
    }
}

@Composable
private fun MangaTitle(
    title: String,
    maxLines: Int = 2,
    isComfortable: Boolean = true,
    hasSubtitle: Boolean = false,
) {
    Text(
        text = title,
        style = if (isComfortable) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
        color = if (isComfortable) MaterialTheme.colorScheme.onSurface else Color.White,
        fontWeight = FontWeight.Medium,
        fontSize = MaterialTheme.typography.titleSmall.fontSize,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(
            top = 4.dp,
            bottom = if (hasSubtitle) 0.dp else 4.dp,
            start = 6.dp,
            end = 6.dp,
        ),
    )
}

@Composable
private fun DisplayText(displayText: String, isComfortable: Boolean = true) {
    if (displayText.isNotBlank()) {
        Text(
            text = displayText,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            color = if (isComfortable) MaterialTheme.colorScheme.onSurface else Color.White,
            fontWeight = if (isComfortable) FontWeight.Normal else FontWeight.SemiBold,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(
                    top = 0.dp,
                    bottom = 4.dp,
                    start = 6.dp,
                    end = 6.dp,
                ),
        )
    }
}
