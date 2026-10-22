package io.github.akuraneee.reschools.core

import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/** Ученики и настройки, общие для всех учеников. */
class Profile(private val persist: Persist) {
    var pupils: List<Pupil> = persist.get<List<Pupil>>("pupils") ?: emptyList()
        private set
    var settings: Settings = persist.get<Settings>("settings") ?: Settings()
        private set

    /** id ученика, которого показываем. */
    var currentId: String? = persist.get<String>("pupil")
        private set

    val current: Pupil? get() = pupils.firstOrNull { it.id == currentId } ?: pupils.firstOrNull()

    fun setPupils(list: List<Pupil>) {
        pupils = list
        persist.set("pupils", list)
        if (list.none { it.id == currentId }) select(list.firstOrNull()?.id)
    }

    fun select(id: String?) {
        currentId = id
        if (id == null) persist.remove("pupil") else persist.set("pupil", id)
    }

    fun setSettings(value: Settings) {
        settings = value
        persist.set("settings", value)
    }

    fun forget() {
        pupils = emptyList(); currentId = null
        persist.remove("pupils"); persist.remove("pupil")
    }
}

/**
 * Дневник одного ученика вместе с «заходом»: что показывали в прошлый раз и какие отметки с тех пор появились.
 * Все изменения состояния — здесь, интерфейс только показывает.
 */
class PupilSession(
    val pupil: Pupil,
    fetcher: PageFetcher,
    private val persist: Persist,
    private val clock: Clock = Clock.systemDefaultZone(),
    requestDelayMs: Long = 120,
) {
    private val zone: ZoneId get() = clock.zone
    val diary = Diary(pupil, fetcher, persist, requestDelayMs, zone) { clock.millis() }

    // Обновление и листание дневника идут из разных корутин — состояние захода меняем под замком.
    private val lock = Any()

    @Volatile var visit: Visit = startVisit(persist.get<Visit>("${pupil.id}:visit"), clock.millis())
        private set
    @Volatile var lastSync: Long? = persist.get<Long>("${pupil.id}:lastSync")
        private set

    init { persist.set("${pupil.id}:visit", visit) }

    fun today(): LocalDate = LocalDate.now(clock)

    private fun updateVisit(change: (Visit) -> Visit): Visit = synchronized(lock) {
        visit = change(visit)
        persist.set("${pupil.id}:visit", visit)
        visit
    }

    /** Обновить недели, которые могли измениться; по успеху запоминаем снимок отметок для следующего захода. */
    suspend fun refresh(onProgress: ((Int, Int) -> Unit)? = null) {
        diary.refresh(today(), visit.baseline?.at, onProgress)
        val now = clock.millis()
        updateVisit { it.copy(seen = snapshot(diary.weeks, now), lastActive = now) }
        lastSync = now
        persist.set("${pupil.id}:lastSync", now)
    }

    /** Неделя, открытая при листании дневника: её отметки в снимок попадают сразу и «новыми» не считаются. */
    suspend fun openWeek(monday: LocalDate, force: Boolean = false) {
        if (force) diary.invalidate(monday)
        if (!force && diary.isFresh(monday) && diary.cached(monday) != null) return
        diary.loadWeek(monday, force)
        updateVisit { it.copy(seen = snapshot(diary.weeks, it.seen?.at ?: clock.millis())) }
    }

    /** Недели с начала учебного года для статистики; их отметки в снимок попадают сразу и «новыми» не считаются. */
    suspend fun loadHistory(onProgress: ((Int, Int) -> Unit)? = null) {
        try {
            diary.loadHistory(today(), onProgress)
        } finally {
            updateVisit { it.copy(seen = snapshot(diary.weeks, it.seen?.at ?: clock.millis())) }
        }
    }

    fun onBackground() {
        updateVisit { it.copy(lastActive = clock.millis()) }
    }

    /** Вернулись в приложение; true — прошло достаточно времени, это новый заход и данные стоит обновить. */
    fun onForeground(): Boolean {
        val now = clock.millis()
        var isNew = false
        updateVisit {
            isNew = it.lastActive == 0L || now - it.lastActive >= VISIT_GAP_MS
            startVisit(it, now)
        }
        return isNew
    }

    fun newMarks(): List<NewMark>? = newMarks(diary.allLessons(), visit.baseline, today(), zone)

    fun clear() {
        diary.clear()
        persist.remove("${pupil.id}:lastSync")
        lastSync = null
        updateVisit { startVisit(null, clock.millis()) }
    }
}
