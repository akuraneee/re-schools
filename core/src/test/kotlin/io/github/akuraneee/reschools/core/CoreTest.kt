package io.github.akuraneee.reschools.core

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup

private val TODAY = LocalDate.of(2026, 10, 20) // вторник, первая четверть; следующая неделя — каникулы
private fun d(s: String) = LocalDate.parse(s)
private val ZONE = ZoneId.systemDefault()
private fun ms(s: String) = LocalDateTime.parse(s).atZone(ZONE).toInstant().toEpochMilli()

private fun diary(site: FakeSite, persist: Persist = Persist(MemoryStore())) =
    Diary(TEST_PUPIL, FakeFetcher(site), persist, requestDelayMs = 0)

private fun lesson(date: String, hw: String = "") = Lesson(d(date), 1, "Матем.", hw = hw)

class ParserTest {
    @Test
    fun `видимый блок, ДЗ с переводами строк, подсказки не путаются с отметками`() {
        val site = FakeSite(TODAY)
        val html = site.handle("$HOST/pupil/$PID/dnevnik/quarter/92/week/2026-10-12").body
        val week = d("2026-10-12")
        val parsed = parseWeek(Jsoup.parse(html), week)
        assertEquals((12..16).map { d("2026-10-$it") }, parsed.dates, "дни (субботы нет в видимом блоке)")
        // отметки в скрытом блоке на 2 меньше — убираем display:none и сверяем
        val both = parseWeek(Jsoup.parse(html.replace("style=\"display: none;\"", "")), week)
        assertTrue(parsed.lessons.any { it.marks.isNotEmpty() }, "есть отметки")
        assertTrue(parsed.lessons.all { l -> l.marks.all { it >= 4 } }, "отметки не из скрытого блока (там бывают < 4)")
        assertTrue(both.lessons.size > parsed.lessons.size || both.lessons.indices.any { both.lessons[it].marks != parsed.lessons.getOrNull(it)?.marks })
        val eng = parsed.lessons.first { it.subject == "Англ. яз." && it.hw.isNotEmpty() }
        assertTrue(Regex("^WB p\\. \\d+ ex\\. \\d+\\nУчить слова Unit \\d$").matches(eng.hw), "ДЗ сохранило перевод строки: ${eng.hw}")
        assertTrue(parsed.lessons.none { 12 in it.marks }, "цифры из .hint не стали отметками")
    }

    @Test
    fun `отсутствие и сдвоенные отметки`() {
        assertEquals(ParsedMarks(listOf(7, 8), false), parseMarkText("7/8"))
        assertEquals(ParsedMarks(listOf(5), true), parseMarkText("н, 5"))
        assertEquals(ParsedMarks(emptyList(), true), parseMarkText("н/б"))
        assertEquals(ParsedMarks(emptyList(), false), parseMarkText("11 0 abc"))
    }

    @Test
    fun `даты дней числом и словами, год ближайший к неделе`() {
        assertEquals(d("2026-10-12"), parseDayDate("Понедельник, 12.10.26", null))
        assertEquals(d("2027-01-04"), parseDayDate("понедельник, 4 января", d("2026-12-28")))
        assertEquals(d("2026-12-30"), parseDayDate("30 снежня", d("2026-12-28")))
        assertNull(parseDayDate("Понедельник", null))
    }

    @Test
    fun `ученик и дети родителя`() {
        val own = Jsoup.parse(FakeSite(TODAY).handle("$HOST/pupil/$PID").body)
        assertEquals(listOf(TEST_PUPIL), findPupils(own, "$HOST/pupil/$PID"))

        val parent = Jsoup.parse(
            """<html><body><a href="/pupil/11">Иванов Иван</a> <a href="/pupil/11/dnevnik">Дневник</a>
               <a href="/pupil/12/">Иванова Анна</a> <a href="/pupil/13/marks">отметки</a></body></html>""",
        )
        assertEquals(
            listOf(Pupil("11", HOST, "Иванов Иван"), Pupil("12", HOST, "Иванова Анна"), Pupil("13", HOST, "Ученик #13")),
            findPupils(parent, "$HOST/parent/5"),
        )
    }
}

