package org.j2k.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.LayoutDirection
import com.google.android.material.composethemeadapter3.createMdc3Theme

@Composable
fun TachiTheme(
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current

    val (colorScheme) = createMdc3Theme(
        context = context,
        layoutDirection = LayoutDirection.Ltr,
        setTextColors = true,
        readTypography = false,
    )

    MaterialTheme(
        colorScheme = colorScheme!!,
        content = content,
    )
}
