package io.github.akuraneee.reschools

import android.app.Application
import android.webkit.CookieManager
import android.webkit.WebSettings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.akuraneee.reschools.core.AuthException
import io.github.akuraneee.reschools.core.Bells
import io.github.akuraneee.reschools.core.HwMode
import io.github.akuraneee.reschools.core.LoginResult
import io.github.akuraneee.reschools.core.OfflineException
import io.github.akuraneee.reschools.core.Persist
import io.github.akuraneee.reschools.core.Profile
import io.github.akuraneee.reschools.core.Pupil
import io.github.akuraneee.reschools.core.PupilSession
import io.github.akuraneee.reschools.core.discoverPupils
import io.github.akuraneee.reschools.core.signIn
import io.github.akuraneee.reschools.data.FileStore
import io.github.akuraneee.reschools.data.OkHttpFetcher
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Всё состояние приложения; логика дневника — в ядре (`core`), здесь только связь с интерфейсом. */
class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val persist = Persist(FileStore(File(application.filesDir, "rs")))
    private val profile = Profile(persist)
    private val fetcher = OkHttpFetcher(WebSettings.getDefaultUserAgent(application))

    @Volatile private var session: PupilSession? = null
    private var refreshJob: Job? = null
    private val historyDone = mutableSetOf<String>()

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            profile.current?.let(::startSession)
            publish()
            refresh()
        }
    }

    // ---------- состояние ----------

    private fun startSession(pupil: Pupil) {
        session = PupilSession(pupil, fetcher, persist)
    }

    /** Переносит данные ядра в состояние интерфейса. */
    private fun publish(change: (UiState) -> UiState = { it }) {
        val s = session
        _state.update { old ->
            change(
                old.copy(
                    ready = true,
                    pupils = profile.pupils,
                    pupil = s?.pupil,
                    settings = profile.settings,
                    today = s?.today() ?: LocalDate.now(),
                    weeks = s?.diary?.weeks.orEmpty(),
                    lessons = s?.diary?.allLessons().orEmpty(),
                    links = s?.diary?.links.orEmpty(),
                    newMarks = s?.newMarks(),
                    baseline = s?.visit?.baseline,
                    lastSync = s?.lastSync,
                ),
            )
        }
    }

    private fun errorOf(e: Throwable): UiError = when (e) {
        is AuthException -> UiError.Auth
        is OfflineException -> UiError.Offline
        else -> UiError.Other(e.message ?: e.toString())
    }

    // ---------- действия ----------

    fun refresh() {
        val s = session ?: return
        if (_state.value.loading) return
        refreshJob = viewModelScope.launch(Dispatchers.IO) {
            publish { it.copy(loading = true, error = null, progress = null) }
            var error: UiError? = null
            try {
                s.refresh { done, total -> publish { it.copy(progress = done to total) } }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = errorOf(e)
            } finally {
                // ученика могли сменить, пока шло обновление — тогда состояние уже чужое
                if (session === s) publish { it.copy(loading = false, progress = null, error = error) }
            }
        }
    }

    /** Неделя, открытая в дневнике: из кэша, если уже грузили в этом заходе, иначе с сайта. */
    fun openWeek(monday: LocalDate, force: Boolean = false) {
        val s = session ?: return
        if (monday in _state.value.weekLoading) return
        if (!force && s.diary.isFresh(monday) && s.diary.cached(monday) != null) return
        viewModelScope.launch(Dispatchers.IO) {
            publish { it.copy(weekLoading = it.weekLoading + monday) }
            var error: UiError? = null
            try {
                s.openWeek(monday, force)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = errorOf(e)
            } finally {
                if (session === s) publish { st -> st.copy(weekLoading = st.weekLoading - monday, error = error ?: st.error) }
            }
        }
    }

    /** Недели с начала учебного года — для статистики. Один раз за сессию ученика; ошибки показываем как обычно. */
    fun loadHistory() {
        val s = session ?: return
        if (_state.value.historyProgress != null || s.pupil.id in historyDone) return
        viewModelScope.launch(Dispatchers.IO) {
            publish { it.copy(historyProgress = 0 to 1) }
            var error: UiError? = null
            try {
                s.loadHistory { done, total -> publish { it.copy(historyProgress = done to total) } }
                historyDone += s.pupil.id
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = errorOf(e)
            } finally {
                if (session === s) publish { st -> st.copy(historyProgress = null, error = error ?: st.error) }
            }
        }
    }

    fun selectPupil(id: String) {
        val pupil = profile.pupils.firstOrNull { it.id == id } ?: return
        if (pupil.id == session?.pupil?.id) return
        viewModelScope.launch(Dispatchers.IO) {
            refreshJob?.cancel()
            profile.select(id)
            startSession(pupil)
            publish { it.copy(loading = false, progress = null, error = null, weekLoading = emptySet()) }
            refresh()
        }
    }

    fun setHomeworkMode(mode: HwMode) {
        profile.setSettings(profile.settings.copy(hwMode = mode))
        publish()
    }

    fun setBells(value: Bells) {
        profile.setSettings(profile.settings.copy(bells = value))
        publish()
    }

    fun setThreshold(value: Double) {
        profile.setSettings(profile.settings.copy(threshold = value))
        publish()
    }

    /** Удалить сохранённый дневник выбранного ученика и загрузить заново. */
    fun clearData() {
        val s = session ?: return
        historyDone -= s.pupil.id
        viewModelScope.launch(Dispatchers.IO) {
            refreshJob?.cancel()
            s.clear()
            publish { it.copy(loading = false, progress = null, error = null) }
            refresh()
        }
    }

    /** Вход на сайте закончился страницей ученика/родителя [url]: узнаём, чей это дневник, и грузим его. */
    fun completeLogin(url: String) {
        viewModelScope.launch(Dispatchers.IO) {
            publish { it.copy(signingIn = true, error = null) }
            try {
                openLanded(url)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                publish { it.copy(error = errorOf(e)) }
            } finally {
                publish { it.copy(signingIn = false) }
            }
        }
    }

    /**
     * Вход в приложении: форма сайта отправляется без WebView. [onDone] — дневник найден;
     * [onNeedsBrowser] — на форме капча или незнакомая разметка, входить на самом сайте.
     */
    fun signIn(user: String, password: String, onDone: () -> Unit, onNeedsBrowser: () -> Unit) {
        if (_state.value.signingIn) return
        viewModelScope.launch(Dispatchers.IO) {
            publish { it.copy(signingIn = true, loginError = null, error = null) }
            val app = getApplication<Application>()
            var done = false
            try {
                when (val r = fetcher.signIn(user, password)) {
                    is LoginResult.Landed -> done = openLanded(r.url)
                    is LoginResult.Rejected -> publish { it.copy(loginError = r.message ?: app.getString(R.string.login_rejected)) }
                    LoginResult.NeedsBrowser -> withContext(Dispatchers.Main) { onNeedsBrowser() }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = when (e) {
                    is OfflineException -> app.getString(R.string.error_offline_login)
                    else -> app.getString(R.string.login_failed, e.message ?: e.toString())
                }
                publish { it.copy(loginError = message) }
            } finally {
                publish { it.copy(signingIn = false) }
            }
            if (done) withContext(Dispatchers.Main) { onDone() }
        }
    }

    fun clearLoginError() = publish { it.copy(loginError = null) }

    /** Страница [url] после входа: ищем на ней учеников и начинаем с первого. false — ученика не нашли. */
    private suspend fun openLanded(url: String): Boolean {
        val pupils = fetcher.discoverPupils(url)
        if (pupils.isEmpty()) {
            val message = getApplication<Application>().getString(R.string.no_pupil_found)
            publish { it.copy(error = UiError.Other(message), loginError = message) }
            return false
        }
        refreshJob?.cancel()
        profile.setPupils(pupils)
        profile.select(pupils.first().id)
        startSession(pupils.first())
        publish { it.copy(loading = false, progress = null, weekLoading = emptySet()) }
        refresh()
        return true
    }

    /** Перед входом в другой аккаунт забываем текущий вход на сайте. */
    fun forgetSiteLogin() {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
    }

    // ---------- заходы ----------

    fun onAppStarted() {
        val s = session ?: return
        // Вернулись после перерыва — это новый заход: новые отметки считаются от прошлого.
        if (s.onForeground()) {
            publish()
            refresh()
        } else {
            publish() // сменилась дата — «сегодня» и «завтра» пересчитаются
        }
    }

    fun onAppStopped() {
        session?.onBackground()
    }
}
