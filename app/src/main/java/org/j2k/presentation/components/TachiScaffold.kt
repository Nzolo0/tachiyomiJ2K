package org.j2k.presentation.components

import ToolTipIconButton
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.ripple.LocalRippleTheme
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import eu.kanade.tachiyomi.R
import org.j2k.presentation.screens.PrimaryColorRippleTheme
import org.j2k.presentation.screens.ThemeColorState

@Composable
fun TachiScaffold(
    title: String,
    onNavigationIconClicked: () -> Unit,
    modifier: Modifier = Modifier,
    themeColorState: ThemeColorState? = null,
    scrollBehavior: TopAppBarScrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(),
    navigationIcon: ImageVector = Icons.Filled.ArrowBack,
    navigationIconLabel: String = stringResource(id = R.string.back),
    subtitle: String = "",
    snackBarHost: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit = {},
) {
    val systemUiController = rememberSystemUiController()
    val useDarkIcons = MaterialTheme.colorScheme.surface.luminance() > .5
    val color = getTopAppBarColor(title)
    SideEffect {
        systemUiController.setStatusBarColor(color, darkIcons = useDarkIcons)
    }
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = snackBarHost,
        topBar =
        {
            CompositionLocalProvider(
                LocalRippleTheme provides (themeColorState?.rippleTheme ?: PrimaryColorRippleTheme),
            ) {
                CenterAlignedTopAppBar(
                    colors = TopAppBarDefaults.smallTopAppBarColors(
                        containerColor = color,
                        scrolledContainerColor = color,
                    ),
                    modifier = Modifier.statusBarsPadding(),
                    title = {
                        if (title.isNotEmpty()) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    },
                    navigationIcon = {
                        ToolTipIconButton(
                            toolTipLabel = navigationIconLabel,
                            icon = navigationIcon,
                            buttonClicked = onNavigationIconClicked,
                        )
                    },
                    actions = actions,
                    scrollBehavior = scrollBehavior,
                )
            }
        },
    ) { paddingValues ->
        CompositionLocalProvider(LocalRippleTheme provides PrimaryColorRippleTheme) {
            content(paddingValues)
        }
    }
}

@Composable
fun getTopAppBarColor(title: String): Color {
    return when (title.isEmpty()) {
        true -> Color.Transparent
        false -> MaterialTheme.colorScheme.surface.copy(alpha = .7f)
    }
}
