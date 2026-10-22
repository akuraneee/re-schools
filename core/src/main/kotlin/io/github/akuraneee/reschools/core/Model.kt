@file:UseSerializers(LocalDateSerializer::class)

package io.github.akuraneee.reschools.core

import java.time.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

@Serializable
data class Pupil(val id: String, val origin: String, val name: String)

/** Урок из недели дневника: [marks] — отметки (1–10), [absent] — «н», [hw] — ДЗ из строки урока. */
@Serializable
data class Lesson(
    val date: LocalDate,
    val num: Int,
    val subject: String,
    val marks: List<Int> = emptyList(),
    val absent: Boolean = false,
    val hw: String = "",
) {
    val marksKey: String get() = marks.joinToString("/")
    val key: String get() = "$date|$num|${subjectKey(subject)}"
}

/** [none] — недели нет в дневнике (каникулы между четвертями, другой учебный год). */
@Serializable
data class Week(
    val lessons: List<Lesson>,
    val dates: List<LocalDate>,
    val at: Long,
    val none: Boolean = false,
)

/** Снимок известных отметок: какие недели загружены и что в них стояло. */
@Serializable
data class Snapshot(val at: Long, val weeks: List<LocalDate>, val marks: Map<String, String>)

/** Состояние «захода» одного ученика: [baseline] — снимок конца прошлого захода, с ним сравниваем новые отметки. */
@Serializable
data class Visit(val baseline: Snapshot? = null, val seen: Snapshot? = null, val lastActive: Long = 0)

/**
 * Где schools.by пишет ДЗ: [Assigned] — в строке урока, на котором его задали (так устроен дневник);
 * [Due] — в строке урока, к которому его надо сделать.
 */
enum class HwMode { Assigned, Due }

@Serializable
data class Settings(val hwMode: HwMode = HwMode.Assigned, val threshold: Double = 0.5)

/** Новая или исправленная отметка; [was] — что стояло раньше. */
data class NewMark(val lesson: Lesson, val was: String?)

/** ДЗ на один предмет в день; сдвоенные уроки объединены ([nums]), [from] — когда ДЗ было задано. */
data class HomeworkItem(
    val subject: String,
    val nums: List<Int>,
    val hw: String,
    val from: LocalDate?,
)

class AuthException : Exception("Нужно войти на schools.by")

class OfflineException : Exception("Нет связи с schools.by")
