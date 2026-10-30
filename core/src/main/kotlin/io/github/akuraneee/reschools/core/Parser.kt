package io.github.akuraneee.reschools.core

import java.net.URI
import java.time.LocalDate
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

// Разбор HTML недели дневника schools.by. Взято из schools++ (src/parser.js), добавлены ДЗ и список дней.
//
// Ожидаемая разметка (страница /pupil/<id>/dnevnik/quarter/<qid>/week/<YYYY-MM-DD>):
//   div.db_day                    — один день недели
//     table.db_table
//       thead th / .db_period     — «Понедельник, 2 сентября»
//       tr > td.lesson            — «1. Математика»
//            td.mark              — <div class="mark_box"><strong>9</strong></div>
//            td.ht .ht-text       — ДЗ
//
// Если разметка на сайте отличается, правьте селекторы в [Sel].
object Sel {
    const val DAYS_BLOCK = ".db_days"
    const val DAY = ".db_day"
    const val DAY_FALLBACK = "table.db_table"
    const val DAY_HEADER = ".db_period, .db_day_title, thead th, th, caption, h3, h4"
    const val ROW = "tr"
    const val LESSON = "td.lesson, td.db_lesson, td.lesson_name"
    const val MARK = "td.mark, td.db_mark, td.marks"
    const val HOMEWORK = "td.ht .ht-text"
}

data class ParsedMarks(val marks: List<Int>, val absent: Boolean)

data class ParsedWeek(val days: Int, val dates: List<LocalDate>, val lessons: List<Lesson>)

private val MONTHS = mapOf(
    "января" to 1, "февраля" to 2, "марта" to 3, "апреля" to 4, "мая" to 5, "июня" to 6,
    "июля" to 7, "августа" to 8, "сентября" to 9, "октября" to 10, "ноября" to 11, "декабря" to 12,
    // белорусский интерфейс
    "студзеня" to 1, "лютага" to 2, "сакавіка" to 3, "красавіка" to 4, "траўня" to 5, "чэрвеня" to 6,
    "ліпеня" to 7, "жніўня" to 8, "верасня" to 9, "кастрычніка" to 10, "лістапада" to 11, "снежня" to 12,
)

private val ABSENCE = setOf("н", "нб", "н/б", "нп", "ув", "отс", "адс", "н/у")

private fun safeDate(y: Int, m: Int, d: Int): LocalDate? = runCatching { LocalDate.of(y, m, d) }.getOrNull()

/** Учебный год, к которому относится [today]: сентябрь–август. Возвращает год начала. */
private fun schoolYearStart(today: LocalDate) = if (today.monthValue >= 8) today.year else today.year - 1

// Подбираем год так, чтобы дата оказалась ближе всего к опорной (неделе из URL).
private fun withYear(day: Int, month: Int, ref: LocalDate?, today: LocalDate): LocalDate? {
    if (ref == null) {
        val sy = schoolYearStart(today)
        return safeDate(if (month >= 8) sy else sy + 1, month, day)
    }
    return listOf(ref.year - 1, ref.year, ref.year + 1)
        .mapNotNull { safeDate(it, month, day) }
        .minByOrNull { kotlin.math.abs(daysBetween(it, ref)) }
}

fun parseDayDate(text: String, ref: LocalDate? = null, today: LocalDate = LocalDate.now()): LocalDate? {
    val t = norm(text).lowercase()
    Regex("(\\d{1,2})\\.(\\d{1,2})(?:\\.(\\d{2,4}))?").find(t)?.let { m ->
        val day = m.groupValues[1].toInt()
        val month = m.groupValues[2].toInt()
        if (month in 1..12 && day in 1..31) {
            val y = m.groupValues[3]
            if (y.isNotEmpty()) return safeDate(if (y.length == 2) 2000 + y.toInt() else y.toInt(), month, day)
            return withYear(day, month, ref, today)
        }
    }
    Regex("(\\d{1,2})\\s+([а-яёіў]+)").find(t)?.let { m ->
        val month = MONTHS[m.groupValues[2]] ?: return@let
        return withYear(m.groupValues[1].toInt(), month, ref, today)
    }
    return null
}

