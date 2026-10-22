package io.github.akuraneee.reschools.ui.theme

import android.app.UiModeManager
import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Тема приложения: Material 3 Expressive. Цвета — динамические (Android 12+), иначе фирменная схема;
 * движение — пружины «expressive», а при выключенных анимациях в системе — сдержанные «standard».
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class) // MaterialExpressiveTheme и MotionScheme.expressive() всё ещё экспериментальные в 1.5.0-beta
@Composable
fun ReSchoolsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val contrast = rememberContrast(context)
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        else -> brandColorScheme(darkTheme, contrast)
    }
    val reduceMotion = rememberReduceMotion(context)
    CompositionLocalProvider(LocalMarkPalette provides markPalette(darkTheme, contrast)) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            motionScheme = if (reduceMotion) MotionScheme.standard() else MotionScheme.expressive(),
            content = content,
        )
    }
}

// Системная настройка контрастности (Android 14+): учитываем её для фирменной схемы и цветов отметок.
@Composable
private fun rememberContrast(context: Context): Contrast = remember(context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        Contrast.Standard
    } else {
        val c = context.getSystemService(UiModeManager::class.java).contrast
        when {
            c >= 0.66f -> Contrast.High
            c >= 0.33f -> Contrast.Medium
            else -> Contrast.Standard
        }
    }
}

// Анимации в системе выключены (масштаб 0) — «пружины» без отскока, без морфинга форм.
@Composable
private fun rememberReduceMotion(context: Context): Boolean = remember(context) {
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
}
