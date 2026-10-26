package io.github.akuraneee.reschools.core

import java.time.LocalDate

// Выдуманный schools.by для тестов: та же разметка, что у настоящего сайта (см. docs/schools-by.md в schools++),
// включая скрытый блок .db_days с заниженными на 2 отметками и переходы между четвертями через каникулы.
// Ученик «Тестов Тест», ID 1234567, хост demo.schools.by. Никаких настоящих данных.

const val PID = "1234567"
const val HOST = "https://demo.schools.by"

val TEST_PUPIL = Pupil(PID, HOST, "Тестов Тест")

private const val ABSENT = -1

private val SCHEDULE = listOf(
    listOf("Матем.", "Рус. яз.", "Англ. яз.", "Физика", "Ист. Бел. в кон. всем. ист.", "Физ. к. и зд.", "Информ. час"),
    listOf("Бел. яз.", "Матем.", "Матем.", "Химия", "Биология", "Англ. яз.", ""),
    listOf("Рус. лит.", "Физика", "Информ.", "География", "Обществов.", "Физ. к. и зд.", ""),
    listOf("Англ. яз.", "Матем.", "Бел. лит.", "Химия", "Черчение", "ДП/МП", ""),
    listOf("Рус. яз.", "Физика", "Матем.", "Биология", "Бел. яз.", "", ""),
    listOf("", "", "", "", "", "", ""),
)

private val HW: Map<String, (Int) -> String> = mapOf(
    "Матем." to { n -> "§ ${n % 20 + 1}, № ${n % 40 + 10}.${n % 9 + 1}, ${n % 40 + 11}.${n % 7 + 2}" },
    "Рус. яз." to { n -> "Упр. ${n % 300 + 20}, выучить правило на с. ${n % 150 + 30}" },
    "Англ. яз." to { n -> "WB p. ${n % 60 + 5} ex. ${n % 6 + 1}\nУчить слова Unit ${n % 8 + 1}" },
    "Физика" to { n -> "§ ${n % 25 + 1}, упр. ${n % 12 + 1} (1–3)" },
    "Химия" to { n -> "§ ${n % 30 + 1}, задачи ${n % 5 + 1}–${n % 5 + 4}" },
    "Биология" to { n -> "§ ${n % 30 + 1}, вопросы после параграфа" },
    "Рус. лит." to { _ -> "Читать «Отцы и дети», гл. 10–14\nhttps://example.org/reading-list" },
    "Бел. яз." to { n -> "Практыкаванне ${n % 200 + 10}" },
)

private fun hash(str: String): Double {
    var h = -2128831035 // 2166136261
    for (c in str) { h = h xor c.code; h *= 16777619 }
    h = h xor (h ushr 16); h *= 0x85ebca6b.toInt()
    h = h xor (h ushr 13); h *= 0xc2b2ae35.toInt()
    h = h xor (h ushr 16)
    return (h.toLong() and 0xFFFFFFFFL) / 4294967296.0
}

class FakeResponse(val status: Int, val body: String, val location: String? = null)

class FakeSite(val today: LocalDate, var loggedIn: Boolean = true) {
    private class Quarter(val id: String, val from: LocalDate, val to: LocalDate)

    var version = 1
    val requests = mutableListOf<String>()
    private val changed = HashMap<String, Int>()

    private val quarters: List<Quarter>

    init {
        val y = if (today.monthValue >= 8) today.year else today.year - 1
        val q1s = LocalDate.of(y, 9, 1).mondayOf()
        val q1e = LocalDate.of(y, 10, 24).mondayOf()
        quarters = listOf(
            Quarter("92", q1s, q1e),
            Quarter("93", q1e.plusDays(14), LocalDate.of(y, 12, 24).mondayOf()),
            Quarter("94", LocalDate.of(y + 1, 1, 12).mondayOf(), LocalDate.of(y + 1, 3, 21).mondayOf()),
            Quarter("95", LocalDate.of(y + 1, 3, 30).mondayOf(), LocalDate.of(y + 1, 5, 30).mondayOf()),
        )
    }

    private fun quarterOf(monday: LocalDate) = quarters.firstOrNull { monday >= it.from && monday <= it.to }

