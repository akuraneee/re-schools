package io.github.akuraneee.reschools.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val REQUEST_DELAY_MS = 120L

/**
 * Загрузка дневника с schools.by.
 *
 * Неделя: GET /pupil/<id>/dnevnik/quarter/<qid>/week/<понедельник>. qid (четверть) заранее неизвестен —
 * узнаём его из ссылок «предыдущая/следующая неделя» (send_to="…/quarter/<qid>/week/<дата>") в уже
 * загруженных неделях и в ответе /pupil/<id>/dnevnik.
 */
class Diary(
    private val pupil: Pupil,
    private val fetcher: PageFetcher,
    private val persist: Persist,
    private val requestDelayMs: Long = REQUEST_DELAY_MS,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val prefix = "${pupil.id}:"
    private val mutex = Mutex()

    // Карты не меняются на месте, а подменяются целиком: интерфейс читает их из другого потока.
    /** понедельник → qid четверти */
    @Volatile var links: Map<LocalDate, String> = persist.get<Map<LocalDate, String>>(prefix + "links") ?: emptyMap()
        private set

    /** понедельник → неделя */
    @Volatile var weeks: Map<LocalDate, Week> = persist.get<Map<LocalDate, Week>>(prefix + "weeks") ?: emptyMap()
        private set

    // недели, загруженные в этой сессии (их соседи уже известны)
    private val expanded: MutableSet<LocalDate> = ConcurrentHashMap.newKeySet()
    private var bootstrapped = false

    private fun save() {
        persist.set(prefix + "links", links)
        persist.set(prefix + "weeks", weeks)
    }

    fun cached(monday: LocalDate): Week? = weeks[monday.mondayOf()]

    fun isFresh(monday: LocalDate): Boolean = monday.mondayOf() in expanded

    fun allLessons(): List<Lesson> =
        weeks.values.flatMap { it.lessons }.sortedWith(compareBy({ it.date }, { it.num }))

    private val weekRe get() = Regex("/pupil/${pupil.id}/dnevnik/quarter/(\\d+)/week/(\\d{4}-\\d{2}-\\d{2})")

    private fun collectLinks(text: String) {
        links = links + weekRe.findAll(text).associate { LocalDate.parse(it.groupValues[2]).mondayOf() to it.groupValues[1] }
    }

    // Ответ вкладки «Дневник»: в нём ссылки на недели текущей четверти (и, возможно, сама текущая неделя).
    private suspend fun bootstrap() {
        bootstrapped = true
        val page = fetcher.fetchChecked("${pupil.origin}/pupil/${pupil.id}/dnevnik")
        collectLinks(page.url + " " + page.html)
        val m = Regex("weekUrl\\s*=\\s*['\"][^'\"]*/week/(\\d{4}-\\d{2}-\\d{2})").find(page.html)
        if (m != null && page.doc.selectFirst(Sel.DAY) != null) storeWeek(LocalDate.parse(m.groupValues[1]).mondayOf(), page)
        save()
    }

    private fun storeWeek(monday: LocalDate, page: Page): Week {
        val parsed = parseWeek(page.doc, monday)
        val week = Week(parsed.lessons, parsed.dates, clock())
        weeks = weeks + (monday to week)
        expanded += monday
        return week
    }

    private suspend fun fetchWeek(monday: LocalDate): Week {
        val page = fetcher.fetchChecked("${pupil.origin}/pupil/${pupil.id}/dnevnik/quarter/${links[monday]}/week/$monday")
        collectLinks(page.url + " " + page.html)
        val week = storeWeek(monday, page)
        save()
        if (requestDelayMs > 0) delay(requestDelayMs)
        return week
    }

    // Ищем qid недели: идём по ссылкам соседних недель от ближайшей известной.
    // null — такой недели в дневнике нет (каникулы между четвертями, другой учебный год).
    private suspend fun resolve(monday: LocalDate): String? {
        for (step in 0 until 14) {
            links[monday]?.let { return it }
            val known = links.keys.sorted()
            if (known.isEmpty() || (!bootstrapped && step == 0)) {
                if (!bootstrapped) { bootstrap(); continue }
                return null
            }
            val before = known.lastOrNull { it < monday }
            val after = known.firstOrNull { it > monday }
            val options = listOfNotNull(before, after).filter { it !in expanded }
                .sortedBy { kotlin.math.abs(daysBetween(it, monday)) }
            if (options.isEmpty()) return null
            fetchWeek(options.first())
        }
        return null
    }

    /** Неделя из сети ([force]) или из кэша, если она уже загружалась в этой сессии. */
    suspend fun loadWeek(day: LocalDate, force: Boolean = false): Week = mutex.withLock {
        val monday = day.mondayOf()
        if (!force && monday in expanded) weeks[monday]?.let { return@withLock it }
        val qid = resolve(monday)
        if (qid == null) {
            val none = Week(emptyList(), emptyList(), clock(), none = true)
            weeks = weeks + (monday to none)
            expanded += monday
            save()
            return@withLock none
        }
        if (monday in expanded && !force) weeks.getValue(monday) else fetchWeek(monday)
    }

    /**
     * Недели учебного года до текущей, которых ещё нет в кэше (для статистики за год). Идём от новых к старым:
     * ссылка «предыдущая неделя» уже подсказывает qid следующей. Загруженные раньше недели не перекачиваем.
     */
    suspend fun loadHistory(today: LocalDate, onProgress: ((done: Int, total: Int) -> Unit)? = null) {
        val start = LocalDate.of(if (today.monthValue >= 8) today.year else today.year - 1, 9, 1).mondayOf()
        val missing = generateSequence(today.mondayOf().minusWeeks(1)) { it.minusWeeks(1) }
            .takeWhile { it >= start }.filter { weeks[it] == null }.toList()
        missing.forEachIndexed { i, w ->
            loadWeek(w)
            onProgress?.invoke(i + 1, missing.size)
        }
    }

    /** Забыть, что неделя уже загружалась, чтобы следующий [loadWeek] сходил в сеть. */
    fun invalidate(monday: LocalDate) { expanded -= monday.mondayOf() }

    /**
     * Недели, которые обновляем при каждом открытии: с последнего захода (но не больше 6 недель назад,
     * и минимум две недели — учителя иногда ставят отметки задним числом) и следующая неделя.
     */
    fun weeksToRefresh(today: LocalDate, lastVisitAt: Long?): List<LocalDate> {
        val cur = today.mondayOf()
        var from = cur.minusWeeks(1)
        if (lastVisitAt != null) {
            val lv = Instant.ofEpochMilli(lastVisitAt).atZone(zone).toLocalDate().mondayOf()
            if (lv < from) from = lv
        }
        val floor = cur.minusWeeks(6)
        if (from < floor) from = floor
        val list = generateSequence(cur) { it.minusWeeks(1) }.takeWhile { it >= from }.toMutableList()
        list.add(1, cur.plusWeeks(1)) // текущая, следующая, затем прошлые
        return list
    }

    suspend fun refresh(today: LocalDate, lastVisitAt: Long?, onProgress: ((done: Int, total: Int) -> Unit)? = null) {
        val list = weeksToRefresh(today, lastVisitAt)
        // всё перекачиваем, но каждую неделю — один раз (часть грузится при поиске qid)
        expanded.clear()
        list.forEachIndexed { i, w ->
            loadWeek(w)
            onProgress?.invoke(i + 1, list.size)
        }
    }

    fun clear() {
        links = emptyMap(); weeks = emptyMap(); expanded.clear(); bootstrapped = false
        persist.remove(prefix + "links"); persist.remove(prefix + "weeks")
    }
}
