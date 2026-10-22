package io.github.akuraneee.reschools.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.akuraneee.reschools.R
import io.github.akuraneee.reschools.UiError
import io.github.akuraneee.reschools.UiState

private sealed interface Banner {
    data class Failure(val error: UiError) : Banner
    data class Progress(val done: Int, val total: Int) : Banner
}

private fun UiState.banner(): Banner? = when {
    error != null -> Banner.Failure(error)
    loading && progress != null && hasData -> Banner.Progress(progress.first, progress.second)
    else -> null
}

/**
 * Состояние обновления над содержимым: ошибка (с кнопкой «Повторить» / «Войти») или ход обновления.
 * Появляется и исчезает пружинами темы; пока исчезает, держим последнюю надпись.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class) // волнистый индикатор прогресса — экспериментальный в 1.5.0-beta
@Composable
fun StatusBanner(state: UiState, onRetry: () -> Unit, onLogin: () -> Unit, modifier: Modifier = Modifier) {
    val current = state.banner()
    var last by remember { mutableStateOf(current) }
    if (current != null) last = current
    val motion = MaterialTheme.motionScheme
    AnimatedVisibility(
        visible = current != null,
        modifier = modifier,
        enter = fadeIn(motion.fastEffectsSpec()) + expandVertically(motion.defaultSpatialSpec<IntSize>()),
        exit = fadeOut(motion.fastEffectsSpec()) + shrinkVertically(motion.defaultSpatialSpec<IntSize>()),
    ) {
        when (val banner = last) {
            is Banner.Failure -> FailureBanner(banner.error, onRetry, onLogin)
            is Banner.Progress -> ProgressBanner(banner)
            null -> Unit
        }
    }
}

@Composable
private fun FailureBanner(error: UiError, onRetry: () -> Unit, onLogin: () -> Unit) {
    val offline = error is UiError.Offline
    val text = when (error) {
        UiError.Auth -> stringResource(R.string.error_auth)
        UiError.Offline -> stringResource(R.string.error_offline)
        is UiError.Other -> stringResource(R.string.error_other, error.message)
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = if (offline) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.errorContainer,
        contentColor = if (offline) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Row(
            Modifier.padding(start = 16.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(painterResource(if (offline) R.drawable.ic_cloud_off_24 else R.drawable.ic_error_24), contentDescription = null)
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            if (error == UiError.Auth) {
                TextButton(onClick = onLogin) { Text(stringResource(R.string.action_sign_in)) }
            } else {
                TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
            }
        }
    }
}

@Composable
private fun ProgressBanner(banner: Banner.Progress) {
    val progress by animateFloatAsState(
        targetValue = banner.done.toFloat() / banner.total.coerceAtLeast(1),
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "refreshProgress",
    )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.progress_refresh, banner.done, banner.total), style = MaterialTheme.typography.bodyMedium)
            LinearWavyProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        }
    }
}
