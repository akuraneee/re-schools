package io.github.akuraneee.reschools.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.akuraneee.reschools.R
import io.github.akuraneee.reschools.core.Stats
import io.github.akuraneee.reschools.core.SubjectStats
import io.github.akuraneee.reschools.core.formatMean
import io.github.akuraneee.reschools.ui.components.MarkBadge
import io.github.akuraneee.reschools.ui.components.SectionHeader

private val SORTS = listOf(
    "name" to R.string.stats_sort_name,
    "year" to R.string.stats_sort_year,
    "q" to R.string.stats_sort_current,
    "trend" to R.string.stats_sort_trend,
    "abs" to R.string.stats_sort_absences,
)

private fun sortedSubjects(st: Stats, key: String): List<SubjectStats> = when (key) {
    "year" -> st.subjects.sortedByDescending { it.year.exact ?: -1.0 }
    "q" -> st.subjects.sortedByDescending { s -> st.current?.let { s.byQ[it]?.avg } ?: -1.0 }
    "trend" -> st.subjects.sortedByDescending { it.trend ?: -99.0 }
    "abs" -> st.subjects.sortedByDescending { it.absences }
    else -> st.subjects
}

/** «Обзор»: средние, четвертные и годовые отметки по предметам, предметы рядом с границей, распределение. */
fun LazyListScope.overviewItems(st: Stats, sortKey: String, onSort: (String) -> Unit, onOpenSubject: (String) -> Unit) {
    item(key = "kpis") {
        val o = st.overall
        val cur = st.currentQuarter
        val recent = o.recent30
        val before = o.before30
        val pct = if (o.lessons > 0) o.absences * 100.0 / o.lessons else 0.0
        KpiGrid(
            listOf(
                KpiData(
                    if (cur != null) stringResource(R.string.stats_kpi_quarter, cur.short) else stringResource(R.string.stats_total),
                    formatMean(o.currentExact),
                    stringResource(R.string.stats_kpi_quarter_sub),
                    stringResource(R.string.stats_kpi_rounded, formatMean(o.currentRounded)),
                    accent = true,
                ),
                KpiData(
                    stringResource(R.string.stats_kpi_year), formatMean(o.yearExact),
                    stringResource(R.string.stats_kpi_year_sub), stringResource(R.string.stats_kpi_rounded, formatMean(o.yearRounded)),
                ),
                KpiData(stringResource(R.string.stats_kpi_all), formatMean(o.avgAll), stringResource(R.string.stats_kpi_all_sub, o.marks)),
                KpiData(
                    stringResource(R.string.stats_kpi_30), formatMean(o.recent30),
                    if (recent != null && before != null) stringResource(R.string.stats_kpi_30_sub, signed(recent - before))
                    else stringResource(R.string.stats_kpi_30_count, o.recent30n),
                ),
                KpiData(
                    stringResource(R.string.stats_kpi_absences), o.absences.toString(),
                    stringResource(R.string.stats_kpi_absences_sub, o.lessons, formatMean(pct, 1)),
                ),
            ),
        )
    }
    item(key = "subjects-head") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeader(stringResource(R.string.stats_subjects))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(SORTS.filter { it.first != "q" || st.current != null }, key = { it.first }) { (key, label) ->
                    FilterChip(selected = key == sortKey, onClick = { onSort(key) }, label = { Text(stringResource(label)) })
                }
            }
        }
    }
    items(sortedSubjects(st, sortKey), key = { "s-" + it.key }) { s -> SubjectCard(st, s, onClick = { onOpenSubject(s.key) }) }
    item(key = "total") { TotalCard(st) }
    item(key = "notes") {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val style = MaterialTheme.typography.bodySmall
            val color = MaterialTheme.colorScheme.onSurfaceVariant
            Text(stringResource(R.string.stats_round_note, formatMean(st.threshold)), style = style, color = color)
            if (st.noMarks.isNotEmpty()) Text(stringResource(R.string.stats_no_marks, st.noMarks.joinToString(", ")), style = style, color = color)
        }
    }
    item(key = "near") { NearCards(st) }
    item(key = "distribution") {
        StatCard(stringResource(R.string.stats_distribution)) { Distribution(st.distribution) }
    }
}

@Composable
private fun SubjectCard(st: Stats, s: SubjectStats, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(s.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.stats_year), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val yearRounded = s.year.rounded
                if (yearRounded != null) {
                    MarkBadge(yearRounded)
                    Text(formatMean(s.year.exact), style = MaterialTheme.typography.labelMedium)
                } else {
                    Text("—")
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                st.quarters.forEach { q ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            q.short,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (q.id == st.current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        QuarterValue(s.byQ[q.id])
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val foot = stringResource(R.string.stats_card_foot, s.all.n, formatMean(s.all.avg)) +
                    (if (s.absences > 0) " · " + stringResource(R.string.stats_card_absences, s.absences) else "")
                Text(foot, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TrendLabel(s.trend)
                Sparkline(s.running.takeLast(15).map { it.avg })
            }
        }
    }
}

@Composable
private fun TotalCard(st: Stats) {
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.stats_total), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.stats_year), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatMean(st.overall.yearExact), style = MaterialTheme.typography.titleMedium)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                st.quarters.forEach { q ->
                    val t = st.quarterTotals[q.id]
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            q.short,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (q.id == st.current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(if (t != null && t.subjects > 0) formatMean(t.exact) else "—", style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }
    }
}

/** «Почти следующая отметка» и «На грани понижения» — по текущей четверти. */
@Composable
private fun NearCards(st: Stats) {
    val near = mutableListOf<SubjectStats>()
    val risk = mutableListOf<SubjectStats>()
    for (s in st.subjects) {
        val c = s.current ?: continue
        val toUp = if (c.rounded < 10) c.rounded + st.threshold - c.avg else Double.POSITIVE_INFINITY
        val toDown = c.avg - (c.rounded - 1 + st.threshold)
        if (toUp <= 0.2) near += s
        if (c.rounded > 1 && toDown < 0.15) risk += s
    }
    near.sortBy { it.current!!.let { c -> c.rounded + st.threshold - c.avg } }
    risk.sortBy { it.current!!.let { c -> c.avg - (c.rounded - 1 + st.threshold) } }
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        StatCard(stringResource(R.string.stats_near), subtitle = st.currentQuarter?.short) {
            if (near.isEmpty()) Text(stringResource(R.string.stats_near_none), color = MaterialTheme.colorScheme.onSurfaceVariant)
            near.forEach { s ->
                val up = s.forecast?.up?.firstOrNull() ?: return@forEach
                Text(stringResource(R.string.stats_near_item, s.name, formatMean(s.current!!.avg), s.current!!.rounded + 1, up.count, up.mark))
            }
        }
        StatCard(stringResource(R.string.stats_risk)) {
            if (risk.isEmpty()) Text(stringResource(R.string.stats_risk_none), color = MaterialTheme.colorScheme.onSurfaceVariant)
            risk.forEach { s ->
                val down = s.forecast?.down ?: return@forEach
                Text(stringResource(R.string.stats_risk_item, s.name, formatMean(s.current!!.avg), down.minSafe, down.dropTo))
            }
        }
    }
}
