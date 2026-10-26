package io.github.akuraneee.reschools.ui.stats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.akuraneee.reschools.R
import io.github.akuraneee.reschools.core.Ru
import io.github.akuraneee.reschools.core.Stats
import io.github.akuraneee.reschools.core.formatMean

/** «Динамика»: средний балл по неделям и дням недели, изменения по предметам, итоги по четвертям. */
fun LazyListScope.dynamicsItems(st: Stats, onOpenSubject: (String) -> Unit) {
    item(key = "weekly") {
        StatCard(stringResource(R.string.stats_weekly)) {
            val weekly = st.weekly
            LineChart(
                series = listOf(
                    ChartSeries(weekly.map { ChartPoint(it.week, it.avg) }, MaterialTheme.colorScheme.outline, line = true, dots = true, dotRadius = 3.dp),
                    ChartSeries(weekly.map { ChartPoint(it.week, it.cumulative) }, MaterialTheme.colorScheme.primary),
                ),
                bands = st.quarters.bands(),
                description = stringResource(R.string.stats_weekly),
                yMin = minOf(4, kotlin.math.floor(weekly.minOfOrNull { it.avg } ?: 4.0).toInt()),
            )
            Text(
                stringResource(R.string.stats_legend_week) + " · " + stringResource(R.string.stats_legend_cumulative),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    item(key = "weeks") {
        StatCard(stringResource(R.string.stats_weeks)) {
            st.bestWeek?.let { Text(stringResource(R.string.stats_best_week, Ru.dayMonth(it.week), formatMean(it.avg), it.n)) }
            st.worstWeek?.let { Text(stringResource(R.string.stats_worst_week, Ru.dayMonth(it.week), formatMean(it.avg), it.n)) }
            if (st.weekly.isNotEmpty()) {
                Text(
                    stringResource(R.string.stats_weeks_count, st.weekly.size, formatMean(st.overall.marks.toDouble() / st.weekly.size, 1)),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    item(key = "weekdays") {
        StatCard(stringResource(R.string.stats_weekdays)) {
            st.byWeekday.filter { it.n > 0 && it.avg != null }.forEach {
                HBar(it.label, it.avg!!, stringResource(R.string.stats_marks_n, it.n))
            }
        }
    }
    item(key = "changes") {
        StatCard(stringResource(R.string.stats_changes)) {
            TableRow {
                Cell(stringResource(R.string.stats_col_subject), 2.2f, header = true)
                Cell(stringResource(R.string.stats_col_before), 1f, header = true, end = true)
                Cell(stringResource(R.string.stats_col_recent), 1f, header = true, end = true)
                Cell(stringResource(R.string.stats_col_change), 1f, header = true, end = true)
            }
            val rows = st.subjects.map { s ->
                val recent = s.recent30
                val before = s.before30
                s to (if (recent != null && before != null) recent - before else null)
            }.sortedByDescending { it.second ?: -99.0 }
            rows.forEach { (s, d) ->
                TableRow(onClick = { onOpenSubject(s.key) }) {
                    Cell(s.name, 2.2f)
                    Cell(formatMean(s.before30), 1f, end = true)
                    Cell(formatMean(s.recent30), 1f, end = true)
                    Box1(1f) { DeltaLabel(d) }
                }
            }
        }
    }
    item(key = "quarters") {
        StatCard(stringResource(R.string.stats_quarters)) {
            TableRow {
                Cell(stringResource(R.string.stats_col_quarter), 1.6f, header = true)
                Cell(stringResource(R.string.stats_col_exact), 1f, header = true, end = true)
                Cell(stringResource(R.string.stats_col_rounded), 1f, header = true, end = true)
                Cell(stringResource(R.string.stats_col_marks), 0.8f, header = true, end = true)
            }
            st.quarters.forEach { q ->
                val t = st.quarterTotals[q.id]
                TableRow {
                    Cell(q.name + if (q.id in st.inProgress) " · " + stringResource(R.string.stats_in_progress) else "", 1.6f)
                    Cell(formatMean(t?.exact, 3), 1f, end = true)
                    Cell(formatMean(t?.rounded, 3), 1f, end = true)
                    Cell((t?.marks ?: 0).toString(), 0.8f, end = true)
                }
            }
        }
    }
}

@Composable
private fun TableRow(onClick: (() -> Unit)? = null, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    val base = Modifier.fillMaxWidth()
    Row(
        (if (onClick != null) base.clickable(onClick = onClick) else base).padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Cell(text: String, weight: Float, header: Boolean = false, end: Boolean = false) {
    Text(
        text,
        Modifier.weight(weight),
        style = if (header) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium,
        color = if (header) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        textAlign = if (end) TextAlign.End else TextAlign.Start,
    )
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Box1(weight: Float, content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Box(Modifier.weight(weight), contentAlignment = Alignment.CenterEnd) { content() }
}
