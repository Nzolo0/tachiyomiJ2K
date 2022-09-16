package eu.kanade.tachiyomi.ui.base.controller

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.runtime.Composable
import eu.kanade.tachiyomi.databinding.EmptyComposeControllerBinding
import eu.kanade.tachiyomi.ui.base.presenter.BaseCoroutinePresenter
import org.j2k.presentation.theme.TachiTheme

abstract class BaseComposeController<PS : BaseCoroutinePresenter<*>>(bundle: Bundle? = null) :
    BaseCoroutineController<EmptyComposeControllerBinding, BaseCoroutinePresenter<*>>(bundle) {

    override fun onViewCreated(view: View) {
        hideToolbar()
        super.onViewCreated(view)
        binding.root.consumeWindowInsets = false
        binding.root.setContent {
            TachiTheme {
                ScreenContent()
            }
        }
    }

    override fun createBinding(inflater: LayoutInflater) =
        EmptyComposeControllerBinding.inflate(inflater)

    @Composable
    abstract fun ScreenContent()
}