fun parseMarkText(text: String): ParsedMarks {
    val marks = mutableListOf<Int>()
    var absent = false
    val tokenChars = Regex("[^0-9a-zа-яёіў/]")
    for (raw in norm(text).lowercase().split(Regex("[\\s,;]+")).filter { it.isNotEmpty() }) {
        val tok = raw.replace(tokenChars, "")
        if (tok.isEmpty()) continue
        if (tok in ABSENCE) { absent = true; continue }
        for (part in tok.split('/')) {
            if (part in ABSENCE) { absent = true; continue }
            if (Regex("^\\d{1,2}$").matches(part) && part.toInt() in 1..10) marks += part.toInt()
        }
    }
    return ParsedMarks(marks, absent)
}

private fun markCellText(cell: Element): String {
    val strong = cell.select("strong, b")
    if (strong.isNotEmpty()) return strong.joinToString(" ") { it.text() }
    val c = cell.clone()
    c.select("[class*=comment], [class*=tooltip], [class*=hint], [class*=popup], script, style").remove()
    return c.text()
}

private data class LessonCell(val num: Int?, val subject: String)

private fun parseLessonCell(cell: Element): LessonCell {
    val full = norm(cell.text())
    val numMatch = Regex("^(\\d{1,2})\\s*[.)]?").find(full)
    val candidates = cell.select("a, span, div").map { norm(it.text()) } + full
    val prefix = Regex("^\\d{1,2}\\s*[.)]?\\s*")
    val letters = Regex("[A-Za-zА-Яа-яЁёІіЎў]")
    val subject = candidates.map { it.replace(prefix, "").trim() }.firstOrNull { letters.containsMatchIn(it) } ?: ""
    return LessonCell(numMatch?.groupValues?.get(1)?.toInt(), subject)
}

private val HIDDEN_STYLE = Regex("display\\s*:\\s*none|visibility\\s*:\\s*hidden", RegexOption.IGNORE_CASE)

private fun isHidden(el: Element) = el.hasAttr("hidden") || el.hasClass("hidden") || HIDDEN_STYLE.containsMatchIn(el.attr("style"))

// Ответ недели содержит два блока .db_days: скрытый (display:none) и видимый, и отметки в них
// различаются. Пользователь видит второй, поэтому берём только видимые блоки.
private fun dayContainers(doc: Document): List<Element> {
    val blocks = doc.select(Sel.DAYS_BLOCK)
    if (blocks.isEmpty()) return listOf(doc)
    val visible = blocks.filter { b -> !isHidden(b) && b.parents().none { isHidden(it) } }
    return visible.ifEmpty { listOf(blocks.last()!!) }
}

// <table id="db_table_12.10.26"> — полная дата прямо в id таблицы дня.
private fun dateFromId(day: Element): LocalDate? {
    val table = if (day.tagName() == "table") day else day.selectFirst("table") ?: return null
    val m = Regex("(\\d{1,2})\\.(\\d{1,2})\\.(\\d{2,4})").find(table.id()) ?: return null
    val y = m.groupValues[3].let { if (it.length == 2) 2000 + it.toInt() else it.toInt() }
    return safeDate(y, m.groupValues[2].toInt(), m.groupValues[1].toInt())
}

// «Понедельник, 12» — только число; ищем его внутри недели.
private fun dateFromDayNumber(text: String, week: LocalDate?): LocalDate? {
    val m = Regex("(\\d{1,2})\\s*$").find(norm(text)) ?: return null
    val monday = week?.mondayOf() ?: return null
    return (0 until 7).map { monday.plusDays(it.toLong()) }.firstOrNull { it.dayOfMonth == m.groupValues[1].toInt() }
}

// Текст ДЗ: как есть, с переводами строк, но без отступов разметки.
private fun homeworkText(tr: Element): String {
    val el = tr.selectFirst(Sel.HOMEWORK) ?: return ""
    return el.wholeText().split('\n').joinToString("\n") { it.replace(Regex("\\s+"), " ").trim() }
        .replace(Regex("\n{3,}"), "\n\n").trim()
}