    private fun neighbor(monday: LocalDate, dir: Int): LocalDate? {
        var d = monday.plusWeeks(dir.toLong())
        repeat(6) {
            if (quarterOf(d) != null) return d
            d = d.plusWeeks(dir.toLong())
        }
        return null
    }

    private fun subjectOf(date: LocalDate, num: Int) = SCHEDULE[date.dayOfWeek.ordinal][num - 1]

    // Отметка урока: null, ABSENT или число. В версии 2 «учитель» добавляет и исправляет отметки задним числом.
    private fun lessonMark(date: LocalDate, num: Int, subject: String): Int? {
        if (subject.isEmpty() || date > today) return null
        val r = hash("$date|$num")
        var mark: Int? = when {
            r < 0.04 -> ABSENT
            r < 0.42 -> 4 + (hash("$date#$num") * 7).toInt()
            else -> null
        }
        if (version >= 2 && date < today && changed.containsKey("$date|$num")) mark = changed["$date|$num"]
        return mark
    }

    /** Что поменяется во второй версии: новая отметка на прошлом учебном дне, исправленная — неделей раньше. */
    fun bump(): Pair<LocalDate, LocalDate?> {
        version = 2
        val days = mutableListOf<LocalDate>()
        var d = today.minusDays(1)
        while (days.size < 6) {
            if (d.dayOfWeek.ordinal < 5 && quarterOf(d.mondayOf()) != null) days += d
            d = d.minusDays(1)
        }
        val prev = days[0]
        // на прошлом учебном дне — новая 10 на первом уроке без отметки
        for (n in 1..7) {
            val s = subjectOf(prev, n)
            if (s.isNotEmpty() && lessonMark(prev, n, s) == null) { changed["$prev|$n"] = 10; break }
        }
        // около недели назад — исправленная отметка (первая числовая)
        var older: LocalDate? = null
        for (day in days.drop(3)) {
            for (n in 1..7) {
                if (older != null) break
                val m = lessonMark(day, n, subjectOf(day, n))
                if (m != null && m != ABSENT) { changed["$day|$n"] = if (m >= 10) 9 else m + 1; older = day }
            }
            if (older != null) break
        }
        return prev to older
    }

    private fun dayTable(date: LocalDate, hidden: Boolean): String {
        val wd = date.dayOfWeek.ordinal
        val names = listOf("Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота")
        val tableId = "db_table_${date.dayOfMonth}.${date.monthValue}.${date.year % 100}"
        val rows = SCHEDULE[wd].mapIndexed { i, subject ->
            val num = i + 1
            val mark = lessonMark(date, num, subject)
            val shown: Any? = if (mark != null && mark != ABSENT) { if (hidden) maxOf(1, mark - 2) else mark } else if (mark == ABSENT) "н" else null
            val hwFn = HW[subject]
            val hw = if (subject.isNotEmpty() && hwFn != null && date <= today && hash("$date%$num") < 0.8) {
                hwFn((hash(date.toString() + subject) * 1000).toInt())
            } else ""
            val lessonSpan = if (subject.isNotEmpty()) "$num.\n            $subject\n        " else "$num."
            val hwHtml = if (hw.isNotEmpty()) {
                """<div class="ht-text-wrapper"><div class="ht-text">
      ${hw.split('\n').joinToString("\n      ")}
  </div></div>"""
            } else ""
            """<tr ${if (i % 2 == 1) "" else "class=\"even\""}>
        <td class="lesson "><span>$lessonSpan</span></td>
        <td class="ht">
          <div class="ht-aside"><div class="ht-box"><span class="icon icon-text-width"></span><span class="hint"><p><b>Тема урока:</b></p><p>недоступно</p></span></div>
          <div class="ht-box"><span class="icon icon-info-sign"></span><div class="hint" style="width: 220px;"><p>Создано: недоступно 12</p></div></div></div>
          $hwHtml
        </td>
        <td class="mark"><div class="mark_box ">${if (shown != null) "<strong>$shown</strong>" else ""}</div></td>
      </tr>"""
        }.joinToString("\n")
        return """<div class="db_day"><table id="$tableId" class="db_table ">
      <thead><tr><th class="lesson">${names[wd]}, ${date.dayOfMonth}</th><th class="ht">Домашнее задание</th><th class="mark">Отметка</th></tr></thead>
      <tbody>$rows</tbody></table><script>AttachmentsDropdown.initAll("$tableId");</script></div>"""
    }

