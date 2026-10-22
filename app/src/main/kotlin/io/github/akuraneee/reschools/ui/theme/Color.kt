package io.github.akuraneee.reschools.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Фирменная схема: Material Theme Builder / material-color-utilities, тональное пятно (TonalSpot, спецификация 2021)
// от цвета #2F6FDE (синий значка приложения). Светлая и тёмная, обычный / средний / высокий контраст.
// Используется там, где нет динамических цветов (Android до 12) или они выключены; на Android 12+ цвета берутся из обоев.
// Это единственный файл, где разрешены литералы Color(0xFF…).

val lightScheme = lightColorScheme(
    primary = Color(0xFF465D91),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD9E2FF),
    onPrimaryContainer = Color(0xFF2D4578),
    inversePrimary = Color(0xFFAFC6FF),
    secondary = Color(0xFF575E71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDBE2F9),
    onSecondaryContainer = Color(0xFF404759),
    tertiary = Color(0xFF725573),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFCD7FB),
    onTertiaryContainer = Color(0xFF593E5A),
    background = Color(0xFFFAF8FF),
    onBackground = Color(0xFF1A1B20),
    surface = Color(0xFFFAF8FF),
    onSurface = Color(0xFF1A1B20),
    surfaceVariant = Color(0xFFE1E2EC),
    onSurfaceVariant = Color(0xFF44464F),
    surfaceTint = Color(0xFF465D91),
    inverseSurface = Color(0xFF2F3036),
    inverseOnSurface = Color(0xFFF1F0F7),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    outline = Color(0xFF757780),
    outlineVariant = Color(0xFFC5C6D0),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFAF8FF),
    surfaceDim = Color(0xFFDAD9E0),
    surfaceContainer = Color(0xFFEEEDF4),
    surfaceContainerHigh = Color(0xFFE8E7EF),
    surfaceContainerHighest = Color(0xFFE2E2E9),
    surfaceContainerLow = Color(0xFFF3F3FA),
    surfaceContainerLowest = Color(0xFFFFFFFF),
)

val lightMediumScheme = lightColorScheme(
    primary = Color(0xFF1B3566),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF556CA1),
    onPrimaryContainer = Color(0xFFFFFFFF),
    inversePrimary = Color(0xFFAFC6FF),
    secondary = Color(0xFF2F3648),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF666D80),
    onSecondaryContainer = Color(0xFFFFFFFF),
    tertiary = Color(0xFF472D49),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFF816382),
    onTertiaryContainer = Color(0xFFFFFFFF),
    background = Color(0xFFFAF8FF),
    onBackground = Color(0xFF1A1B20),
    surface = Color(0xFFFAF8FF),
    onSurface = Color(0xFF0F1116),
    surfaceVariant = Color(0xFFE1E2EC),
    onSurfaceVariant = Color(0xFF34363E),
    surfaceTint = Color(0xFF465D91),
    inverseSurface = Color(0xFF2F3036),
    inverseOnSurface = Color(0xFFF1F0F7),
    error = Color(0xFF740006),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFCF2C27),
    onErrorContainer = Color(0xFFFFFFFF),
    outline = Color(0xFF50525A),
    outlineVariant = Color(0xFF6B6D75),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFAF8FF),
    surfaceDim = Color(0xFFC6C6CD),
    surfaceContainer = Color(0xFFE8E7EF),
    surfaceContainerHigh = Color(0xFFDCDCE3),
    surfaceContainerHighest = Color(0xFFD1D1D8),
    surfaceContainerLow = Color(0xFFF3F3FA),
    surfaceContainerLowest = Color(0xFFFFFFFF),
)

val lightHighScheme = lightColorScheme(
    primary = Color(0xFF0E2A5B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF30487B),
    onPrimaryContainer = Color(0xFFFFFFFF),
    inversePrimary = Color(0xFFAFC6FF),
    secondary = Color(0xFF252C3D),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF42495B),
    onSecondaryContainer = Color(0xFFFFFFFF),
    tertiary = Color(0xFF3C233E),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFF5B405C),
    onTertiaryContainer = Color(0xFFFFFFFF),
    background = Color(0xFFFAF8FF),
    onBackground = Color(0xFF1A1B20),
    surface = Color(0xFFFAF8FF),
    onSurface = Color(0xFF000000),
    surfaceVariant = Color(0xFFE1E2EC),
    onSurfaceVariant = Color(0xFF000000),
    surfaceTint = Color(0xFF465D91),
    inverseSurface = Color(0xFF2F3036),
    inverseOnSurface = Color(0xFFFFFFFF),
    error = Color(0xFF600004),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFF98000A),
    onErrorContainer = Color(0xFFFFFFFF),
    outline = Color(0xFF292C33),
    outlineVariant = Color(0xFF474951),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFAF8FF),
    surfaceDim = Color(0xFFB8B8BF),
    surfaceContainer = Color(0xFFE2E2E9),
    surfaceContainerHigh = Color(0xFFD4D4DB),
    surfaceContainerHighest = Color(0xFFC6C6CD),
    surfaceContainerLow = Color(0xFFF1F0F7),
    surfaceContainerLowest = Color(0xFFFFFFFF),
)

