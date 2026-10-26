package io.github.akuraneee.reschools.core

import java.time.LocalDate

// Что идёт сейчас по расписанию звонков: урок, перемена или неурочное время. Из re:schools web (lib/logic.js).

/** Урок дня со временем по звонкам; несколько уроков с одним номером (группы) — в одном слоте. Время — минуты от полуночи. */
data class Slot(val num: Int, val lessons: List<Lesson>, val start: Int, val end: Int)

sealed interface NowStatus {
    /** Идёт урок [cur]; [next] — следующий урок дня. */
    data class InLesson(val cur: Slot, val next: Slot?) : NowStatus

    /** Перемена между [prev] и [next]. */
    data class Break(val prev: Slot, val next: Slot) : NowStatus

    /** Уроки ещё не начались. */
    data class Before(val next: Slot) : NowStatus

    /** Уроки на сегодня закончились. */
    data object After : NowStatus

    /** Сегодня уроков нет. */
    data object NoLessons : NowStatus
}

fun daySchedule(lessons: List<Lesson>, date: LocalDate, bells: Bells): List<Slot> =
    lessons.filter { it.date == date }.groupBy { it.num }.toSortedMap()
        .map { (num, ls) -> Slot(num, ls, bells.start(num), bells.end(num)) }

/** [nowSeconds] — секунды от полуночи. */
fun nowStatus(lessons: List<Lesson>, today: LocalDate, nowSeconds: Int, bells: Bells): NowStatus {
    val day = daySchedule(lessons, today, bells)
    if (day.isEmpty()) return NowStatus.NoLessons
    for ((i, x) in day.withIndex()) {
        if (nowSeconds >= x.start * 60 && nowSeconds < x.end * 60) return NowStatus.InLesson(x, day.getOrNull(i + 1))
        if (nowSeconds < x.start * 60) return if (i == 0) NowStatus.Before(x) else NowStatus.Break(day[i - 1], x)
    }
    return NowStatus.After
}

/** Минут до [untilMinute] (с округлением вверх: «осталось 1 мин», пока не прозвенел звонок). */
fun minutesLeft(nowSeconds: Int, untilMinute: Int): Int = maxOf(0, (untilMinute * 60 - nowSeconds + 59) / 60)

/** Доля прошедшего от [fromMinute] до [toMinute], 0..1. */
fun elapsedFraction(nowSeconds: Int, fromMinute: Int, toMinute: Int): Float =
    ((nowSeconds - fromMinute * 60).toFloat() / ((toMinute - fromMinute) * 60).coerceAtLeast(1)).coerceIn(0f, 1f)
