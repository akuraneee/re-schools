package io.github.akuraneee.reschools.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import io.github.akuraneee.reschools.R
import io.github.akuraneee.reschools.core.Bells
import io.github.akuraneee.reschools.core.HomeworkItem
import io.github.akuraneee.reschools.core.Lesson
import io.github.akuraneee.reschools.core.NowStatus
import io.github.akuraneee.reschools.core.Ru
import io.github.akuraneee.reschools.core.Slot
import io.github.akuraneee.reschools.core.elapsedFraction
import io.github.akuraneee.reschools.core.minutesLeft
import io.github.akuraneee.reschools.core.nowStatus
import io.github.akuraneee.reschools.core.subjectKey
import io.github.akuraneee.reschools.core.subjectTitle
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.delay

/** Текущее время для «что сейчас»; null — часов нет (скриншот-тесты без них, блоки не показываются). */
val LocalNow = compositionLocalOf<LocalDateTime?> { null }

/** Часы обновляются раз в 10 секунд, пока приложение на экране. */
@Composable
fun ProvideNow(content: @Composable () -> Unit) {
    var ticking by remember { mutableStateOf(false) }
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { ticking = true }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { ticking = false }
    LaunchedEffect(ticking) {
        while (ticking) {
            now = LocalDateTime.now()
            delay(10_000)
        }
    }
    CompositionLocalProvider(LocalNow provides now, content = content)
}

/** Что идёт сейчас и который час (секунды от полуночи). */
class NowInfo(val status: NowStatus, val seconds: Int)

/** null — часов нет или «сегодня» в состоянии не совпадает с датой на часах. */
@Composable
fun rememberNowInfo(lessons: List<Lesson>, today: LocalDate, bells: Bells): NowInfo? {
    val now = LocalNow.current ?: return null
    if (now.toLocalDate() != today) return null
    val seconds = now.toLocalTime().toSecondOfDay()
    return NowInfo(nowStatus(lessons, today, seconds, bells), seconds)
}

private fun Slot.title() = lessons.joinToString(" / ") { subjectTitle(it.subject) }

/** Идёт урок: что за урок, сколько осталось до звонка (прогресс урока) и что дальше. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class) // волнистый индикатор — экспериментальный в 1.5.0-beta
@Composable
fun LessonNowCard(cur: Slot, next: Slot?, seconds: Int, modifier: Modifier = Modifier) {
    val progress by animateFloatAsState(
        elapsedFraction(seconds, cur.start, cur.end),
        MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "lessonProgress",
    )
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.now_lesson, cur.num),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                Text("${Ru.clock(cur.start)}–${Ru.clock(cur.end)}", style = MaterialTheme.typography.labelLarge)
            }
            Text(cur.title(), style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(R.string.now_left, Ru.duration(minutesLeft(seconds, cur.end))),
                style = MaterialTheme.typography.bodyLarge,
            )
            LinearWavyProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            if (next != null) {
                Text(
                    stringResource(R.string.now_then, next.title(), Ru.clock(next.start)),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/** Перемена: компактный блок со временем до звонка. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BreakCard(prevEnd: Int, nextStart: Int, seconds: Int, modifier: Modifier = Modifier) {
    val progress by animateFloatAsState(
        elapsedFraction(seconds, prevEnd, nextStart),
        MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "breakProgress",
    )
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.now_break),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                Text(
                    stringResource(R.string.now_bell_in, Ru.duration(minutesLeft(seconds, nextStart))),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LinearWavyProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Следующий урок (на перемене или до начала уроков) с ДЗ к нему. */
@Composable
fun NextLessonCard(next: Slot, label: String, homework: List<HomeworkItem>, modifier: Modifier = Modifier) {
    val keys = next.lessons.map { subjectKey(it.subject) }.toSet()
    val hw = homework.filter { next.num in it.nums || subjectKey(it.subject) in keys }.map { it.hw }.filter { it.isNotEmpty() }.distinct()
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(next.title(), style = MaterialTheme.typography.titleLarge)
            if (hw.isEmpty()) {
                Text(stringResource(R.string.now_no_homework), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(rememberLinkified(hw.joinToString("\n")), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
