package org.j2k.presentation.components

import ToolTipIconButton
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import eu.kanade.tachiyomi.R

@Composable
fun ListGridActionButton(isList: Boolean, buttonClicked: () -> Unit) {
    when (isList.not()) {
        true -> ToolTipIconButton(
            toolTipLabel = stringResource(id = R.string.display_as_, "list"),
            icon = Icons.Filled.ViewList,
            buttonClicked = buttonClicked,
        )

        false -> ToolTipIconButton(
            toolTipLabel = stringResource(id = R.string.display_as_, "grid"),
            icon = Icons.Filled.ViewModule,
            buttonClicked = buttonClicked,
        )
    }
}

@Preview
@Composable
private fun ListGridActionButton() {
    Row {
        ListGridActionButton(isList = false) {}
        ListGridActionButton(isList = true) {}
    }
}
