package io.github.akuraneee.reschools.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

fun norm(s: String?): String = (s ?: "").replace(Regex("\\s+"), " ").trim()

fun subjectKey(s: String): String =
    norm(s).lowercase().replace('ё', 'е').replace(Regex("[.:]+$"), "")

// Сокращения из дневника → полные названия (ключ — subjectKey).
private val SUBJECT_NAMES = mapOf(
    "матем" to "Математика", "алг" to "Алгебра", "геом" to "Геометрия",
    "рус. яз" to "Русский язык", "рус. лит" to "Русская литература",
    "бел. яз" to "Белорусский язык", "бел. лит" to "Белорусская литература",
    "англ. яз" to "Английский язык", "нем. яз" to "Немецкий язык", "фр. яз" to "Французский язык",
    "кит. яз" to "Китайский язык", "ин. яз" to "Иностранный язык",
    "физ. к. и зд" to "Физкультура и здоровье", "информ" to "Информатика", "информ. час" to "Информационный час",
    "обществов" to "Обществоведение",
    "ист. бел. в кон. всем. ист" to "История Беларуси в контексте всемирной истории",
    "всем. ист" to "Всемирная история", "ист. бел" to "История Беларуси",
    "дп/мп" to "Допризывная / медицинская подготовка", "черч" to "Черчение", "астр" to "Астрономия",
    "тр. обуч" to "Трудовое обучение", "иск" to "Искусство", "чел. и мир" to "Человек и мир",
)

fun subjectTitle(s: String): String = SUBJECT_NAMES[subjectKey(s)] ?: norm(s).removeSuffix(".")

/** Уровень отметки по белорусской 10-балльной шкале: 1 (1–2) … 5 (9–10); 0 — нет отметки. */
fun markLevel(v: Int?): Int = when {
    v == null -> 0
    v <= 2 -> 1
    v <= 4 -> 2
    v <= 6 -> 3
    v <= 8 -> 4
    else -> 5
}

fun mean(values: List<Int>): Double? = if (values.isEmpty()) null else values.sum().toDouble() / values.size

/** Среднее с запятой, как привычно в дневнике: 7,50. */
fun formatMean(x: Double?, digits: Int = 2): String =
    if (x == null || x.isNaN()) "—" else "%.${digits}f".format(java.util.Locale.ROOT, x).replace('.', ',')

/** Русские даты и времена для интерфейса. */
object Ru {
    private val WEEKDAYS_FULL = listOf("Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье")
    private val WEEKDAYS_ACC = listOf("понедельник", "вторник", "среду", "четверг", "пятницу", "субботу", "воскресенье")
    private val WEEKDAYS_SHORT = listOf("пн", "вт", "ср", "чт", "пт", "сб", "вс")
    private val MONTHS_GEN = listOf(
        "января", "февраля", "марта", "апреля", "мая", "июня",
        "июля", "августа", "сентября", "октября", "ноября", "декабря",
    )
    private val MONTHS_SHORT = listOf("янв", "фев", "мар", "апр", "май", "июн", "июл", "авг", "сен", "окт", "ноя", "дек")

    fun dayName(d: LocalDate) = WEEKDAYS_FULL[d.dayOfWeek.ordinal]

    /** «20 октября» */
    fun dateLong(d: LocalDate) = "${d.dayOfMonth} ${MONTHS_GEN[d.monthValue - 1]}"

    /** «8:05» по минутам от полуночи */
    fun clock(minutes: Int) = "%d:%02d".format(minutes / 60 % 24, minutes % 60)

    /** «12 мин», «1 ч 5 мин» */
    fun duration(minutes: Int) = if (minutes < 60) "$minutes мин" else if (minutes % 60 == 0) "${minutes / 60} ч" else "${minutes / 60} ч ${minutes % 60} мин"

    /** «20 окт» */
    fun dayMonth(d: LocalDate) = "${d.dayOfMonth} ${MONTHS_SHORT[d.monthValue - 1]}"

    /** «окт» по номеру месяца 1–12 */
    fun monthShort(month: Int) = MONTHS_SHORT[month - 1]

    /** «вт, 20 окт» */
    fun dateShort(d: LocalDate) = "${WEEKDAYS_SHORT[d.dayOfWeek.ordinal]}, ${d.dayOfMonth} ${MONTHS_SHORT[d.monthValue - 1]}"

    /** «на завтра», «на послезавтра», «на понедельник» */
    fun relDay(target: LocalDate, today: LocalDate) = when (daysBetween(today, target)) {
        1 -> "на завтра"
        2 -> "на послезавтра"
        else -> "на ${WEEKDAYS_ACC[target.dayOfWeek.ordinal]}"
    }

    /** «12 – 17 октября» или «26 октября – 1 ноября» (неделя — с понедельника по субботу). */
    fun weekRange(monday: LocalDate): String {
        val sat = monday.plusDays(5)
        return if (monday.monthValue == sat.monthValue) "${monday.dayOfMonth} – ${sat.dayOfMonth} ${MONTHS_GEN[sat.monthValue - 1]}"
        else "${dateLong(monday)} – ${dateLong(sat)}"
    }

    fun time(ts: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val t = Instant.ofEpochMilli(ts).atZone(zone)
        return "%02d:%02d".format(t.hour, t.minute)
    }

    /** «сегодня в 08:15», «вчера в 20:40», «18 октября в 09:00» */
    fun whenText(ts: Long, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): String {
        val d = Instant.ofEpochMilli(ts).atZone(zone).toLocalDate()
        return when (d) {
            today -> "сегодня в ${time(ts, zone)}"
            today.minusDays(1) -> "вчера в ${time(ts, zone)}"
            else -> "${dateLong(d)} в ${time(ts, zone)}"
        }
    }
}