val darkScheme = darkColorScheme(
    primary = Color(0xFFAFC6FF),
    onPrimary = Color(0xFF142F60),
    primaryContainer = Color(0xFF2D4578),
    onPrimaryContainer = Color(0xFFD9E2FF),
    inversePrimary = Color(0xFF465D91),
    secondary = Color(0xFFBFC6DC),
    onSecondary = Color(0xFF293042),
    secondaryContainer = Color(0xFF404759),
    onSecondaryContainer = Color(0xFFDBE2F9),
    tertiary = Color(0xFFDFBBDE),
    onTertiary = Color(0xFF412743),
    tertiaryContainer = Color(0xFF593E5A),
    onTertiaryContainer = Color(0xFFFCD7FB),
    background = Color(0xFF121318),
    onBackground = Color(0xFFE2E2E9),
    surface = Color(0xFF121318),
    onSurface = Color(0xFFE2E2E9),
    surfaceVariant = Color(0xFF44464F),
    onSurfaceVariant = Color(0xFFC5C6D0),
    surfaceTint = Color(0xFFAFC6FF),
    inverseSurface = Color(0xFFE2E2E9),
    inverseOnSurface = Color(0xFF2F3036),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF8F9099),
    outlineVariant = Color(0xFF44464F),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF38393E),
    surfaceDim = Color(0xFF121318),
    surfaceContainer = Color(0xFF1E1F25),
    surfaceContainerHigh = Color(0xFF282A2F),
    surfaceContainerHighest = Color(0xFF33353A),
    surfaceContainerLow = Color(0xFF1A1B20),
    surfaceContainerLowest = Color(0xFF0C0E13),
)

val darkMediumScheme = darkColorScheme(
    primary = Color(0xFFD0DCFF),
    onPrimary = Color(0xFF042355),
    primaryContainer = Color(0xFF7990C7),
    onPrimaryContainer = Color(0xFF000000),
    inversePrimary = Color(0xFF2E4779),
    secondary = Color(0xFFD5DCF3),
    onSecondary = Color(0xFF1E2536),
    secondaryContainer = Color(0xFF8990A5),
    onSecondaryContainer = Color(0xFF000000),
    tertiary = Color(0xFFF6D1F4),
    onTertiary = Color(0xFF351D37),
    tertiaryContainer = Color(0xFFA786A7),
    onTertiaryContainer = Color(0xFF000000),
    background = Color(0xFF121318),
    onBackground = Color(0xFFE2E2E9),
    surface = Color(0xFF121318),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF44464F),
    onSurfaceVariant = Color(0xFFDBDCE6),
    surfaceTint = Color(0xFFAFC6FF),
    inverseSurface = Color(0xFFE2E2E9),
    inverseOnSurface = Color(0xFF282A2F),
    error = Color(0xFFFFD2CC),
    onError = Color(0xFF540003),
    errorContainer = Color(0xFFFF5449),
    onErrorContainer = Color(0xFF000000),
    outline = Color(0xFFB0B1BB),
    outlineVariant = Color(0xFF8E9099),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF43444A),
    surfaceDim = Color(0xFF121318),
    surfaceContainer = Color(0xFF26282D),
    surfaceContainerHigh = Color(0xFF313238),
    surfaceContainerHighest = Color(0xFF3C3D43),
    surfaceContainerLow = Color(0xFF1C1D23),
    surfaceContainerLowest = Color(0xFF06070C),
)

val darkHighScheme = darkColorScheme(
    primary = Color(0xFFECEFFF),
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFFAAC2FD),
    onPrimaryContainer = Color(0xFF000A24),
    inversePrimary = Color(0xFF2E4779),
    secondary = Color(0xFFECEFFF),
    onSecondary = Color(0xFF000000),
    secondaryContainer = Color(0xFFBBC2D8),
    onSecondaryContainer = Color(0xFF040B1B),
    tertiary = Color(0xFFFFEAFB),
    onTertiary = Color(0xFF000000),
    tertiaryContainer = Color(0xFFDBB7DA),
    onTertiaryContainer = Color(0xFF18031B),
    background = Color(0xFF121318),
    onBackground = Color(0xFFE2E2E9),
    surface = Color(0xFF121318),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF44464F),
    onSurfaceVariant = Color(0xFFFFFFFF),
    surfaceTint = Color(0xFFAFC6FF),
    inverseSurface = Color(0xFFE2E2E9),
    inverseOnSurface = Color(0xFF000000),
    error = Color(0xFFFFECE9),
    onError = Color(0xFF000000),
    errorContainer = Color(0xFFFFAEA4),
    onErrorContainer = Color(0xFF220001),
    outline = Color(0xFFEFEFF9),
    outlineVariant = Color(0xFFC1C2CC),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF4F5056),
    surfaceDim = Color(0xFF121318),
    surfaceContainer = Color(0xFF2F3036),
    surfaceContainerHigh = Color(0xFF3A3B41),
    surfaceContainerHighest = Color(0xFF45464C),
    surfaceContainerLow = Color(0xFF1E1F25),
    surfaceContainerLowest = Color(0xFF000000),
)

