package io.github.akuraneee.reschools.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.github.akuraneee.reschools.R
import io.github.akuraneee.reschools.ui.components.TooltipIconButton

/**
 * Вход без WebView: логин и пароль отправляются в форму schools.by прямо из приложения и нигде не сохраняются.
 * Если на сайте капча — [onViaSite] открывает вход на самом сайте.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class) // LoadingIndicator и размеры кнопок — экспериментальные в 1.5.0-beta
@Composable
fun LoginScreen(
    signingIn: Boolean,
    error: String?,
    onSignIn: (user: String, password: String) -> Unit,
    onEdit: () -> Unit,
    onViaSite: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var user by rememberSaveable { mutableStateOf("") }
    // пароль не сохраняем даже в состояние экрана
    var password by remember { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val canSubmit = user.isNotBlank() && password.isNotEmpty() && !signingIn
    fun submit() {
        if (!canSubmit) return
        focus.clearFocus()
        onSignIn(user, password)
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.login_title)) },
                navigationIcon = { TooltipIconButton(R.drawable.ic_arrow_back_24, stringResource(R.string.action_back), onBack) },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).imePadding(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.login_text),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = user,
                    onValueChange = { user = it; onEdit() },
                    label = { Text(stringResource(R.string.login_user)) },
                    singleLine = true,
                    enabled = !signingIn,
                    isError = error != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.Username },
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; onEdit() },
                    label = { Text(stringResource(R.string.login_password)) },
                    singleLine = true,
                    enabled = !signingIn,
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        TooltipIconButton(
                            if (showPassword) R.drawable.ic_visibility_off_24 else R.drawable.ic_visibility_24,
                            stringResource(if (showPassword) R.string.login_hide_password else R.string.login_show_password),
                            { showPassword = !showPassword },
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done, autoCorrectEnabled = false),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.Password },
                )
                val size = ButtonDefaults.MediumContainerHeight
                Button(
                    onClick = ::submit,
                    enabled = canSubmit || signingIn,
                    shapes = ButtonDefaults.shapesFor(size),
                    contentPadding = ButtonDefaults.contentPaddingFor(size),
                    modifier = Modifier.fillMaxWidth().heightIn(size),
                ) {
                    if (signingIn) {
                        LoadingIndicator(Modifier.size(32.dp), color = LocalContentColor.current)
                    } else {
                        Text(stringResource(R.string.action_sign_in), style = ButtonDefaults.textStyleFor(size))
                    }
                }
                TextButton(onClick = onViaSite, enabled = !signingIn, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(stringResource(R.string.login_via_site))
                }
            }
        }
    }
}
