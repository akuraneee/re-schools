package io.github.akuraneee.reschools.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.akuraneee.reschools.R
import io.github.akuraneee.reschools.UiState
import io.github.akuraneee.reschools.core.computeStats
import io.github.akuraneee.reschools.ui.components.ContentMaxWidth
import io.github.akuraneee.reschools.ui.components.SegmentedNote
import io.github.akuraneee.reschools.ui.components.StatusBanner
import io.github.akuraneee.reschools.ui.components.TooltipIconButton
import io.github.akuraneee.reschools.ui.components.contentPaddingOf

/**
 * «Статистика» (из Schools++): «Обзор», «Предмет», «Динамика». Недели с начала учебного года подгружаются
 * при открытии вкладки, потом остаются в кэше.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StatsScreen(
    state: UiState,
    contentInsets: WindowInsets,
    onLoadHistory: () -> Unit,
    onRefresh: () -> Unit,
    onLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(state.pupil?.id) { onLoadHistory() }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var subjectKey by rememberSaveable { mutableStateOf<String?>(null) }
    var sortKey by rememberSaveable { mutableStateOf("name") }
    var whatIf by rememberSaveable { mutableStateOf("") }
    val stats = remember(state.lessons, state.links, state.today, state.settings.threshold) {
        computeStats(state.lessons, { state.links[it] }, state.today, state.settings.threshold)
    }
    val listState = rememberLazyListState()

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = contentInsets,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.stats_title)) },
                actions = {
                    TooltipIconButton(R.drawable.ic_refresh_24, stringResource(R.string.action_refresh), onRefresh, enabled = !state.loading)
                },
                scrollBehavior = scrollBehavior,
                windowInsets = contentInsets,
            )
        },
    ) { padding ->
        val pullState = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = state.loading,
            onRefresh = onRefresh,
            state = pullState,
            modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()),
            contentAlignment = Alignment.TopCenter,
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    state = pullState,
                    isRefreshing = state.loading,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            },
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth(),
                contentPadding = contentPaddingOf(padding),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                item(key = "banner") { StatusBanner(state, onRetry = onRefresh, onLogin = onLogin) }
                state.historyProgress?.let { (done, total) -> item(key = "progress") { HistoryProgress(done, total) } }
                item(key = "tabs") {
                    val titles = listOf(R.string.stats_tab_overview, R.string.stats_tab_subject, R.string.stats_tab_dynamics)
                    PrimaryTabRow(selectedTabIndex = tab) {
                        titles.forEachIndexed { i, title ->
                            Tab(selected = i == tab, onClick = { tab = i }, text = { Text(stringResource(title)) })
                        }
                    }
                }
                if (stats.subjects.isEmpty()) {
                    item(key = "none") { SegmentedNote(stringResource(R.string.stats_none)) }
                } else {
                    val subject = stats.subjects.firstOrNull { it.key == subjectKey } ?: stats.subjects.first()
                    when (tab) {
                        0 -> overviewItems(stats, sortKey, onSort = { sortKey = it }, onOpenSubject = { subjectKey = it; tab = 1 })
                        1 -> subjectItems(stats, subject, onPick = { subjectKey = it }, whatIf = whatIf, onWhatIf = { whatIf = it })
                        else -> dynamicsItems(stats, onOpenSubject = { subjectKey = it; tab = 1 })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class) // волнистый индикатор — экспериментальный в 1.5.0-beta
@Composable
private fun HistoryProgress(done: Int, total: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.stats_loading, done, total), style = MaterialTheme.typography.bodyMedium)
            LinearWavyProgressIndicator(progress = { done.toFloat() / total.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth())
        }
    }
}
