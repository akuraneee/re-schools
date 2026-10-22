package io.github.akuraneee.reschools

import android.app.Application
import android.webkit.CookieManager
import android.webkit.WebSettings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.akuraneee.reschools.core.AuthException
import io.github.akuraneee.reschools.core.HwMode
import io.github.akuraneee.reschools.core.OfflineException
import io.github.akuraneee.reschools.core.Persist
import io.github.akuraneee.reschools.core.Profile
import io.github.akuraneee.reschools.core.Pupil
import io.github.akuraneee.reschools.core.PupilSession
import io.github.akuraneee.reschools.core.discoverPupils
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
                val pupils = fetcher.discoverPupils(url)
                if (pupils.isEmpty()) {
                    publish { it.copy(error = UiError.Other(getApplication<Application>().getString(R.string.no_pupil_found))) }
                } else {
                    refreshJob?.cancel()
                    profile.setPupils(pupils)
                    profile.select(pupils.first().id)
                    startSession(pupils.first())
                    publish { it.copy(loading = false, progress = null, weekLoading = emptySet()) }
                    refresh()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                publish { it.copy(error = errorOf(e)) }
            } finally {
                publish { it.copy(signingIn = false) }
            }
        }
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
