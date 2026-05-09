package com.abk.kernel.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.expressiveLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowCompat

@Composable
fun AbkTheme(
    themeMode: String = "system",
    dynamicColorEnabled: Boolean = true,
    customThemeColorArgb: Int? = null,
    customAccentColorArgb: Int? = null,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }

    val context = LocalContext.current
    val useDynamicColor = dynamicColorEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colorScheme = when {
        useDynamicColor -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> customExpressiveColorScheme(
            darkTheme = darkTheme,
            themeColorArgb = customThemeColorArgb,
            accentColorArgb = customAccentColorArgb
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.surface.toArgb()
            window.navigationBarColor = colorScheme.surface.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        content = content
    )
}

private fun customExpressiveColorScheme(
    darkTheme: Boolean,
    themeColorArgb: Int?,
    accentColorArgb: Int?
): ColorScheme {
    val base = if (darkTheme) darkColorScheme() else expressiveLightColorScheme()
    if (themeColorArgb == null && accentColorArgb == null) return base

    val primarySource = themeColorArgb ?: base.primary.toArgb()
    val accentSource = accentColorArgb ?: base.secondary.toArgb()
    val tertiarySource = hueShift(accentSource, 28f)
    val primary = tonalColor(primarySource, if (darkTheme) 0.78f else 0.40f)
    val primaryContainer = tonalColor(primarySource, if (darkTheme) 0.24f else 0.86f, saturationMultiplier = 0.72f)
    val secondary = tonalColor(accentSource, if (darkTheme) 0.78f else 0.40f)
    val secondaryContainer = tonalColor(accentSource, if (darkTheme) 0.24f else 0.86f, saturationMultiplier = 0.68f)
    val tertiary = tonalColor(tertiarySource, if (darkTheme) 0.76f else 0.38f)
    val tertiaryContainer = tonalColor(tertiarySource, if (darkTheme) 0.24f else 0.86f, saturationMultiplier = 0.64f)

    return base.copy(
        primary = Color(primary),
        onPrimary = readableOnColor(primary),
        primaryContainer = Color(primaryContainer),
        onPrimaryContainer = readableOnColor(primaryContainer),
        inversePrimary = Color(tonalColor(primarySource, if (darkTheme) 0.40f else 0.80f)),
        secondary = Color(secondary),
        onSecondary = readableOnColor(secondary),
        secondaryContainer = Color(secondaryContainer),
        onSecondaryContainer = readableOnColor(secondaryContainer),
        tertiary = Color(tertiary),
        onTertiary = readableOnColor(tertiary),
        tertiaryContainer = Color(tertiaryContainer),
        onTertiaryContainer = readableOnColor(tertiaryContainer),
        surfaceTint = Color(primary)
    )
}

private fun tonalColor(
    argb: Int,
    lightness: Float,
    saturationMultiplier: Float = 1f
): Int {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(argb, hsl)
    hsl[1] = (hsl[1] * saturationMultiplier).coerceIn(0.24f, 0.82f)
    hsl[2] = lightness.coerceIn(0f, 1f)
    return ColorUtils.HSLToColor(hsl)
}

private fun hueShift(argb: Int, degrees: Float): Int {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(argb, hsl)
    hsl[0] = (hsl[0] + degrees) % 360f
    return ColorUtils.HSLToColor(hsl)
}

private fun readableOnColor(argb: Int): Color {
    return if (ColorUtils.calculateLuminance(argb) > 0.5) {
        Color(0xFF11140F.toInt())
    } else {
        Color(0xFFFFFFFF.toInt())
    }
}
