package io.github.akuraneee.reschools.core

import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sqrt

// Статистика по загруженным урокам. Портировано из schools++ (src/stats.js): четвертные и годовые отметки,
// прогноз «сколько нужно до следующей отметки», тренды и динамика.

/** Округление средней до отметки 1–10: дробная часть не меньше [threshold] — вверх. */
fun roundMark(avg: Double, threshold: Double = 0.5): Int {
    val f = floor(avg)
    return (if (avg - f >= threshold - 1e-9) f.toInt() + 1 else f.toInt()).coerceIn(1, 10)
}

/** Четверть: [order] — по порядку начала, [start]/[end] — первый и последний день с уроками. */
data class Quarter(val id: String, val order: Int, val start: LocalDate, val end: LocalDate) {
    val name: String get() = "${order + 1} четверть"
    val short: String get() = "${order + 1} четв."
}

data class Summary(
    val n: Int,
    val sum: Int,
    val avg: Double,
    val rounded: Int,
    val median: Double,
    val mode: Int,
    val min: Int,
    val max: Int,
    val std: Double,
)

fun summary(vals: List<Int>, threshold: Double): Summary? {
    if (vals.isEmpty()) return null
    val n = vals.size
    val sum = vals.sum()
    val avg = sum.toDouble() / n
    val sorted = vals.sorted()
    val median = if (n % 2 == 1) sorted[n / 2].toDouble() else (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0
    val counts = IntArray(11)
    var mode = vals[0]
    for (v in vals) {
        counts[v]++
        if (counts[v] > counts[mode] || (counts[v] == counts[mode] && v > mode)) mode = v
    }
    val std = sqrt(vals.sumOf { (it - avg) * (it - avg) } / n)
    return Summary(n, sum, avg, roundMark(avg, threshold), median, mode, sorted.first(), sorted.last(), std)
}

data class UpStep(val mark: Int, val count: Int)

/** [minSafe] — отметка ниже этой опустит округлённую до [dropTo]; [anySafe] — не опустит никакая. */
data class Down(val minSafe: Int, val anySafe: Boolean, val dropTo: Int)

data class Forecast(val target: Int?, val up: List<UpStep>, val down: Down?)

/** Сколько отметок нужно, чтобы округлённая отметка выросла, и какая отметка её уронит. */
fun forecast(s: Summary, threshold: Double): Forecast {
    val r = s.rounded
    val up = mutableListOf<UpStep>()
    if (r < 10) {
        val target = r + threshold
        for (m in listOf(10, 9, 8, 7, 6, 5)) {
            if (m <= target) break
            val k = max(1, ceil((target * s.n - s.sum) / (m - target) - 1e-9).toInt())
            up += UpStep(m, k)
            if (up.size >= 3) break
        }
    }
    var down: Down? = null
    if (r > 1) {
        val x = ceil((r - 1 + threshold) * (s.n + 1) - s.sum - 1e-9).toInt()
        down = Down(max(1, x), x <= 1, r - 1)
    }
    return Forecast(if (r < 10) r + 1 else null, up, down)
}

data class MarkPoint(val date: LocalDate, val v: Int, val q: String)

/** [avg] — среднее по четверти на эту дату (как «копится» четвертная отметка). */
data class RunningPoint(val date: LocalDate, val v: Int, val q: String, val avg: Double)

/** Годовая — среднее округлённых четвертных; [quarters] — по скольким четвертям есть отметки. */
data class YearMark(val quarters: Int, val exact: Double?, val rounded: Int?)

data class SubjectStats(
    val name: String,
    val key: String,
    val marks: List<MarkPoint>,
    val lessons: Int,
    val absences: Int,
    val byQ: Map<String, Summary?>,
    val all: Summary,
    val year: YearMark,
    val current: Summary?,
    val forecast: Forecast?,
    /** Баллов за 30 дней по линейной регрессии; null — меньше 4 отметок. */
    val trend: Double?,
    val last5: Double?,
    val prev5: Double?,
    val recent30: Double?,
    val before30: Double?,
    val running: List<RunningPoint>,
)

data class QuarterTotal(val subjects: Int, val exact: Double?, val rounded: Double?, val marks: Int)

data class WeekPoint(val week: LocalDate, val avg: Double, val n: Int, val cumulative: Double)

data class WeekdayStat(val label: String, val n: Int, val avg: Double?)

data class Overall(
    val marks: Int,
    val avgAll: Double?,
    val currentExact: Double?,
    val currentRounded: Double?,
    val yearExact: Double?,
    val yearRounded: Double?,
    val lessons: Int,
    val absences: Int,
    val recent30: Double?,
    val before30: Double?,
    val recent30n: Int,
)

class Stats(
    val threshold: Double,
    val quarters: List<Quarter>,
    val current: String?,
    val inProgress: Set<String>,
    val subjects: List<SubjectStats>,
    val noMarks: List<String>,
    val quarterTotals: Map<String, QuarterTotal>,
    val overall: Overall,
    /** distribution[v] — сколько отметок v (индексы 1..10). */
    val distribution: IntArray,
    val weekly: List<WeekPoint>,
    val bestWeek: WeekPoint?,
    val worstWeek: WeekPoint?,
    val byWeekday: List<WeekdayStat>,
) {
    val currentQuarter: Quarter? get() = quarters.firstOrNull { it.id == current }

    /** Столбцов с четвертями в таблицах — не меньше четырёх, как в школе. */
    val quartersCount: Int get() = max(4, quarters.size)
}

private val WEEKDAY_LABELS = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

private fun List<Double>.meanOrNull(): Double? = if (isEmpty()) null else average()

private fun trendPerMonth(marks: List<MarkPoint>): Double? {
    if (marks.size < 4) return null
    val x0 = marks[0].date
    val xs = marks.map { daysBetween(x0, it.date).toDouble() }
    val ys = marks.map { it.v.toDouble() }
    val mx = xs.average()
    val my = ys.average()
    var num = 0.0
    var den = 0.0
    for (i in xs.indices) {
        num += (xs[i] - mx) * (ys[i] - my)
        den += (xs[i] - mx) * (xs[i] - mx)
    }
    return if (den != 0.0) num / den * 30 else 0.0
}

/**
 * Статистика по урокам до [today] включительно. [quarterOf] — четверть недели по её понедельнику
 * (`Diary.links`); уроки недель с неизвестной четвертью пропускаются.
 */
fun computeStats(
    lessons: List<Lesson>,
    quarterOf: (LocalDate) -> String?,
    today: LocalDate,
    threshold: Double = 0.5,
): Stats {
    class L(val l: Lesson, val q: String)

    val all = lessons.mapNotNull { l -> quarterOf(l.date.mondayOf())?.let { L(l, it) } }
        .sortedWith(compareBy({ it.l.date }, { it.l.num }))

    val ranges = HashMap<String, Pair<LocalDate, LocalDate>>()
    for (x in all) {
        val r = ranges[x.q]
        ranges[x.q] = if (r == null) x.l.date to x.l.date else minOf(r.first, x.l.date) to maxOf(r.second, x.l.date)
    }
    val quarters = ranges.entries.sortedBy { it.value.first }
        .mapIndexed { i, e -> Quarter(e.key, i, e.value.first, e.value.second) }

    // Текущая четверть — последняя, в которой уже есть прошедшие уроки.
    val current = quarters.lastOrNull { q -> all.any { it.q == q.id && it.l.date <= today } }?.id
    val inProgress = quarters.filter { it.start <= today && it.end >= today }.map { it.id }.toSet()

    class Acc(val key: String, val name: String) {
        val marks = mutableListOf<MarkPoint>()
        var lessons = 0
        var absences = 0
    }

    val subjMap = LinkedHashMap<String, Acc>()
    val allMarks = mutableListOf<MarkPoint>()
    var lessonsPast = 0
    var absences = 0
    for (x in all) {
        val l = x.l
        val key = subjectKey(l.subject)
        val s = subjMap.getOrPut(key) { Acc(key, subjectTitle(l.subject)) }
        if (l.date <= today) {
            s.lessons++; lessonsPast++
            if (l.absent) { s.absences++; absences++ }
        }
        for (v in l.marks) {
            val m = MarkPoint(l.date, v, x.q)
            s.marks += m
            allMarks += m
        }
    }

    val d30 = today.minusDays(30)
    val d60 = today.minusDays(60)
    val subjects = mutableListOf<SubjectStats>()
    val noMarks = mutableListOf<String>()
    for (s in subjMap.values) {
        if (s.marks.isEmpty()) { noMarks += s.name; continue }
        val byQ = quarters.associate { q -> q.id to summary(s.marks.filter { it.q == q.id }.map { it.v }, threshold) }
        val done = byQ.values.filterNotNull()
        val yearExact = done.map { it.rounded.toDouble() }.meanOrNull()
        val running = mutableListOf<RunningPoint>()
        val acc = HashMap<String, Pair<Int, Int>>()
        for (m in s.marks) {
            val (sum, n) = acc[m.q] ?: (0 to 0)
            acc[m.q] = (sum + m.v) to (n + 1)
            running += RunningPoint(m.date, m.v, m.q, (sum + m.v).toDouble() / (n + 1))
        }
        val values = s.marks.map { it.v.toDouble() }
        val cur = current?.let { byQ[it] }
        subjects += SubjectStats(
            name = s.name,
            key = s.key,
            marks = s.marks,
            lessons = s.lessons,
            absences = s.absences,
            byQ = byQ,
            all = summary(s.marks.map { it.v }, threshold)!!,
            year = YearMark(done.size, yearExact, yearExact?.let { roundMark(it, threshold) }),
            current = cur,
            forecast = cur?.let { forecast(it, threshold) },
            trend = trendPerMonth(s.marks),
            last5 = values.takeLast(5).meanOrNull(),
            prev5 = values.dropLast(5).takeLast(5).meanOrNull(),
            recent30 = s.marks.filter { it.date > d30 && it.date <= today }.map { it.v.toDouble() }.meanOrNull(),
            before30 = s.marks.filter { it.date > d60 && it.date <= d30 }.map { it.v.toDouble() }.meanOrNull(),
            running = running,
        )
    }
    subjects.sortBy { it.name.lowercase() }

    val quarterTotals = quarters.associate { q ->
        val list = subjects.mapNotNull { it.byQ[q.id] }
        q.id to QuarterTotal(
            subjects = list.size,
            exact = list.map { it.avg }.meanOrNull(),
            rounded = list.map { it.rounded.toDouble() }.meanOrNull(),
            marks = list.sumOf { it.n },
        )
    }

    val distribution = IntArray(11)
    allMarks.forEach { distribution[it.v]++ }

    val weekMap = sortedMapOf<LocalDate, MutableList<Int>>()
    for (m in allMarks) weekMap.getOrPut(m.date.mondayOf()) { mutableListOf() } += m.v
    var cs = 0
    var cn = 0
    val weekly = weekMap.map { (week, vals) ->
        cs += vals.sum(); cn += vals.size
        WeekPoint(week, vals.average(), vals.size, cs.toDouble() / cn)
    }
    val solid = weekly.filter { it.n >= 5 }
    val byWeekday = List(7) { i ->
        val v = allMarks.filter { it.date.dayOfWeek.ordinal == i }.map { it.v.toDouble() }
        WeekdayStat(WEEKDAY_LABELS[i], v.size, v.meanOrNull())
    }

    val recent = allMarks.filter { it.date > d30 && it.date <= today }.map { it.v.toDouble() }
    val before = allMarks.filter { it.date > d60 && it.date <= d30 }.map { it.v.toDouble() }
    val withYear = subjects.filter { it.year.quarters > 0 }
    val withCurrent = subjects.filter { it.current != null }

    return Stats(
        threshold = threshold,
        quarters = quarters,
        current = current,
        inProgress = inProgress,
        subjects = subjects,
        noMarks = noMarks,
        quarterTotals = quarterTotals,
        overall = Overall(
            marks = allMarks.size,
            avgAll = allMarks.map { it.v.toDouble() }.meanOrNull(),
            currentExact = withCurrent.map { it.current!!.avg }.meanOrNull(),
            currentRounded = withCurrent.map { it.current!!.rounded.toDouble() }.meanOrNull(),
            yearExact = withYear.mapNotNull { it.year.exact }.meanOrNull(),
            yearRounded = withYear.mapNotNull { it.year.rounded?.toDouble() }.meanOrNull(),
            lessons = lessonsPast,
            absences = absences,
            recent30 = recent.meanOrNull(),
            before30 = before.meanOrNull(),
            recent30n = recent.size,
        ),
        distribution = distribution,
        weekly = weekly,
        bestWeek = solid.maxByOrNull { it.avg },
        worstWeek = solid.minByOrNull { it.avg },
        byWeekday = byWeekday,
    )
}

/** Среднее для сравнения «стало лучше/хуже» — различие меньше [eps] считаем отсутствием изменений. */
fun deltaSign(d: Double, eps: Double = 0.05): Int = if (abs(d) <= eps) 0 else if (d > 0) 1 else -1