class DiaryTest {
    @Test
    fun `обновление - текущая, следующая каникулы и прошлая неделя, qid из ссылок`() = runBlocking {
        val site = FakeSite(TODAY)
        val diary = diary(site)
        diary.refresh(TODAY, null)
        // 02.11 — первая неделя второй четверти: её загрузили, чтобы убедиться, что 26.10 в дневнике нет
        assertEquals(listOf("2026-10-12", "2026-10-19", "2026-10-26", "2026-11-02").map(::d), diary.weeks.keys.sorted())
        assertTrue(diary.weeks.getValue(d("2026-10-26")).none, "неделя каникул помечена как отсутствующая")
        assertTrue(diary.weeks.getValue(d("2026-10-19")).lessons.size > 20, "текущая неделя загружена")
        assertEquals(site.requests.size, site.requests.toSet().size, "каждая страница - один запрос")
        assertFalse(site.requests.any { it.contains("/quarter/92/week/2026-10-26") }, "не угадываем адрес недели каникул")
    }

    @Test
    fun `переход через каникулы в следующую четверть`() = runBlocking {
        val diary = diary(FakeSite(TODAY))
        val week = diary.loadWeek(d("2026-11-04"))
        assertEquals("93", diary.links[d("2026-11-02")])
        assertTrue(week.lessons.size > 20 && week.lessons[0].date == d("2026-11-02"), "неделя второй четверти")
        assertTrue(week.lessons.all { it.marks.isEmpty() }, "будущая неделя без отметок")
    }

    @Test
    fun `дневник сохраняется и читается после перезапуска`() = runBlocking {
        val site = FakeSite(TODAY)
        val persist = Persist(MemoryStore())
        val first = diary(site, persist)
        first.refresh(TODAY, null)
        val second = diary(site, persist)
        assertEquals(first.allLessons(), second.allLessons())
        assertEquals("92", second.links[d("2026-10-19")])
    }

    @Test
    fun `без входа - AuthException`() {
        val diary = diary(FakeSite(TODAY, loggedIn = false))
        assertFailsWith<AuthException> { runBlocking { diary.refresh(TODAY, null) } }
    }

    @Test
    fun `недели для обновления - с последнего захода, не больше шести недель`() {
        val diary = diary(FakeSite(TODAY))
        assertEquals(listOf("2026-10-19", "2026-10-26", "2026-10-12").map(::d), diary.weeksToRefresh(TODAY, null))
        assertEquals(
            listOf("2026-10-19", "2026-10-26", "2026-10-12", "2026-10-05").map(::d),
            diary.weeksToRefresh(TODAY, ms("2026-10-07T10:00:00")),
        )
        assertEquals(8, diary.weeksToRefresh(TODAY, ms("2026-01-07T10:00:00")).size, "потолок - 6 недель назад")
    }
}

class LogicTest {
    @Test
    fun `ДЗ на завтра берётся с прошлого урока того же предмета, сдвоенные уроки объединены`() = runBlocking {
        val diary = diary(FakeSite(TODAY))
        diary.refresh(TODAY, null)
        val all = diary.allLessons()
        val target = nextSchoolDay(all, TODAY)
        assertEquals(d("2026-10-21"), target)
        val items = homeworkFor(all, target, HwMode.Assigned)
        assertEquals(listOf("Рус. лит.", "Физика", "Информ.", "География", "Обществов.", "Физ. к. и зд."), items.map { it.subject })
        val physics = items.first { it.subject == "Физика" }
        assertEquals(d("2026-10-19"), physics.from, "физика была в понедельник")
        val monday = all.first { it.date == d("2026-10-19") && it.subject == "Физика" }
        assertEquals(monday.hw, physics.hw)
        // вторник: «Матем.» 2-м и 3-м уроком - один пункт
        val tuesday = homeworkFor(all, d("2026-10-20"), HwMode.Assigned)
        assertEquals(listOf(2, 3), tuesday.first { it.subject == "Матем." }.nums)
        val due = homeworkFor(all, d("2026-10-19"), HwMode.Due)
        assertEquals(monday.hw, due.first { it.subject == "Физика" }.hw, "режим «к уроку» берёт ДЗ из самого урока")
    }

