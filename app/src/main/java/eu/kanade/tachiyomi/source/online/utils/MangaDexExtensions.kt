package eu.kanade.tachiyomi.source.online.utils

import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.models.dto.MangaDataDto

fun MangaDataDto.toBasicManga(coverQuality: Int = 0, useNoCoverUrl: Boolean = true): SManga {
    return SManga.create().apply {
        url = "/manga/" + this@toBasicManga.id
        title = MdUtil.cleanString(
            MdUtil.getTitle(
                this@toBasicManga.attributes.title,
                this@toBasicManga.attributes.originalLanguage,
            ),
        )

        thumbnail_url = this@toBasicManga.relationships
            .firstOrNull { relationshipDto -> relationshipDto.type == MdConstants.Types.coverArt }
            ?.attributes?.fileName
            ?.let { coverFileName ->
                MdUtil.cdnCoverUrl(this@toBasicManga.id, coverFileName, coverQuality)
            } ?: if (useNoCoverUrl) MdConstants.noCoverUrl else null
    }
}
