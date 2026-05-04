package com.example.mycard.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFBBDEFB),
    secondary = Color(0xFFB0BEC5),
    tertiary = Color(0xFFB3E5FC)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF455A64),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFECEFF1),
    onPrimaryContainer = Color(0xFF263238),
    secondary = Color(0xFF607D8B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFECEFF1),
    onSecondaryContainer = Color(0xFF263238),
    background = Color(0xFFFAFAFA),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF212121),
    onSurfaceVariant = Color(0xFF757575)
)

@Composable
fun MyCardTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
