package io.github.akuraneee.reschools.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.akuraneee.reschools.R
import io.github.akuraneee.reschools.UiState
import io.github.akuraneee.reschools.core.HwMode
import io.github.akuraneee.reschools.ui.components.ContentMaxWidth
import io.github.akuraneee.reschools.ui.components.SectionHeader
import io.github.akuraneee.reschools.ui.components.SegmentedList
import io.github.akuraneee.reschools.ui.components.TooltipIconButton
import io.github.akuraneee.reschools.ui.components.contentPaddingOf
import io.github.akuraneee.reschools.ui.components.segmentedColors

/** Настройки — спокойный служебный экран: без акцентов, обычные списки. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class) // сегментированные списки и subtitle у app bar — экспериментальные в 1.5.0-beta
@Composable
fun SettingsScreen(
    state: UiState,
    versionName: String,
    onBack: () -> Unit,
    onSelectPupil: (String) -> Unit,
    onHomeworkMode: (HwMode) -> Unit,
    onOpenSite: () -> Unit,
    onOtherAccount: () -> Unit,
    onClearData: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = { TooltipIconButton(R.drawable.ic_arrow_back_24, stringResource(R.string.action_back), onBack) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth(),
                contentPadding = contentPaddingOf(padding),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                if (state.pupils.size > 1) {
                    item(key = "pupils") {
                        Section(stringResource(R.string.settings_pupil)) {
                            SegmentedList(state.pupils.size) { index, shapes ->
                                val pupil = state.pupils[index]
                                val selected = pupil.id == state.pupil?.id
                                SegmentedListItem(
                                    selected = selected,
                                    onClick = { onSelectPupil(pupil.id) },
                                    shapes = shapes,
        colors = segmentedColors(),
                                    leadingContent = { RadioButton(selected = selected, onClick = null) },
                                ) { Text(pupil.name) }
                            }
                        }
                    }
                }
                item(key = "homework") {
                    Section(stringResource(R.string.settings_homework)) {
                        val options = listOf(
                            Triple(HwMode.Assigned, R.string.settings_hw_assigned, R.string.settings_hw_assigned_hint),
                            Triple(HwMode.Due, R.string.settings_hw_due, R.string.settings_hw_due_hint),
                        )
                        SegmentedList(options.size) { index, shapes ->
                            val (mode, title, hint) = options[index]
                            val selected = state.settings.hwMode == mode
                            SegmentedListItem(
                                selected = selected,
                                onClick = { onHomeworkMode(mode) },
                                shapes = shapes,
        colors = segmentedColors(),
                                verticalAlignment = Alignment.Top,
                                leadingContent = { RadioButton(selected = selected, onClick = null) },
                                supportingContent = { Text(stringResource(hint)) },
                            ) { Text(stringResource(title)) }
                        }
                    }
                }
                item(key = "actions") {
                    val actions = listOf(
                        Triple(R.drawable.ic_open_in_new_24, R.string.settings_open_site, onOpenSite),
                        Triple(R.drawable.ic_login_24, R.string.settings_other_account, onOtherAccount),
                        Triple(R.drawable.ic_delete_24, R.string.settings_clear, { confirmClear = true }),
                    )
                    SegmentedList(actions.size) { index, shapes ->
                        val (icon, title, action) = actions[index]
                        SegmentedListItem(
                            onClick = action,
                            shapes = shapes,
        colors = segmentedColors(),
                            leadingContent = { Icon(painterResource(icon), contentDescription = null) },
                        ) { Text(stringResource(title)) }
                    }
                }
                item(key = "footer") {
                    Text(
                        text = stringResource(R.string.settings_footer, versionName),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.settings_clear_title)) },
            text = { Text(stringResource(R.string.settings_clear_text)) },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.action_cancel)) } },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; onClearData() }) { Text(stringResource(R.string.action_delete)) }
            },
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(title)
        content()
    }
}
