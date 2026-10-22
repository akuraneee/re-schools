package io.github.akuraneee.reschools.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.akuraneee.reschools.R
import io.github.akuraneee.reschools.core.Ru
import io.github.akuraneee.reschools.core.Stats
import io.github.akuraneee.reschools.core.SubjectStats
import io.github.akuraneee.reschools.core.formatMean
import io.github.akuraneee.reschools.core.parseMarkText
import io.github.akuraneee.reschools.core.roundMark
import io.github.akuraneee.reschools.ui.components.MarkBadge
import io.github.akuraneee.reschools.ui.components.SectionHeader
import io.github.akuraneee.reschools.ui.theme.LocalMarkPalette

fun List<io.github.akuraneee.reschools.core.Quarter>.bands() =
    map { ChartBand(it.start, it.end, it.short) }

/** «Предмет»: выбор предмета, показатели, что нужно до следующей отметки, «а что если», график и отметки по четвертям. */
fun LazyListScope.subjectItems(
    st: Stats,
    subject: SubjectStats,
    onPick: (String) -> Unit,
    whatIf: String,
    onWhatIf: (String) -> Unit,
) {
    item(key = "pick") {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(st.subjects, key = { it.key }) { s ->
                FilterChip(selected = s.key == subject.key, onClick = { onPick(s.key) }, label = { Text(s.name) })
            }
        }
    }
    item(key = "kpis") {
        val c = subject.current
        val cur = st.currentQuarter
        val q = cur?.short ?: stringResource(R.string.stats_total)
        val trend = subject.trend
        KpiGrid(
            listOf(
                KpiData(
                    q, if (c != null) c.rounded.toString() else "—",
                    if (c != null) stringResource(R.string.stats_marks_n, c.n) else stringResource(R.string.stats_no_marks_yet),
                    if (c != null) formatMean(c.avg, 3) else null,
                    accent = true,
                ),
                KpiData(
                    stringResource(R.string.stats_year_kpi), subject.year.rounded?.toString() ?: "—",
                    stringResource(R.string.stats_year_sub, subject.year.quarters, st.quartersCount),
                    subject.year.exact?.let { formatMean(it, 3) },
                ),
                KpiData(stringResource(R.string.stats_all_year), formatMean(subject.all.avg), stringResource(R.string.stats_marks_n, subject.all.n)),
                KpiData(
                    stringResource(R.string.stats_median_mode), "${formatMean(subject.all.median, 1)} · ${subject.all.mode}",
                    stringResource(R.string.stats_min_max, subject.all.min, subject.all.max),
                ),
                KpiData(stringResource(R.string.stats_spread), formatMean(subject.all.std), stringResource(R.string.stats_spread_sub)),
                KpiData(
                    stringResource(R.string.stats_trend),
                    if (trend == null) "—" else (if (trend > 0.25) "▲ " else if (trend < -0.25) "▼ " else "● ") + stringResource(R.string.stats_trend_per_month, signed(trend, 1)),
                    if (subject.prev5 != null) stringResource(R.string.stats_trend_sub, formatMean(subject.last5), formatMean(subject.prev5)) else null,
                ),
                KpiData(
                    stringResource(R.string.stats_kpi_absences), subject.absences.toString(),
                    stringResource(R.string.stats_absences_sub, subject.lessons),
                ),
            ),
        )
    }
    item(key = "need") {
        StatCard(stringResource(R.string.stats_need)) {
            val f = subject.forecast
            val c = subject.current
            if (f == null || c == null) {
                Text(stringResource(R.string.stats_need_none), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val target = f.target
                if (target != null && f.up.isNotEmpty()) {
                    Text(stringResource(R.string.stats_need_up, target, f.up.joinToString(" / ") { "${it.count}×${it.mark}" }))
                } else if (target == null) {
                    Text(stringResource(R.string.stats_need_max))
                }
                f.down?.let { d ->
                    Text(
                        if (d.anySafe) stringResource(R.string.stats_need_safe, c.rounded)
                        else stringResource(R.string.stats_need_down, d.minSafe, d.dropTo),
                    )
                }
            }
        }
    }
    item(key = "whatif") {
        StatCard(stringResource(R.string.stats_whatif)) {
            OutlinedTextField(
                value = whatIf,
                onValueChange = onWhatIf,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.stats_whatif_hint)) },
            )
            val extra = parseMarkText(whatIf).marks
            if (extra.isEmpty()) {
                Text(stringResource(R.string.stats_whatif_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val c = subject.current
                val n = (c?.n ?: 0) + extra.size
                val avg = ((c?.sum ?: 0) + extra.sum()).toDouble() / n
                val rounded = roundMark(avg, st.threshold)
                Text(
                    if (c != null) stringResource(R.string.stats_whatif_result, formatMean(avg, 3), rounded, formatMean(c.avg, 3))
                    else stringResource(R.string.stats_whatif_result_new, formatMean(avg, 3), rounded),
                )
            }
        }
    }
    item(key = "chart") {
        val palette = LocalMarkPalette.current
        var last: String? = null
        val avgPoints = subject.running.map { r ->
            val p = ChartPoint(r.date, r.avg, breakBefore = r.q != last)
            last = r.q
            p
        }
        val dots = subject.marks.map {
            val colors = palette[io.github.akuraneee.reschools.core.markLevel(it.v)]
            ChartPoint(it.date, it.v.toDouble(), color = colors.container, outline = colors.onContainer)
        }
        StatCard(stringResource(R.string.stats_chart)) {
            LineChart(
                series = listOf(
                    ChartSeries(dots, MaterialTheme.colorScheme.outline, line = false, dots = true, dotRadius = 4.dp),
                    ChartSeries(avgPoints, MaterialTheme.colorScheme.primary),
                ),
                bands = st.quarters.bands(),
                description = stringResource(R.string.stats_chart),
            )
            Text(
                stringResource(R.string.stats_legend_mark) + " · " + stringResource(R.string.stats_legend_avg),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    item(key = "byq") { ByQuarter(st, subject) }
}

@OptIn(ExperimentalLayoutApi::class) // FlowRow
@Composable
private fun ByQuarter(st: Stats, s: SubjectStats) {
    StatCard(stringResource(R.string.stats_by_quarter)) {
        st.quarters.forEach { q ->
            val sq = s.byQ[q.id]
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    if (sq != null) {
                        MarkBadge(sq.rounded, compact = true)
                        Text(
                            stringResource(R.string.stats_quarter_line, q.name, formatMean(sq.avg, 3), sq.n, sq.min, sq.max),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    } else {
                        Text(q.name + " · " + stringResource(R.string.stats_quarter_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    s.marks.filter { it.q == q.id }.forEach { m ->
                        Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                            MarkBadge(m.v, compact = true)
                            Text(Ru.dayMonth(m.date), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