/** Цвета отметки: фон и цвет текста на нём (container / onContainer). */
@Immutable
class MarkColors(val container: Color, val onContainer: Color)

/**
 * Цвета уровней отметок 1…5 (красный → зелёный по белорусской 10-балльной шкале). Это «пользовательские цвета»
 * Material Theme Builder: оттенки подмешаны к основному цвету, но остаются узнаваемыми — смысл важнее гармонии,
 * поэтому они не меняются вместе с динамической схемой.
 */
@Immutable
class MarkPalette(private val levels: List<MarkColors>) {
    /** [level] — 1…5, как у `markLevel` в ядре. */
    operator fun get(level: Int): MarkColors = levels[(level - 1).coerceIn(0, levels.lastIndex)]
}

private val lightMarks = MarkPalette(
    listOf(
        MarkColors(Color(0xFFFFD9DF), Color(0xFF3D0318)),
        MarkColors(Color(0xFFFFDBD0), Color(0xFF3A0B00)),
        MarkColors(Color(0xFFFFDDB9), Color(0xFF2B1700)),
        MarkColors(Color(0xFFB1F2B4), Color(0xFF002108)),
        MarkColors(Color(0xFF9CF4D0), Color(0xFF002116)),
    ),
)

private val lightMediumMarks = MarkPalette(
    listOf(
        MarkColors(Color(0xFFFFE1E5), Color(0xFF370114)),
        MarkColors(Color(0xFFFFE2DA), Color(0xFF330800)),
        MarkColors(Color(0xFFFFE4C9), Color(0xFF251400)),
        MarkColors(Color(0xFFB7F8B9), Color(0xFF001D06)),
        MarkColors(Color(0xFFA1FAD5), Color(0xFF001C13)),
    ),
)

private val lightHighMarks = MarkPalette(
    listOf(
        MarkColors(Color(0xFFFFECEE), Color(0xFF2B000E)),
        MarkColors(Color(0xFFFFEDE8), Color(0xFF270500)),
        MarkColors(Color(0xFFFFEEDF), Color(0xFF1C0E00)),
        MarkColors(Color(0xFFC6FFC7), Color(0xFF001504)),
        MarkColors(Color(0xFFBCFFE1), Color(0xFF00150D)),
    ),
)

private val darkMarks = MarkPalette(
    listOf(
        MarkColors(Color(0xFF753043), Color(0xFFFFD9DF)),
        MarkColors(Color(0xFF76331D), Color(0xFFFFDBD0)),
        MarkColors(Color(0xFF663E00), Color(0xFFFFDDB9)),
        MarkColors(Color(0xFF145224), Color(0xFFB1F2B4)),
        MarkColors(Color(0xFF00513C), Color(0xFF9CF4D0)),
    ),
)

private val darkMediumMarks = MarkPalette(
    listOf(
        MarkColors(Color(0xFF662438), Color(0xFFFFE1E5)),
        MarkColors(Color(0xFF672813), Color(0xFFFFE2DA)),
        MarkColors(Color(0xFF573300), Color(0xFFFFE4C9)),
        MarkColors(Color(0xFF024619), Color(0xFFB7F8B9)),
        MarkColors(Color(0xFF004432), Color(0xFFA1FAD5)),
    ),
)

private val darkHighMarks = MarkPalette(
    listOf(
        MarkColors(Color(0xFF58192D), Color(0xFFFFECEE)),
        MarkColors(Color(0xFF581D09), Color(0xFFFFEDE8)),
        MarkColors(Color(0xFF472A00), Color(0xFFFFEEDF)),
        MarkColors(Color(0xFF003913), Color(0xFFC6FFC7)),
        MarkColors(Color(0xFF003828), Color(0xFFBCFFE1)),
    ),
)

internal enum class Contrast { Standard, Medium, High }

internal fun brandColorScheme(dark: Boolean, contrast: Contrast) = when {
    !dark && contrast == Contrast.Standard -> lightScheme
    !dark && contrast == Contrast.Medium -> lightMediumScheme
    !dark -> lightHighScheme
    contrast == Contrast.Standard -> darkScheme
    contrast == Contrast.Medium -> darkMediumScheme
    else -> darkHighScheme
}

internal fun markPalette(dark: Boolean, contrast: Contrast) = when {
    !dark && contrast == Contrast.Standard -> lightMarks
    !dark && contrast == Contrast.Medium -> lightMediumMarks
    !dark -> lightHighMarks
    contrast == Contrast.Standard -> darkMarks
    contrast == Contrast.Medium -> darkMediumMarks
    else -> darkHighMarks
}

val LocalMarkPalette = staticCompositionLocalOf { lightMarks }
