package br.com.vigiauto.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import br.com.vigiauto.domain.model.ThemeMode

private val DarkColors = darkColorScheme(
    primary = BlueAccent,
    onPrimary = Color(0xFF001A4D),
    primaryContainer = BlueDeep,
    onPrimaryContainer = Color(0xFFD6E4FF),
    secondary = Amber,
    onSecondary = Color(0xFF2B1D00),
    background = Ink,
    onBackground = Color(0xFFE6EAF0),
    surface = InkSurface,
    onSurface = Color(0xFFE6EAF0),
    surfaceVariant = InkSurfaceHigh,
    onSurfaceVariant = Color(0xFFB9C2D0),
    outline = Color(0xFF3A4456),
    error = Danger,
    onError = Color(0xFF2B0000)
)

private val LightColors = lightColorScheme(
    primary = BlueDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E4FF),
    onPrimaryContainer = Color(0xFF001A4D),
    secondary = Color(0xFF8A6100),
    onSecondary = Color.White,
    background = Paper,
    onBackground = Color(0xFF101828),
    surface = PaperSurface,
    onSurface = Color(0xFF101828),
    surfaceVariant = PaperSurfaceHigh,
    onSurfaceVariant = Color(0xFF46536A),
    outline = Color(0xFFB4C0D3),
    error = Color(0xFFB3261E),
    onError = Color.White
)

@Composable
fun VigiAutoTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val darkTheme = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = DeltaTypography,
        content = content
    )
}
