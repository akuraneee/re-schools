package io.github.akuraneee.reschools.ui.web

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import io.github.akuraneee.reschools.R
import io.github.akuraneee.reschools.core.LANDING_PATH
import io.github.akuraneee.reschools.ui.components.TooltipIconButton
import androidx.compose.ui.res.stringResource

private const val LOGIN_URL = "https://schools.by/login"

private fun isSchools(u: Uri): Boolean {
    val host = u.host ?: return false
    return u.scheme == "https" && (host == "schools.by" || host.endsWith(".schools.by"))
}

/** Поддомен школы (не сам schools.by — там только вход). */
private fun isSchoolHost(u: Uri) = isSchools(u) && u.host != "schools.by"

/** Waydroid (Android в контейнере на Linux): производитель «Waydroid», модель «WayDroid x86_64 Device». */
private val isWaydroid: Boolean =
    listOf(Build.MANUFACTURER, Build.BRAND, Build.MODEL, Build.DEVICE).any { it.orEmpty().contains("waydroid", ignoreCase = true) }

/**
 * Сайт schools.by в WebView. В режиме входа ([loginMode]) пользователь вводит пароль на настоящей странице,
 * а после входа сайт открывает страницу ученика/родителя (`/pupil/<id>`, `/parent/<id>`) — тогда вызываем [onLanded].
 * Приложение пароль не видит.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class) // subtitle у app bar и волнистый индикатор — экспериментальные в 1.5.0-beta
@SuppressLint("SetJavaScriptEnabled") // сайт школы без JavaScript не работает
@Composable
fun WebScreen(loginMode: Boolean, startUrl: String?, onBack: () -> Unit, onLanded: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var progress by remember { mutableFloatStateOf(0f) }
    var loading by remember { mutableStateOf(true) }
    var canGoBack by remember { mutableStateOf(false) }
    val webView = remember {
        WebView(context).also { view ->
            // В Waydroid аппаратная отрисовка WebView часто даёт чёрный экран — там рисуем программно.
            if (isWaydroid) view.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            view.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = false
                allowContentAccess = false
                setSupportMultipleWindows(false)
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            }
            CookieManager.getInstance().run {
                setAcceptCookie(true)
                setAcceptThirdPartyCookies(view, false)
            }
        }
    }
    // «Прошёл через /login» — признак того, что страница ученика открыта именно после входа
    var sawLogin by remember { mutableStateOf(false) }
    val landedHandler by rememberUpdatedState(onLanded)

    fun check(uri: Uri): Boolean {
        if (uri.path?.contains("/login") == true) sawLogin = true
        if (loginMode && sawLogin && isSchoolHost(uri) && LANDING_PATH.matches(uri.path ?: "")) {
            sawLogin = false
            landedHandler(uri.toString())
            return true
        }
        return false
    }

    DisposableEffect(webView) {
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                if (!isSchools(uri)) {
                    // Ссылки из ДЗ, tel:, mailto: и прочее — во внешних приложениях
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                    return true
                }
                return request.isForMainFrame && check(uri)
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                loading = true
                check(Uri.parse(url))
            }

            override fun onPageFinished(view: WebView, url: String) {
                loading = false
                canGoBack = view.canGoBack()
            }
        }
        webView.webChromeClient = object : android.webkit.WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) { progress = newProgress / 100f }
        }
        if (webView.url == null) webView.loadUrl(startUrl ?: LOGIN_URL)
        onDispose {
            CookieManager.getInstance().flush() // сохранить вход
            webView.stopLoading()
            webView.destroy()
        }
    }

    BackHandler(enabled = canGoBack) { webView.goBack() }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (loginMode) R.string.web_login_title else R.string.web_site_title)) },
                navigationIcon = { TooltipIconButton(R.drawable.ic_arrow_back_24, stringResource(R.string.action_back), onBack) },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
            if (loading && progress < 1f) {
                LinearWavyProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
