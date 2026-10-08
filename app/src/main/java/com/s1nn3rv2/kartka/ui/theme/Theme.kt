package com.s1nn3rv2.kartka.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/** Material You only: colors always come from the wallpaper-based dynamic scheme (minSdk 31 guarantees it). */
@Composable
fun KartkaTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val context = LocalContext.current
    MaterialExpressiveTheme(
        colorScheme = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context),
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}
