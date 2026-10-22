package io.github.akuraneee.reschools.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.akuraneee.reschools.R
import io.github.akuraneee.reschools.core.Summary
import io.github.akuraneee.reschools.core.formatMean
import io.github.akuraneee.reschools.core.markLevel
import io.github.akuraneee.reschools.ui.components.MarkBadge
import io.github.akuraneee.reschools.ui.components.SectionHeader
import io.github.akuraneee.reschools.ui.theme.LocalMarkPalette
import kotlin.math.abs

/** Со знаком: +0,25 / −0,25. */
fun signed(x: Double, digits: Int = 2): String = (if (x >= 0) "+" else "−") + formatMean(abs(x), digits)

/** Карточка раздела с заголовком. */
@Composable
fun StatCard(title: String, modifier: Modifier = Modifier, subtitle: String? = null, content: @Composable () -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(title, subtitle = subtitle)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
        }
    }
}

class KpiData(val title: String, val value: String, val sub: String? = null, val small: String? = null, val accent: Boolean = false)

/** Показатели по два в ряд; [KpiData.accent] выделяет главный (один на экран). */
@Composable
fun KpiGrid(items: List<KpiData>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { Kpi(it, Modifier.weight(1f).fillMaxHeight()) }
                if (row.size == 1) Box(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Kpi(k: KpiData, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = if (k.accent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = if (k.accent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(k.title, style = MaterialTheme.typography.labelLarge)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(k.value, style = MaterialTheme.typography.headlineMedium)
                if (k.small != null) Text(k.small, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 4.dp))
            }
            if (k.sub != null) Text(k.sub, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Тренд в баллах за месяц: ▲ растёт, ▼ снижается, ● без изменений. */
@Composable
fun TrendLabel(trend: Double?, modifier: Modifier = Modifier) {
    if (trend == null) {
        Text("—", modifier, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val (mark, color) = when {
        trend > 0.25 -> "▲" to MaterialTheme.colorScheme.primary
        trend < -0.25 -> "▼" to MaterialTheme.colorScheme.error
        else -> "●" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        "$mark ${stringResource(R.string.stats_trend_per_month, signed(trend, 1))}",
        modifier,
        style = MaterialTheme.typography.labelLarge,
        color = color,
    )
}

/** Изменение средней: зелёного нет — только акцент / ошибка из темы. */
@Composable
fun DeltaLabel(delta: Double?, modifier: Modifier = Modifier) {
    if (delta == null) {
        Text("—", modifier, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val color: Color = when {
        delta > 0.05 -> MaterialTheme.colorScheme.primary
        delta < -0.05 -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(signed(delta), modifier, style = MaterialTheme.typography.labelLarge, color = color)
}

/** Четвертная отметка и точное среднее; «—», если отметок нет. */
@Composable
fun QuarterValue(s: Summary?, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (s == null) {
            Text("—", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            MarkBadge(s.rounded, compact = true)
            Text(formatMean(s.avg), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Столбики: сколько отметок каждого балла; цвет — по уровню шкалы. */
@Composable
fun Distribution(counts: IntArray) {
    val max = (1..10).maxOf { counts[it] }.coerceAtLeast(1)
    val palette = LocalMarkPalette.current
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        for (v in 1..10) {
            val n = counts[v]
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(if (n > 0) n.toString() else " ", style = MaterialTheme.typography.labelSmall)
                Surface(
                    modifier = Modifier.fillMaxWidth().height(if (n > 0) (8 + 88f * n / max).dp else 4.dp),
                    shape = MaterialTheme.shapes.small,
                    color = if (n > 0) palette[markLevel(v)].container else MaterialTheme.colorScheme.surfaceContainerHighest,
                ) { Box(Modifier.fillMaxSize()) }
                Text(v.toString(), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
            }
        }
    }
}

/** Горизонтальная полоска «среднее по дням недели». */
@Composable
fun HBar(label: String, value: Double, note: String) {
    val palette = LocalMarkPalette.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, Modifier.padding(end = 4.dp), style = MaterialTheme.typography.labelLarge)
        Box(Modifier.weight(1f).height(14.dp)) {
            Surface(Modifier.fillMaxSize(), shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerHighest) {}
            Surface(
                Modifier.fillMaxHeight().fillMaxWidth((value / 10).toFloat().coerceIn(0.02f, 1f)),
                shape = MaterialTheme.shapes.small,
                color = palette[markLevel(io.github.akuraneee.reschools.core.roundMark(value))].container,
            ) {}
        }
        Text(formatMean(value), style = MaterialTheme.typography.labelLarge)
        Text(note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