    @Test
    fun `следующий учебный день - пятница на понедельник, выходные пропускаются`() {
        assertEquals(d("2026-10-19"), nextSchoolDay(listOf(lesson("2026-10-16"), lesson("2026-10-19")), d("2026-10-16")))
        assertEquals(d("2026-10-26"), nextSchoolDay(emptyList(), d("2026-10-23")))
    }

    @Test
    fun `новые отметки с прошлого захода - без сегодняшних, исправленные с прежним значением`() = runBlocking {
        val site = FakeSite(TODAY)
        var visit = startVisit(null, 1000)
        assertNull(visit.baseline, "первый заход - сравнивать не с чем")
        var diary = diary(site)
        diary.refresh(TODAY, null)
        assertNull(newMarks(diary.allLessons(), visit.baseline, TODAY))
        visit = visit.copy(seen = snapshot(diary.weeks, ms("2026-10-20T08:00:00")))

        assertNull(startVisit(visit, visit.lastActive + 10 * 60000).baseline, "короткий перерыв - не новый заход")

        val (prev, older) = site.bump()
        visit = startVisit(visit, visit.lastActive + 3 * 3600000)
        assertTrue(visit.baseline!!.weeks.contains(d("2026-10-12")), "новый заход сравнивает со снимком прошлого")
        diary = diary(site)
        diary.refresh(TODAY, visit.baseline.at)
        val fresh = newMarks(diary.allLessons(), visit.baseline, TODAY)!!
        assertEquals(listOf(prev, older), fresh.map { it.lesson.date })
        assertEquals(listOf(10), fresh[0].lesson.marks)
        assertNull(fresh[0].was)
        assertNotNull(fresh[1].was)
        assertTrue(fresh[1].was != fresh[1].lesson.marks[0].toString(), "исправленная отметка помнит прежнюю")
        assertTrue(fresh.all { it.lesson.date != TODAY }, "сегодняшние не входят")
        assertContentEquals(emptyList(), todayMarks(diary.allLessons(), TODAY).filter { it.date != TODAY })
    }

    @Test
    fun `неделя, впервые загруженная при листании дневника, не считается новой`() = runBlocking {
        val diary = diary(FakeSite(TODAY))
        diary.refresh(TODAY, null)
        val base = snapshot(diary.weeks, ms("2026-10-20T08:00:00"))
        diary.loadWeek(d("2026-09-21"))
        assertEquals(emptyList(), newMarks(diary.allLessons(), base, TODAY))
    }
}

class SessionTest {
    private fun clock(at: String) = Clock.fixed(Instant.ofEpochMilli(ms(at)), ZONE)

    @Test
    fun `сессия - снимок после обновления, новый заход через перерыв, неделя при листании не новая`() = runBlocking {
        val site = FakeSite(TODAY)
        val store = MemoryStore()
        val first = PupilSession(TEST_PUPIL, FakeFetcher(site), Persist(store), clock("2026-10-20T08:00:00"), 0)
        assertNull(first.newMarks(), "первый заход")
        first.refresh()
        assertNotNull(first.lastSync)
        first.openWeek(d("2026-09-21"))

        val (prev, _) = site.bump()
        val second = PupilSession(TEST_PUPIL, FakeFetcher(site), Persist(store), clock("2026-10-20T12:00:00"), 0)
        assertNotNull(second.visit.baseline, "через 4 часа - новый заход со снимком прошлого")
        second.refresh()
        val fresh = second.newMarks()!!
        assertTrue(fresh.any { it.lesson.date == prev }, "новая отметка видна")
        assertTrue(fresh.none { it.lesson.date < d("2026-10-12") }, "листание не породило новых")
        assertFalse(second.onForeground(), "сразу после активности - тот же заход")
    }

