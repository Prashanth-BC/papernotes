package com.example.notes.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Light color scheme - Paper notebook aesthetic
private val LightNotebookColorScheme = lightColorScheme(
    primary = LeatherBrown,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDCC4),      // Light leather tint
    onPrimaryContainer = Color(0xFF2E1500),

    secondary = ClassicBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD8E2F3),    // Light blue tint
    onSecondaryContainer = Color(0xFF001D36),

    tertiary = GoldAccent,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE8A3),     // Light gold tint
    onTertiaryContainer = Color(0xFF261900),

    error = ErrorRed,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),

    background = PaperBackground,              // Warm beige
    onBackground = InkBlack,                   // Ink black

    surface = PaperSurface,                    // Cream/aged paper
    onSurface = InkBlack,
    surfaceVariant = Color(0xFFF0EBE3),        // Slightly darker paper
    onSurfaceVariant = WarmGray,

    outline = SoftGray,
    outlineVariant = Color(0xFFF5F1E8),

    scrim = Color.Black.copy(alpha = 0.4f),

    inverseSurface = Color(0xFF322F2B),
    inverseOnSurface = Color(0xFFF5F0E9),
    inversePrimary = Color(0xFFFFB68C)
)

// Dark color scheme - Dark paper aesthetic
private val DarkNotebookColorScheme = darkColorScheme(
    primary = WarmBrown,
    onPrimary = Color(0xFF4A2800),
    primaryContainer = Color(0xFF6A3C00),
    onPrimaryContainer = Color(0xFFFFDCC4),

    secondary = SkyBlue,
    onSecondary = Color(0xFF003258),
    secondaryContainer = Color(0xFF00497D),
    onSecondaryContainer = Color(0xFFD8E2F3),

    tertiary = BrightGold,
    onTertiary = Color(0xFF3F2E00),
    tertiaryContainer = Color(0xFF5B4300),
    onTertiaryContainer = Color(0xFFFFE8A3),

    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),

    background = DarkPaperBackground,          // Dark warm background
    onBackground = LightInk,                   // Light ink

    surface = DarkPaperSurface,                // Dark aged paper
    onSurface = LightInk,
    surfaceVariant = Color(0xFF51483F),        // Darker variant
    onSurfaceVariant = MediumGray,

    outline = DarkGray,
    outlineVariant = Color(0xFF51483F),

    scrim = Color.Black.copy(alpha = 0.6f),

    inverseSurface = Color(0xFFEAE1D9),
    inverseOnSurface = Color(0xFF322F2B),
    inversePrimary = LeatherBrown
)

@Composable
fun NotebookTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,  // Default to false to maintain notebook aesthetic
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkNotebookColorScheme
        else -> LightNotebookColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = NotebookTypography,
        content = content
    )
}
