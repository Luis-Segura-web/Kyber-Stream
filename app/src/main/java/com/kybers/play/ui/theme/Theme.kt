package com.kybers.play.ui.theme

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.colorResource
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.kybers.play.R

@Composable
fun IPTVAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // 1. Leemos colores desde res/values/colors.xml
    val primaryDark    = colorResource(R.color.purple_200)
    val primaryLight   = colorResource(R.color.purple_500)
    val secondaryColor = colorResource(R.color.teal_200)
    val backgroundDark = colorResource(R.color.black)
    val backgroundLight= colorResource(R.color.white)
    val onPrimaryDark  = colorResource(R.color.black)
    val onPrimaryLight = colorResource(R.color.white)
    val onSurfaceDark  = colorResource(R.color.white)
    val onSurfaceLight = colorResource(R.color.black)

    // 2. Creamos esquemas de Material3
    val darkColors = darkColorScheme(
        primary      = primaryDark,
        secondary    = secondaryColor,
        background   = backgroundDark,
        surface      = backgroundDark,
        onPrimary    = onPrimaryDark,
        onSecondary  = onPrimaryDark,
        onBackground = onSurfaceDark,
        onSurface    = onSurfaceDark
    )
    val lightColors = lightColorScheme(
        primary      = primaryLight,
        secondary    = secondaryColor,
        background   = backgroundLight,
        surface      = backgroundLight,
        onPrimary    = onPrimaryLight,
        onSecondary  = onPrimaryLight,
        onBackground = onSurfaceLight,
        onSurface    = onSurfaceLight
    )

    val colors = if (darkTheme) darkColors else lightColors

    // 3. Edge-to-edge y contraste de status bar
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowInsetsControllerCompat(window, view)
                .isAppearanceLightStatusBars = !darkTheme
        }
    }

    // 4. Aplicamos el tema y añadimos padding para barras de sistema
    MaterialTheme(
        colorScheme = colors,
        typography   = Typography
    ) {
        Box(
            modifier = Modifier
                .systemBarsPadding()
                .background(colors.primary)
        ) {
            content()
        }
    }
}
