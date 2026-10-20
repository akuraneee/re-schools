package io.github.akuraneee.reschools;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowInsets;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Оболочка re:schools. Интерфейс (assets/web) открывается по адресу https://<школа>.schools.by/__rs/index.html:
 * файлы подставляем сами в shouldInterceptRequest, а запросы дневника со страницы идут на тот же домен с куками
 * входа — как у самого сайта, без CORS и без пароля в приложении.
 * Вход — на настоящей странице schools.by/login; после него сайт открывает /pupil/<id> (или /parent/<id>),
 * и мы переключаемся на приложение.
 */
public class MainActivity extends Activity {
    private static final String LOGIN_URL = "https://schools.by/login";
    private static final String APP_PATH = "/__rs/";

    private static final Map<String, String> TYPES = new HashMap<>();
    static {
        TYPES.put("html", "text/html");
        TYPES.put("js", "text/javascript");
        TYPES.put("css", "text/css");
        TYPES.put("svg", "image/svg+xml");
        TYPES.put("webmanifest", "application/manifest+json");
        TYPES.put("png", "image/png");
    }

    private WebView web;
    private SharedPreferences prefs;
    private boolean loginFlow = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("rs", MODE_PRIVATE);
        int bg = getColor(R.color.bg);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(bg);
        web = new WebView(this);
        web.setBackgroundColor(bg);
        root.addView(web, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);

        // Android 15 рисует приложение под статус-баром и панелью навигации — отодвигаем содержимое.
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                Insets i = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime());
                v.setPadding(i.left, i.top, i.right, i.bottom);
            } else {
                v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            }
            return insets;
        });

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true); // здесь приложение хранит дневник и снимок отметок для «новых»
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setSupportMultipleWindows(false); // target=_blank — в этом же окне, а внешние ссылки уйдут в браузер
        s.setTextZoom(100);

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(web, false);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if (!isSchoolHost(u) || u.getPath() == null || !u.getPath().startsWith(APP_PATH)) return null;
                return serveAsset(u.getPath().substring(APP_PATH.length()));
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (!isSchools(uri)) {
                    // Ссылки из ДЗ, tel:, mailto: и прочее — во внешних приложениях
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, uri));
                    } catch (Exception ignored) {
                        // нет приложения для такой ссылки
                    }
                    return true;
                }
                noteLogin(uri);
                if (request.isForMainFrame() && openAppIfLanded(uri)) return true;
                return false;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                Uri uri = Uri.parse(url);
                noteLogin(uri);
                if (isApp(uri)) {
                    prefs.edit().putString("appUrl", uri.getScheme() + "://" + uri.getHost() + APP_PATH + "index.html").apply();
                } else {
                    openAppIfLanded(uri); // на случай, если переход не прошёл через shouldOverrideUrlLoading
                }
            }
        });

        if (savedInstanceState != null) {
            web.restoreState(savedInstanceState);
        } else {
            String app = prefs.getString("appUrl", null);
            if (app == null) loginFlow = true;
            web.loadUrl(app != null ? app : LOGIN_URL);
        }
    }

    private void noteLogin(Uri u) {
        String p = u.getPath();
        if (p != null && p.contains("/login")) loginFlow = true;
    }

    /** После входа сайт открыл страницу ученика/родителя — показываем приложение на домене школы. */
    private boolean openAppIfLanded(Uri u) {
        if (!loginFlow || !isSchoolHost(u)) return false;
        String p = u.getPath();
        if (p == null || !p.matches("^/(pupil|parent)/\\d+/?$")) return false;
        loginFlow = false;
        String app = "https://" + u.getHost() + APP_PATH + "index.html";
        web.stopLoading();
        web.loadUrl(app + "?from=" + Uri.encode(p));
        return true;
    }

    private WebResourceResponse serveAsset(String rel) {
        if (rel.isEmpty()) rel = "index.html";
        int q = rel.indexOf('?');
        if (q >= 0) rel = rel.substring(0, q);
        if (rel.contains("..") || rel.startsWith("/")) return notFound();
        String ext = rel.substring(rel.lastIndexOf('.') + 1);
        String type = TYPES.containsKey(ext) ? TYPES.get(ext) : "application/octet-stream";
        try {
            InputStream in = getAssets().open("web/" + rel);
            Map<String, String> headers = new HashMap<>();
            headers.put("Cache-Control", "no-store");
            return new WebResourceResponse(type, "utf-8", 200, "OK", headers, in);
        } catch (IOException e) {
            return notFound();
        }
    }

    private static WebResourceResponse notFound() {
        return new WebResourceResponse("text/plain", "utf-8", 404, "Not Found", new HashMap<>(), null);
    }

    private static boolean isSchools(Uri u) {
        String host = u.getHost();
        return "https".equals(u.getScheme()) && host != null && (host.equals("schools.by") || host.endsWith(".schools.by"));
    }

    /** Поддомен школы (не сам schools.by — там только вход). */
    private static boolean isSchoolHost(Uri u) {
        return isSchools(u) && !"schools.by".equals(u.getHost());
    }

    private static boolean isApp(Uri u) {
        return isSchoolHost(u) && u.getPath() != null && u.getPath().startsWith(APP_PATH);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        String url = web.getUrl();
        if (url != null && isApp(Uri.parse(url))) {
            // В приложении «Назад» уводит из настроек/дневника на главную, с главной — сворачивает.
            web.evaluateJavascript("!!(window.RS && RS.app && RS.app.back())", v -> {
                if (!"true".equals(v)) moveTaskToBack(true);
            });
            return;
        }
        if (web.canGoBack()) {
            web.goBack();
            return;
        }
        String app = prefs.getString("appUrl", null);
        if (app != null) web.loadUrl(app);
        else super.onBackPressed();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        web.saveState(outState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
    }

    @Override
    protected void onPause() {
        CookieManager.getInstance().flush(); // сохранить вход
        web.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        web.destroy();
        super.onDestroy();
    }
}
