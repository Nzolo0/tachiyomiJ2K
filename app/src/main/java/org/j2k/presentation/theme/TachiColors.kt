package org.j2k.presentation.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

class TachiColors {
    companion object {
        const val highAlphaHighContrast = 1f
        const val highAlphaLowContrast = .87f
        const val mediumAlphaHighContrast = .74f
        const val mediumAlphaLowContrast = .6f
        const val disabledAlphaHighContrast = .38f
        const val disabledAlphaLowContrast = .38f
        const val veryLowContrast = .1f
    }
}

object Outline {
    val color = Color(0XFF9D9D9D)
    val thickness = .75.dp
}
