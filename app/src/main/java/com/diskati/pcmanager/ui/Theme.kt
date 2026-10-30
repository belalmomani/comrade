package com.diskati.pcmanager.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Color(0xFF0A5CFF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE8FF),
    onPrimaryContainer = Color(0xFF001B4D),
    secondary = Color(0xFF0A8FB8),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD3F3FF),
    onSecondaryContainer = Color(0xFF00313F),
    background = Color(0xFFF6F8FC),
    onBackground = Color(0xFF111827),
    surface = Color.White,
    onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFE8EDF5),
    onSurfaceVariant = Color(0xFF4B5563),
    outline = Color(0xFF9AA5B8),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFFFE0DC),
    onErrorContainer = Color(0xFF410002),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7FA8FF),
    onPrimary = Color(0xFF002A78),
    primaryContainer = Color(0xFF0A3FA8),
    onPrimaryContainer = Color(0xFFDCE8FF),
    secondary = Color(0xFF5CD0F5),
    onSecondary = Color(0xFF003544),
    secondaryContainer = Color(0xFF004D63),
    onSecondaryContainer = Color(0xFFD3F3FF),
    background = Color(0xFF0B1220),
    onBackground = Color(0xFFE5E9F2),
    surface = Color(0xFF111A2C),
    onSurface = Color(0xFFE5E9F2),
    surfaceVariant = Color(0xFF1D2840),
    onSurfaceVariant = Color(0xFFB4BED0),
    outline = Color(0xFF66738C),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val DiskatiShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
)

/** الثيم: أزرق/سماوي/أبيض، وضع ليلي تلقائي حسب النظام، واتجاه RTL إجباري لأن اللغة الأساسية عربية. */
@Composable
fun DiskatiTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = Typography(),
            shapes = DiskatiShapes,
            content = content,
        )
    }
}
