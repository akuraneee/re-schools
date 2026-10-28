package io.github.akuraneee.reschools.core

import org.jsoup.nodes.Document

const val LOGIN_URL = "https://schools.by/login"

/** Форма входа со страницы сайта: куда отправлять, скрытые поля (CSRF-токен Django и т. п.) и имена полей логина и пароля. */
class LoginForm(val action: String, val hidden: List<Pair<String, String>>, val userField: String, val passField: String) {
    fun fields(user: String, password: String) = hidden + (userField to user) + (passField to password)
}

/** Ищет на странице форму с полем пароля; null — формы нет или на ней капча (тогда входить только на сайте). */
fun findLoginForm(doc: Document): LoginForm? {
    val form = doc.select("form").firstOrNull { it.selectFirst("input[type=password]") != null } ?: return null
    if (form.selectFirst(".g-recaptcha, .h-captcha, [data-sitekey], input[name*=captcha], img[src*=captcha]") != null) return null
    val pass = form.selectFirst("input[type=password]")!!.attr("name").ifEmpty { return null }
    val user = form.select("input").firstOrNull {
        it.attr("type").lowercase() in setOf("", "text", "email", "tel") && it.attr("name").isNotEmpty()
    }?.attr("name") ?: return null
    val hidden = form.select("input[type=hidden]").filter { it.attr("name").isNotEmpty() }.map { it.attr("name") to it.attr("value") }
    val action = form.attr("abs:action").ifEmpty { doc.location() }
    return LoginForm(action, hidden, user, pass)
}

sealed interface LoginResult {
    /** Вход удался, сайт открыл [url] (обычно страницу ученика/родителя). */
    data class Landed(val url: String) : LoginResult

    /** Сайт снова показал форму: неверный логин или пароль. [message] — текст ошибки с сайта, если нашёлся. */
    data class Rejected(val message: String?) : LoginResult

    /** Форма не та, что ожидали (капча, другая разметка) — входить на самом сайте. */
    data object NeedsBrowser : LoginResult
}

/**
 * Вход без WebView: берём форму со страницы [loginUrl] (с CSRF-токеном) и отправляем её, как браузер.
 * Куки сессии сохраняет транспорт; пароль нигде не остаётся.
 */
suspend fun PageFetcher.signIn(user: String, password: String, loginUrl: String = LOGIN_URL): LoginResult {
    val page = fetch(loginUrl)
    if (page.status >= 400) throw java.io.IOException("HTTP ${page.status}: $loginUrl")
    // уже вошли раньше — сайт сразу открыл свою страницу
    if (page.doc.selectFirst("input[type=password]") == null && !page.url.contains("/login")) return LoginResult.Landed(page.url)
    val form = findLoginForm(page.doc) ?: return LoginResult.NeedsBrowser
    val result = post(form.action, form.fields(user.trim(), password), referer = page.url)
    if (result.status >= 500) throw java.io.IOException("HTTP ${result.status}: ${form.action}")
    if (result.doc.selectFirst("input[type=password]") != null || result.url.contains("/login")) {
        if (findLoginForm(result.doc) == null && result.doc.selectFirst("input[type=password]") != null) return LoginResult.NeedsBrowser
        val message = result.doc.select(".errorlist, .error, .errors, .alert-danger, .alert-error, .form-error, .err")
            .map { it.text().trim() }.firstOrNull { it.isNotEmpty() }
        return LoginResult.Rejected(message)
    }
    if (result.status >= 400) throw java.io.IOException("HTTP ${result.status}: ${result.url}")
    return LoginResult.Landed(result.url)
}