    private fun weekHtml(monday: LocalDate, q: Quarter): String {
        val prev = neighbor(monday, -1)
        val next = neighbor(monday, 1)
        fun link(w: LocalDate?, cls: String) = if (w == null) "" else
            """<a id="db_period_${cls}_link_${PID}_${q.id}_$monday" class="$cls" href="#" ${cls}_week_id="$w" send_to="/pupil/$PID/dnevnik/quarter/${quarterOf(w)!!.id}/week/$w">${if (cls == "prev") "←" else "→"}</a>"""
        fun block(hidden: Boolean): String {
            val d = (0 until if (hidden) 6 else 5).map { dayTable(monday.plusDays(it.toLong()), hidden) }
            return """<div class="db_days clearfix"${if (hidden) " style=\"display: none;\"" else ""}>
        <div class="db_days_column left">${d.take(3).joinToString("")}</div>
        <div class="db_days_column right">${d.drop(3).joinToString("")}</div></div>"""
        }
        return """<div class="db_under clearfix"><div class="db_absences"><p>Пропущенных уроков: <b>1</b></p></div></div>
      <div class="db_period"><p>${link(prev, "prev")} $monday ${link(next, "next")}<img class="db_period_loader"></p></div>
      ${block(true)}
      ${block(false)}
      <div class="db_notes"><div class="title_blue">Замечания на этой неделе</div><div class="list"><div class="mess_empty">нет</div></div></div>
      <script>var quarter_id = '${q.id}', weekID = '$monday', weekUrl = '/pupil/$PID/dnevnik/quarter/${q.id}/week/$monday', pupil_id = '$PID';</script>"""
    }

    private val profile = """<!doctype html><html><head><meta charset="utf-8"><title>Тестов Тест. Обучающийся 10 "А". Тестовая школа</title></head>
    <body style="font:14px Arial;margin:0"><div style="background:#2a6ebb;color:#fff;padding:14px 20px">schools.by (выдуманный)</div>
    <div style="padding:20px"><div class="title_box"><h1>Тестов Тест, <a href="/class/1">10 "А"</a> класс, ID $PID</h1></div>
    <ul><li><a href="#dnevnik" src="/pupil/$PID/dnevnik">Дневник</a></li></ul></div></body></html>"""

    private val login = """<!doctype html><html><head><meta charset="utf-8"><title>Вход</title></head>
    <body><h1>schools.by — вход (выдуманный)</h1>
    <form action="$HOST/do-login" method="get"><input name="u" value="test"><input type="password" name="p" value="x">
    <button id="go">Войти</button></form></body></html>"""

    fun handle(url: String): FakeResponse {
        val path = java.net.URI(url).path
        requests += path
        if (path == "/login") return FakeResponse(200, login)
        if (path == "/do-login") { loggedIn = true; return FakeResponse(302, "", "$HOST/pupil/$PID") }
        if (!loggedIn) return FakeResponse(302, "", "$HOST/login")
        if (path == "/pupil/$PID") return FakeResponse(200, profile)
        if (path == "/pupil/$PID/dnevnik") {
            val cur = today.mondayOf()
            val w = if (quarterOf(cur) != null) cur else neighbor(cur, -1) ?: neighbor(cur, 1)!!
            return FakeResponse(200, weekHtml(w, quarterOf(w)!!))
        }
        Regex("^/pupil/$PID/dnevnik/quarter/(\\d+)/week/(\\d{4}-\\d{2}-\\d{2})$").find(path)?.let { m ->
            val monday = LocalDate.parse(m.groupValues[2])
            val q = quarterOf(monday)
            if (q == null || q.id != m.groupValues[1]) return FakeResponse(404, "not found")
            return FakeResponse(200, weekHtml(monday, q))
        }
        return FakeResponse(404, "not found")
    }
}

/** Транспорт к выдуманному сайту; переходы по редиректам — как в браузере. */
class FakeFetcher(val site: FakeSite) : PageFetcher {
    override suspend fun fetch(url: String): Page {
        var u = java.net.URI(url).toString()
        repeat(5) {
            val r = site.handle(u)
            if (r.status in 300..399) { u = java.net.URI(u).resolve(r.location!!).toString(); return@repeat }
            return Page(u, r.status, r.body)
        }
        error("redirect loop")
    }
}
