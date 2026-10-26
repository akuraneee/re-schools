package io.github.akuraneee.reschools.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.akuraneee.reschools.core.Ru
import java.time.LocalDate

/** Точка графика; [breakBefore] — не соединять с предыдущей (новая четверть), [outline] — обводка точки. */
class ChartPoint(
    val date: LocalDate,
    val y: Double,
    val breakBefore: Boolean = false,
    val color: Color? = null,
    val outline: Color? = null,
)

class ChartSeries(
    val points: List<ChartPoint>,
    val color: Color,
    val line: Boolean = true,
    val dots: Boolean = false,
    val dotRadius: Dp = 3.5.dp,
)

/** Подложка четверти на графике. */
class ChartBand(val start: LocalDate, val end: LocalDate, val label: String)

/** Линейный график по датам: подложка четвертей, сетка по баллам, подписи месяцев. */
@Composable
fun LineChart(
    series: List<ChartSeries>,
    bands: List<ChartBand>,
    description: String,
    modifier: Modifier = Modifier,
    yMin: Int = 1,
    yMax: Int = 10,
    height: Dp = 220.dp,
) {
    val measurer = rememberTextMeasurer()
    val axis = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val grid = MaterialTheme.colorScheme.outlineVariant
    val bandEven = MaterialTheme.colorScheme.surfaceContainerHigh
    val bandOdd = MaterialTheme.colorScheme.surfaceContainerHighest
    Canvas(modifier.fillMaxWidth().height(height).semantics { contentDescription = description }) {
        val dates = series.flatMap { s -> s.points.map { it.date } } + bands.flatMap { listOf(it.start, it.end) }
        if (dates.isEmpty()) return@Canvas
        var x0 = dates.minOf { it.toEpochDay() }.toDouble()
        var x1 = dates.maxOf { it.toEpochDay() }.toDouble()
        if (x1 - x0 < 7) { x0 -= 3; x1 += 4 }
        val left = 26.dp.toPx()
        val right = 8.dp.toPx()
        val top = if (bands.isEmpty()) 8.dp.toPx() else 20.dp.toPx()
        val bottom = 22.dp.toPx()
        val w = size.width - left - right
        val h = size.height - top - bottom
        fun px(d: LocalDate) = left + ((d.toEpochDay() - x0) / (x1 - x0) * w).toFloat()
        fun py(v: Double) = top + ((1 - (v - yMin) / (yMax - yMin)) * h).toFloat()

        bands.forEachIndexed { i, b ->
            val a = px(b.start)
            val z = px(b.end)
            drawRect(if (i % 2 == 0) bandEven else bandOdd, Offset(a, top), Size(maxOf(2f, z - a), h))
            val label = measurer.measure(b.label, axis)
            drawText(label, topLeft = Offset((a + z) / 2 - label.size.width / 2f, 2.dp.toPx()))
        }
        for (v in yMin..yMax) {
            drawLine(grid, Offset(left, py(v.toDouble())), Offset(size.width - right, py(v.toDouble())), strokeWidth = 1f)
            val label = measurer.measure(v.toString(), axis)
            drawText(label, topLeft = Offset(left - 6.dp.toPx() - label.size.width, py(v.toDouble()) - label.size.height / 2f))
        }
        // подписи месяцев
        var m = LocalDate.ofEpochDay(x0.toLong()).let { if (it.dayOfMonth > 1) it.withDayOfMonth(1).plusMonths(1) else it }
        val end = LocalDate.ofEpochDay(x1.toLong())
        while (!m.isAfter(end)) {
            val x = px(m)
            drawLine(grid, Offset(x, top + h), Offset(x, top + h + 4.dp.toPx()), strokeWidth = 1f)
            drawText(measurer.measure(Ru.monthShort(m.monthValue), axis), topLeft = Offset(x + 2.dp.toPx(), top + h + 4.dp.toPx()))
            m = m.plusMonths(1)
        }
        for (s in series) {
            if (s.line && s.points.size > 1) {
                val path = Path()
                s.points.forEachIndexed { i, p ->
                    if (i == 0 || p.breakBefore) path.moveTo(px(p.date), py(p.y)) else path.lineTo(px(p.date), py(p.y))
                }
                drawPath(path, s.color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
            }
            if (s.dots) {
                for (p in s.points) {
                    val c = Offset(px(p.date), py(p.y))
                    drawCircle(p.color ?: s.color, s.dotRadius.toPx(), c)
                    p.outline?.let { drawCircle(it, s.dotRadius.toPx(), c, style = Stroke(1.dp.toPx())) }
                }
            }
        }
    }
}

/** Мини-график скользящего среднего у карточки предмета. */
@Composable
fun Sparkline(values: List<Double>, modifier: Modifier = Modifier) {
    if (values.size < 2) return
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier.size(84.dp, 24.dp)) {
        val min = values.min()
        val max = values.max()
        val lo = minOf(min, max - 1)
        val hi = maxOf(max, min + 1)
        fun px(i: Int) = 2.dp.toPx() + i.toFloat() / (values.size - 1) * (size.width - 4.dp.toPx())
        fun py(v: Double) = 2.dp.toPx() + ((1 - (v - lo) / (hi - lo)) * (size.height - 4.dp.toPx())).toFloat()
        val path = Path()
        values.forEachIndexed { i, v -> if (i == 0) path.moveTo(px(i), py(v)) else path.lineTo(px(i), py(v)) }
        drawPath(path, color, style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(color, 2.5.dp.toPx(), Offset(px(values.size - 1), py(values.last())))
    }
}
