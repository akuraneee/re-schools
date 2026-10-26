package io.github.akuraneee.reschools.data

import android.webkit.CookieManager
import io.github.akuraneee.reschools.core.OfflineException
import io.github.akuraneee.reschools.core.Page
import io.github.akuraneee.reschools.core.PageFetcher
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * Запросы к schools.by через OkHttp. Куки входа общие с WebView, в котором пользователь входит на сайте:
 * приложение пароль не видит и не хранит, а сессию берёт из [CookieManager].
 */
class OkHttpFetcher(private val userAgent: String) : PageFetcher {
    private val client = OkHttpClient.Builder()
        .cookieJar(WebViewCookieJar)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    override suspend fun fetch(url: String): Page {
        // Сайт сам грузит дневник через XHR — повторяем те же заголовки.
        val request = Request.Builder()
            .url(url)
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Accept", "text/html, */*; q=0.01")
            // как у браузера: сайт может по-другому отвечать клиентам с чужим User-Agent и без Referer
            .header("User-Agent", userAgent)
            .header("Accept-Language", "ru-RU,ru;q=0.9")
            .header("Referer", Regex("^https?://[^/]+").find(url)?.value.orEmpty() + "/")
            .build()
        return try {
            client.newCall(request).await().use { Page(it.request.url.toString(), it.code, it.body.string()) }
        } catch (e: IOException) {
            throw OfflineException()
        }
    }

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { cont.resumeWithException(e) }
            override fun onResponse(call: Call, response: Response) { cont.resume(response) }
        })
    }
}

private object WebViewCookieJar : CookieJar {
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val raw = CookieManager.getInstance().getCookie(url.toString()) ?: return emptyList()
        return raw.split(';').mapNotNull { Cookie.parse(url, it.trim()) }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val manager = CookieManager.getInstance()
        for (cookie in cookies) manager.setCookie(url.toString(), cookie.toString())
    }
}
