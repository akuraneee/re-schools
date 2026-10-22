package io.github.akuraneee.reschools.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.akuraneee.reschools.R
import io.github.akuraneee.reschools.UiState
import io.github.akuraneee.reschools.core.Stats
import io.github.akuraneee.reschools.core.SubjectStat
import io.github.akuraneee.reschools.core.computeStats
import io.github.akuraneee.reschools.core.formatMean
import io.github.akuraneee.reschools.core.markLevel
import io.github.akuraneee.reschools.ui.components.ContentMaxWidth
import io.github.akuraneee.reschools.ui.components.SectionHeader
import io.github.akuraneee.reschools.ui.components.SegmentedList
import io.github.akuraneee.reschools.ui.components.SegmentedNote
import io.github.akuraneee.reschools.ui.components.StatusBanner
import io.github.akuraneee.reschools.ui.components.contentPaddingOf
import io.github.akuraneee.reschools.ui.components.mergedRow
import io.github.akuraneee.reschools.ui.components.segmentedColors
import io.github.akuraneee.reschools.ui.theme.LocalMarkPalette

private enum class Period(val days: Long?) { Year(null), Days30(30), Days7(7) }

/** «Статистика»: средние и динамика отметок. Недели с начала учебного года подгружаются при открытии вкладки. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StatsScreen(
    state: UiState,
    contentInsets: WindowInsets,
    onLoadHistory: () -> Unit,
    onRetry: () -> Unit,
    onLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(state.pupil?.id) { onLoadHistory() }
    var periodIndex by rememberSaveable { mutableIntStateOf(0) }
    val period = Period.entries[periodIndex]
    val stats = remember(state.lessons, state.today, period) {
        computeStats(state.lessons, state.today, period.days?.let { state.today.minusDays(it - 1) })
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = contentInsets,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.stats_title)) },
                scrollBehavior = scrollBehavior,
                windowInsets = contentInsets,
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth(),
                contentPadding = contentPaddingOf(padding),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                item { StatusBanner(state, onRetry = onRetry, onLogin = onLogin) }
                state.historyProgress?.let { (done, total) -> item { HistoryProgress(done, total) } }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Period.entries.forEachIndexed { i, p ->
                            FilterChip(
                                selected = i == periodIndex,
                                onClick = { periodIndex = i },
                                label = {
                                    Text(
                                        stringResource(
                                            when (p) {
                                                Period.Year -> R.string.stats_period_all
                                                Period.Days30 -> R.string.stats_period_30
                                                Period.Days7 -> R.string.stats_period_7
                                            },
                                        ),
                                    )
                                },
                            )
                        }
                    }
                }
                if (stats.marks == 0) {
                    item { SegmentedNote(stringResource(R.string.stats_none)) }
                } else {
                    item { Summary(stats) }
                    item { Distribution(stats) }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SectionHeader(stringResource(R.string.stats_subjects))
                            SegmentedList(stats.subjects.size) { i, shapes -> SubjectRow(stats.subjects[i], shapes) }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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

/** Главная карточка экрана — единственный акцент. */
@Composable
private fun Summary(s: Stats) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.stats_average), style = MaterialTheme.typography.titleMedium)
            Text(formatMean(s.avg), style = MaterialTheme.typography.displayMedium)
            val recent = s.recent
            val before = s.before
            if (recent != null) {
                Text(
                    if (before != null) {
                        val delta = recent - before
                        stringResource(R.string.stats_recent_delta, formatMean(recent), (if (delta >= 0) "+" else "−") + formatMean(kotlin.math.abs(delta)))
                    } else {
                        stringResource(R.string.stats_recent, formatMean(recent))
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            Text(
                stringResource(R.string.stats_marks_count, s.marks) + " · " + stringResource(R.string.stats_absences, s.absences),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** Столбики: сколько отметок каждого балла; цвет — по уровню шкалы. */
@Composable
private fun Distribution(s: Stats) {
    val max = (1..10).maxOf { s.distribution[it] }.coerceAtLeast(1)
    val palette = LocalMarkPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(stringResource(R.string.stats_distribution))
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                for (v in 1..10) {
                    val n = s.distribution[v]
                    Column(
                        Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(if (n > 0) n.toString() else "", style = MaterialTheme.typography.labelSmall)
                        Box(
                            Modifier.fillMaxWidth().height((8 + 88f * n / max).dp.takeIf { n > 0 } ?: 4.dp),
                        ) {
                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                shape = MaterialTheme.shapes.small,
                                color = if (n > 0) palette[markLevel(v)].container else MaterialTheme.colorScheme.surfaceContainerHighest,
                            ) {}
                        }
                        Text(v.toString(), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SubjectRow(s: SubjectStat, shapes: ListItemShapes) {
    val avgText = formatMean(s.avg)
    val colors = LocalMarkPalette.current[markLevel(s.rounded)]
    val trendText = s.trend?.let {
        when {
            it >= 0.5 -> stringResource(R.string.stats_trend_up)
            it <= -0.5 -> stringResource(R.string.stats_trend_down)
            else -> null
        }
    }
    val description = stringResource(R.string.stats_subject_cd, s.name, avgText, s.count)
    SegmentedListItem(
        shapes = shapes,
        colors = segmentedColors(),
        modifier = Modifier.mergedRow().semantics { contentDescription = description },
        supportingContent = {
            val info = stringResource(R.string.stats_subject_info, s.count, s.absences)
            Text(if (trendText != null) "$info · $trendText" else info)
        },
        trailingContent = {
            Surface(shape = MaterialTheme.shapes.medium, color = colors.container, contentColor = colors.onContainer) {
                Text(avgText, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
            }
        },
    ) {
        Text(s.name)
    }
}
