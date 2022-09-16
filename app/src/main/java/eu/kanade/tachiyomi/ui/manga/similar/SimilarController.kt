package eu.kanade.tachiyomi.ui.manga.similar

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.ui.base.controller.BaseComposeController
import eu.kanade.tachiyomi.ui.manga.MangaDetailsController
import eu.kanade.tachiyomi.util.view.withFadeTransaction
import org.j2k.presentation.screens.SimilarScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Controller that shows the similar/related manga
 */
class SimilarController(manga: Manga?) : BaseComposeController<SimilarPresenter>() {

    constructor(mangaId: Long) : this(
        Injekt.get<DatabaseHelper>().getManga(mangaId).executeAsBlocking(),
    )

    constructor(bundle: Bundle) : this(bundle.getLong(MANGA_EXTRA))

    override var presenter = SimilarPresenter(manga)

    @Composable
    override fun ScreenContent() {
        SimilarScreen(
            controller = this,
            similarScreenState = presenter.similarScreenState.collectAsState(),
            switchDisplayClick = presenter::switchDisplayMode,
            onBackPress = { activity?.onBackPressed() },
            mangaClick = { mangaId: Long -> router.pushController(MangaDetailsController(mangaId).withFadeTransaction()) },
            onRefresh = presenter::refresh,
        )
    }

    companion object {
        const val MANGA_EXTRA = "manga"
    }
}