/** Разбирает страницу недели; [week] — понедельник из адреса страницы (по нему достраиваются даты). */
fun parseWeek(doc: Document, week: LocalDate?): ParsedWeek {
    var days = dayContainers(doc).flatMap { c -> c.select(Sel.DAY).filter { it !== c } }
    if (days.isEmpty()) days = doc.select(Sel.DAY_FALLBACK).toList()
    val lessons = mutableListOf<Lesson>()
    val seen = mutableSetOf<LocalDate>()
    val dates = mutableListOf<LocalDate>()

    val monday = week?.mondayOf()
    days.forEachIndexed { i, day ->
        val htext = day.selectFirst(Sel.DAY_HEADER)?.text() ?: ""
        // Дата обязана попасть в неделю из адреса: иначе урок виден в «Дневнике», но статистика
        // не найдёт четверть для чужого понедельника и молча его пропустит.
        val date = listOfNotNull(dateFromId(day), parseDayDate(htext, week), dateFromDayNumber(htext, week), monday?.plusDays(i.toLong()))
            .firstOrNull { monday == null || it.mondayOf() == monday }
            ?: return@forEachIndexed
        if (!seen.add(date)) return@forEachIndexed
        dates += date

        var rowIndex = 0
        for (tr in day.select(Sel.ROW)) {
            val lessonCell = tr.selectFirst(Sel.LESSON) ?: continue
            rowIndex++
            val (num, subject) = parseLessonCell(lessonCell)
            if (subject.isEmpty()) continue
            val parsed = tr.selectFirst(Sel.MARK)?.let { parseMarkText(markCellText(it)) } ?: ParsedMarks(emptyList(), false)
            lessons += Lesson(date, num ?: rowIndex, subject, parsed.marks, parsed.absent, homeworkText(tr))
        }
    }
    return ParsedWeek(days.size, dates.sorted(), lessons)
}

private fun nameFromTitle(title: String): String {
    val part = norm(title.split(Regex("[.|—–-]"))[0])
    return if (Regex("[А-Яа-яЁёA-Za-z]").containsMatchIn(part) && part.length < 60) part else ""
}

private fun originOf(u: URI) = buildString {
    append(u.scheme).append("://").append(u.host)
    if (u.port != -1) append(':').append(u.port)
}

/**
 * Ученики, доступные со страницы [url]: сам ученик (/pupil/<id>) или дети на странице родителя.
 * (Из schools++, src/crawler.js.)
 */
fun findPupils(doc: Document, url: String): List<Pupil> {
    class Found(val id: String, val origin: String, var name: String)

    val base = URI(url)
    val found = linkedMapOf<String, Found>()
    Regex("/pupil/(\\d+)").find(base.path ?: "")?.let { m ->
        val fromH1 = doc.selectFirst(".title_box h1")?.let { norm(it.text().split(',')[0]) } ?: ""
        found[m.groupValues[1]] = Found(
            m.groupValues[1], originOf(base),
            if (fromH1.isNotEmpty() && fromH1.length < 60) fromH1 else nameFromTitle(doc.title()),
        )
    }
    val badLink = Regex("дневник|отметк|расписан", RegexOption.IGNORE_CASE)
    for (a in doc.select("a[href*=/pupil/]")) {
        val u = runCatching { base.resolve(a.attr("href")) }.getOrNull() ?: continue
        val m = Regex("^/pupil/(\\d+)(?:/|$)").find(u.path ?: "") ?: continue
        val text = norm(a.text())
        val good = Regex("[А-Яа-яЁё]{2,}").containsMatchIn(text) && text.length < 60 && !badLink.containsMatchIn(text)
        val prev = found[m.groupValues[1]]
        if (prev == null) found[m.groupValues[1]] = Found(m.groupValues[1], originOf(u), if (good) text else "")
        else if (prev.name.isEmpty() && good) prev.name = text
    }
    return found.values.map { Pupil(it.id, it.origin, it.name.ifEmpty { "Ученик #${it.id}" }) }
}
