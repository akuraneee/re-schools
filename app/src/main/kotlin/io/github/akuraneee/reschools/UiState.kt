package io.github.akuraneee.reschools

import androidx.compose.runtime.Immutable
import io.github.akuraneee.reschools.core.Lesson
import io.github.akuraneee.reschools.core.NewMark
import io.github.akuraneee.reschools.core.Pupil
import io.github.akuraneee.reschools.core.Settings
import io.github.akuraneee.reschools.core.Snapshot
import io.github.akuraneee.reschools.core.Week
import java.time.LocalDate

sealed interface UiError {
    /** Вход на schools.by истёк — нужно войти заново. */
    data object Auth : UiError

    /** Нет связи: показаны сохранённые данные. */
    data object Offline : UiError

    data class Other(val message: String) : UiError
}

@Immutable
data class UiState(
    /** Профиль и кэш прочитаны с диска. */
    val ready: Boolean = false,
    val pupils: List<Pupil> = emptyList(),
    val pupil: Pupil? = null,
    val settings: Settings = Settings(),
    val today: LocalDate = LocalDate.now(),
    val weeks: Map<LocalDate, Week> = emptyMap(),
    val lessons: List<Lesson> = emptyList(),
    /** null — сравнивать не с чем (первый заход). */
    val newMarks: List<NewMark>? = null,
    val baseline: Snapshot? = null,
    val lastSync: Long? = null,
    val loading: Boolean = false,
    /** Сколько недель из скольких уже обновлено. */
    val progress: Pair<Int, Int>? = null,
    val weekLoading: Set<LocalDate> = emptySet(),
    /** Подгрузка недель учебного года для статистики: сколько из скольких. */
    val historyProgress: Pair<Int, Int>? = null,
    val signingIn: Boolean = false,
    val error: UiError? = null,
) {
    val needLogin: Boolean get() = ready && pupil == null
    val hasData: Boolean get() = weeks.isNotEmpty()
}
