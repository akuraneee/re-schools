package io.github.akuraneee.reschools.core

import java.time.LocalDate
import kotlin.math.floor

// Статистика по загруженным урокам (по мотивам schools++, src/stats.js): средние, распределение, динамика.

/** Предмет в статистике; [trend] — средняя последних 5 отметок минус средняя 5 перед ними (null — мало отметок). */
data class SubjectStat(
    val name: String,
    val marks: List<Int>,
    val absences: Int,
    val trend: Double?,
) {
    val count: Int get() = marks.size
    val avg: Double get() = marks.average()
    val rounded: Int get() = roundMark(avg)
}

data class Stats(
    val marks: Int,
    val avg: Double?,
    val absences: Int,
    val lessons: Int,
    /** distribution[v] — сколько отметок v (индексы 1..10, нулевой не используется). */
    val distribution: IntArray,
    /** Средняя за последние 30 дней и за 30 дней перед ними (null — отметок не было). */
    val recent: Double?,
    val before: Double?,
    val subjects: List<SubjectStat>,
)

/** Округление средней до отметки 1–10: от половины и выше — вверх. */
fun roundMark(avg: Double): Int = floor(avg + 0.5).toInt().coerceIn(1, 10)

/** Статистика по урокам с даты [from] (null — все загруженные) по [today] включительно; будущие уроки не считаются. */
fun computeStats(lessons: List<Lesson>, today: LocalDate, from: LocalDate? = null): Stats {
    val past = lessons.filter { it.date <= today && (from == null || it.date >= from) }
        .sortedWith(compareBy({ it.date }, { it.num }))
    val all = past.flatMap { l -> l.marks.map { l.date to it } }
    val distribution = IntArray(11)
    all.forEach { distribution[it.second]++ }

    val d30 = today.minusDays(30)
    val d60 = today.minusDays(60)
    val recent = all.filter { it.first > d30 }.map { it.second }
    val before = all.filter { it.first > d60 && it.first <= d30 }.map { it.second }

    val subjects = past.groupBy { subjectKey(it.subject) }.values.mapNotNull { ls ->
        val marks = ls.flatMap { it.marks }
        if (marks.isEmpty()) return@mapNotNull null
        val trend = if (marks.size >= 6) marks.takeLast(5).average() - marks.dropLast(5).takeLast(5).average() else null
        SubjectStat(subjectTitle(ls.first().subject), marks, ls.count { it.absent }, trend)
    }.sortedBy { it.name }

    return Stats(
        marks = all.size,
        avg = all.map { it.second }.takeIf { it.isNotEmpty() }?.average(),
        absences = past.count { it.absent },
        lessons = past.size,
        distribution = distribution,
        recent = recent.takeIf { it.isNotEmpty() }?.average(),
        before = before.takeIf { it.isNotEmpty() }?.average(),
        subjects = subjects,
    )
}
