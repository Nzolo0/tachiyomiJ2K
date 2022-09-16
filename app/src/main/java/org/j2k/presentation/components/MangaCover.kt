package org.j2k.presentation.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.zedlabs.pastelplaceholder.Pastel
import eu.kanade.tachiyomi.data.database.models.Manga.DisplayManga
import eu.kanade.tachiyomi.data.image.coil.MangaCoverFetcher
import eu.kanade.tachiyomi.util.system.toMangaCacheKey
import eu.kanade.tachiyomi.util.toManga
import org.j2k.presentation.extensions.conditional
import org.j2k.presentation.theme.Outline
import org.j2k.presentation.theme.Shapes

/**
 * Taken from main app then modified
 * https://github.com/tachiyomiorg/tachiyomi/blob/cd3cb72b65d2f5f6ec038eb319717e21e3a1731e/app/src/main/java/eu/kanade/presentation/components/MangaCover.kt
 */

enum class MangaCover(private val ratio: Float) {
    Square(1f / 1f),
    Book(15f / 22f),
    ;

    @Composable
    operator fun invoke(
        modifier: Modifier = Modifier,
        manga: DisplayManga,
        contentDescription: String = "",
        shape: Shape = RoundedCornerShape(Shapes.coverRadius),
        shouldOutlineCover: Boolean = true,
    ) {
        val color by remember { mutableStateOf(Pastel.getColorLight()) }
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(manga.toManga())
                .memoryCacheKey(manga.mangaId.toMangaCacheKey())
                .placeholder(color)
                .setParameter(MangaCoverFetcher.useCustomCover, false)
                .build(),
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .aspectRatio(ratio)
                .clip(shape)
                .conditional(shouldOutlineCover) {
                    this.border(
                        width = Outline.thickness,
                        color = Outline.color,
                        shape = RoundedCornerShape(Shapes.coverRadius),
                    )
                },
        )
    }
}
