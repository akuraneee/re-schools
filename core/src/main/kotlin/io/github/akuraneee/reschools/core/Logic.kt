package io.github.akuraneee.reschools.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

// Что показывать на главной: отметки за сегодня, ДЗ на следующий учебный день, новые отметки с прошлого захода.

fun todayMarks(lessons: List<Lesson>, today: LocalDate): List<Lesson> =
    lessons.filter { it.date == today && (it.marks.isNotEmpty() || it.absent) }

/** Ближайший день после [today], в который есть уроки (обычно завтра; в пятницу/субботу — понедельник). */
fun nextSchoolDay(lessons: List<Lesson>, today: LocalDate): LocalDate {
    val limit = today.plusDays(10)
    lessons.map { it.date }.filter { it > today && it <= limit }.minOrNull()?.let { return it }
    var d = today.plusDays(1)
    while (d.isWeekend) d = d.plusDays(1)
    return d
}

/**
 * ДЗ к урокам дня [target]. Сдвоенные уроки одного предмета объединяются.
 *
 * [HwMode.Assigned]: на schools.by ДЗ стоит в строке урока, на котором его задали (docs/schools-by.md из schools++),
 * значит к уроку [target] относится ДЗ из предыдущего урока того же предмета;
 * [HwMode.Due]: ДЗ записано прямо в строке урока, к которому его надо сделать.
 */
fun homeworkFor(lessons: List<Lesson>, target: LocalDate, mode: HwMode = HwMode.Assigned): List<HomeworkItem> {
    class Item(val key: String, val subject: String, val nums: MutableList<Int>, var hw: String, var from: LocalDate?)

    val day = lessons.filter { it.date == target }.sortedBy { it.num }
    val items = mutableListOf<Item>()
    for (l in day) {
        val key = subjectKey(l.subject)
        val last = items.lastOrNull()
        if (last != null && last.key == key && last.nums.last() == l.num - 1) {
            last.nums += l.num
            if (mode == HwMode.Due && l.hw.isNotEmpty() && l.hw !in last.hw) {
                last.hw = listOf(last.hw, l.hw).filter { it.isNotEmpty() }.joinToString("\n")
            }
            continue
        }
        val item = Item(key, l.subject, mutableListOf(l.num), "", null)
        if (mode == HwMode.Due) {
            item.hw = l.hw
            item.from = target
        } else {
            val prev = lessons.filter { it.date < target && subjectKey(it.subject) == key }
            prev.maxOfOrNull { it.date }?.let { lastDate ->
                item.hw = prev.filter { it.date == lastDate && it.hw.isNotEmpty() }.map { it.hw }.distinct().joinToString("\n")
                item.from = lastDate
            }
        }
        items += item
    }
    return items.map { HomeworkItem(it.subject, it.nums, it.hw, it.from) }
}

/** Снимок известных отметок: какие недели загружены и что в них стояло. */
fun snapshot(weeks: Map<LocalDate, Week>, at: Long = System.currentTimeMillis()): Snapshot {
    val marks = HashMap<String, String>()
    for (w in weeks.values) for (l in w.lessons) if (l.marks.isNotEmpty()) marks[l.key] = l.marksKey
    return Snapshot(at, weeks.filterValues { !it.none }.keys.sorted(), marks)
}

/**
 * Новые отметки по сравнению со снимком [baseline] (сделанным в конце прошлого захода); null — сравнивать не с чем.
 * Сегодняшние не считаем — они в отдельном блоке. Неделя, которой не было в снимке и которая целиком
 * раньше прошлого захода, — не «новая», а просто впервые загруженная (например, листали дневник).
 */
fun newMarks(lessons: List<Lesson>, baseline: Snapshot?, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<NewMark>? {
    if (baseline == null) return null
    val known = baseline.weeks.toSet()
    val visitDay = Instant.ofEpochMilli(baseline.at).atZone(zone).toLocalDate()
    return lessons
        .filter { it.marks.isNotEmpty() && it.date < today }
        .mapNotNull { l ->
            val was = baseline.marks[l.key]
            when {
                was == l.marksKey -> null
                l.date.mondayOf() !in known && l.date < visitDay -> null
                else -> NewMark(l, was)
            }
        }
        .sortedWith(compareByDescending<NewMark> { it.lesson.date }.thenBy { it.lesson.num })
}

/**
 * «Заход» — открытие приложения после перерыва. Короткие перерывы (свернул на минуту) заходом не считаем,
 * иначе новые отметки исчезали бы при каждом переключении приложений.
 */
const val VISIT_GAP_MS = 15 * 60 * 1000L

fun startVisit(state: Visit?, now: Long = System.currentTimeMillis()): Visit {
    val s = state ?: Visit()
    if (s.lastActive != 0L && now - s.lastActive < VISIT_GAP_MS) return s.copy(lastActive = now)
    return Visit(baseline = s.seen, seen = s.seen, lastActive = now)
}
