package io.github.akuraneee.reschools.ui.welcome

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.akuraneee.reschools.R
import io.github.akuraneee.reschools.UiState
import io.github.akuraneee.reschools.ui.components.StatusBanner

/**
 * Первый экран: вход через сайт schools.by. Главный акцент — логотип в «печенье» MaterialShapes
 * и единственная заливная кнопка.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class) // MaterialShapes и размеры кнопок — экспериментальные в 1.5.0-beta
@Composable
fun WelcomeScreen(state: UiState, onLogin: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Surface(
                    modifier = Modifier.size(144.dp),
                    shape = MaterialShapes.Cookie9Sided.toShape(),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(painterResource(R.drawable.ic_menu_book_fill_48), contentDescription = null, modifier = Modifier.size(64.dp))
                    }
                }
                Spacer(Modifier.size(8.dp))
                Text(
                    text = stringResource(R.string.welcome_title),
                    style = MaterialTheme.typography.displaySmallEmphasized,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(R.string.welcome_text),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.size(8.dp))
                if (state.signingIn) {
                    LoadingIndicator()
                } else {
                    val size = ButtonDefaults.MediumContainerHeight
                    Button(
                        onClick = onLogin,
                        shapes = ButtonDefaults.shapesFor(size),
                        contentPadding = ButtonDefaults.contentPaddingFor(size),
                        modifier = Modifier.heightIn(size),
                    ) {
                        Text(stringResource(R.string.welcome_button), style = ButtonDefaults.textStyleFor(size))
                    }
                }
                StatusBanner(state, onRetry = onLogin, onLogin = onLogin)
                Text(
                    text = stringResource(R.string.welcome_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
