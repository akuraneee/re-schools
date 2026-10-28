package io.github.akuraneee.reschools.core

import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/** Ответ сайта: [url] — адрес после всех переходов (по нему видно, что нас отправили на вход). */
class Page(val url: String, val status: Int, val html: String) {
    val doc: Document by lazy { Jsoup.parse(html, url) }
}

/** Транспорт. Запросы идут с куками входа на schools.by; нет связи — [OfflineException]. */
fun interface PageFetcher {
    suspend fun fetch(url: String): Page

    /** Отправка формы (POST, application/x-www-form-urlencoded) с переходами по редиректам — нужна только для входа. */
    suspend fun post(url: String, fields: List<Pair<String, String>>, referer: String): Page =
        throw UnsupportedOperationException("POST не поддерживается")
}

/** Ответ вместо страницы дневника → [AuthException] (нужен вход) или ошибка HTTP. */
suspend fun PageFetcher.fetchChecked(url: String): Page {
    val page = fetch(url)
    if (Regex("/login").containsMatchIn(page.url)) throw AuthException()
    if (page.status >= 400) throw java.io.IOException("HTTP ${page.status}: $url")
    if (page.doc.selectFirst("input[type=password]") != null && page.doc.selectFirst(Sel.DAY) == null) throw AuthException()
    return page
}

/** Страница ученика/родителя, на которую сайт переводит после входа. */
val LANDING_PATH = Regex("^/(pupil|parent)/\\d+/?$")

suspend fun PageFetcher.discoverPupils(url: String): List<Pupil> {
    val page = fetchChecked(url)
    return findPupils(page.doc, page.url)
}