    @Test
    fun `профиль помнит учеников и выбор`() {
        val persist = Persist(MemoryStore())
        val profile = Profile(persist)
        assertNull(profile.current)
        profile.setPupils(listOf(TEST_PUPIL, Pupil("2", HOST, "Тестова Анна")))
        assertEquals(TEST_PUPIL, profile.current)
        profile.select("2")
        profile.setSettings(Settings(HwMode.Due))
        val again = Profile(persist)
        assertEquals("2", again.current?.id)
        assertEquals(HwMode.Due, again.settings.hwMode)
    }
}

class TextTest {
    @Test
    fun `даты, времена и склонения`() {
        assertEquals("20 октября", Ru.dateLong(TODAY))
        assertEquals("вт, 20 окт", Ru.dateShort(TODAY))
        assertEquals("12 – 17 октября", Ru.weekRange(d("2026-10-12")))
        assertEquals("30 ноября – 5 декабря", Ru.weekRange(d("2026-11-30")))
        assertEquals("на завтра", Ru.relDay(d("2026-10-21"), TODAY))
        assertEquals("на понедельник", Ru.relDay(d("2026-10-26"), d("2026-10-23")))
        assertEquals("сегодня в 08:05", Ru.whenText(ms("2026-10-20T08:05:00"), TODAY))
        assertEquals("вчера в 20:40", Ru.whenText(ms("2026-10-19T20:40:00"), TODAY))
        assertEquals("7,50", formatMean(mean(listOf(7, 8))))
        assertEquals("—", formatMean(mean(emptyList())))
        assertEquals("Математика", subjectTitle("Матем."))
        assertEquals(5, markLevel(10)); assertEquals(1, markLevel(2)); assertEquals(0, markLevel(null))
    }
}

class StatsTest {
    @Test
    fun `история - недели с начала года подгружаются один раз, каникулы пропускаются`() = runBlocking {
        val site = FakeSite(TODAY)
        val diary = diary(site)
        diary.refresh(TODAY, null)
        diary.loadHistory(TODAY)
        val mondays = diary.weeks.filterValues { !it.none }.keys
        assertTrue(d("2026-08-31") in mondays || d("2026-09-07") in mondays, "начало сентября загружено")
        val before = site.requests.size
        diary.loadHistory(TODAY)
        assertEquals(before, site.requests.size, "повторно ничего не качаем")
    }

    @Test
    fun `четверти, средние, прогноз и динамика`() {
        val l = { day: String, subj: String, marks: List<Int> -> Lesson(d(day), 1, subj, marks) }
        val q = { monday: LocalDate -> if (monday < d("2026-10-05")) "91" else "92" }
        val lessons = listOf(
            l("2026-09-14", "Матем.", listOf(5)),
            l("2026-10-05", "Матем.", listOf(6, 7, 8, 9, 9)),
            l("2026-10-12", "Матем.", listOf(10)),
            l("2026-10-12", "Физика", listOf(4)),
        ) + Lesson(d("2026-10-13"), 2, "Физика", absent = true)
        val s = computeStats(lessons, q, TODAY)
        assertEquals(listOf("91", "92"), s.quarters.map { it.id })
        assertEquals("92", s.current)
        val math = s.subjects.first { it.name == "Математика" }
        assertEquals(5.0, math.byQ.getValue("91")!!.avg)
        assertEquals(49.0 / 6, math.byQ.getValue("92")!!.avg, 1e-9)
        assertEquals(7, math.all.n)
        // годовая — среднее округлённых четвертных: (5 + 8) / 2 = 6,5 → 7
        assertEquals(6.5, math.year.exact)
        assertEquals(7, math.year.rounded)
        // 8,17 в 92-й: до 9 нужно ceil((8,5*6 - 49) / (10 - 8,5)) = 2 десятки
        assertEquals(UpStep(10, 2), math.forecast!!.up.first())
        assertEquals(1, s.subjects.first { it.name == "Физика" }.absences)
        assertEquals(1, s.distribution[4])
        assertEquals(2, s.distribution[9])
        assertEquals(roundMark(7.49), 7)
        assertEquals(roundMark(7.5), 8)
        assertEquals(roundMark(7.3, 0.3), 8)
    }
}
