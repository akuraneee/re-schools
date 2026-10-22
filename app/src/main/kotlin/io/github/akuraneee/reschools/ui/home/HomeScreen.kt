package io.github.akuraneee.reschools.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.akuraneee.reschools.R
import io.github.akuraneee.reschools.UiState
import io.github.akuraneee.reschools.core.HomeworkItem
import io.github.akuraneee.reschools.core.Lesson
import io.github.akuraneee.reschools.core.NewMark
import io.github.akuraneee.reschools.core.NowStatus
import io.github.akuraneee.reschools.core.minutesLeft
import io.github.akuraneee.reschools.core.Ru
import io.github.akuraneee.reschools.core.homeworkFor
import io.github.akuraneee.reschools.core.nextSchoolDay
import io.github.akuraneee.reschools.core.subjectTitle
import io.github.akuraneee.reschools.core.todayMarks
import io.github.akuraneee.reschools.ui.components.ContentMaxWidth
import io.github.akuraneee.reschools.ui.components.EmptyNote
import io.github.akuraneee.reschools.ui.components.BreakCard
import io.github.akuraneee.reschools.ui.components.LessonNowCard
import io.github.akuraneee.reschools.ui.components.LessonNumber
import io.github.akuraneee.reschools.ui.components.NextLessonCard
import io.github.akuraneee.reschools.ui.components.NowInfo
import io.github.akuraneee.reschools.ui.components.rememberNowInfo
import io.github.akuraneee.reschools.ui.components.MarksRow
import io.github.akuraneee.reschools.ui.components.SectionHeader
import io.github.akuraneee.reschools.ui.components.SegmentedList
import io.github.akuraneee.reschools.ui.components.SegmentedNote
import io.github.akuraneee.reschools.ui.components.StatusBanner
import io.github.akuraneee.reschools.ui.components.TooltipIconButton
import io.github.akuraneee.reschools.ui.components.contentPaddingOf
import io.github.akuraneee.reschools.ui.components.mergedRow
import io.github.akuraneee.reschools.ui.components.segmentedColors
import io.github.akuraneee.reschools.ui.components.rememberLinkified

/**
 * «Главная»: что идёт сейчас (урок или перемена), новые отметки с прошлого захода (главный акцент экрана), отметки за сегодня и ДЗ на следующий учебный день.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class) // гибкие app bar и LoadingIndicator — экспериментальные в 1.5.0-beta
@Composable
fun HomeScreen(
    state: UiState,
    contentInsets: WindowInsets,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
    onLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = contentInsets,
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.nav_home)) },
                subtitle = { state.pupil?.let { Text(it.name) } },
                actions = {
                    TooltipIconButton(R.drawable.ic_refresh_24, stringResource(R.string.action_refresh), onRefresh, enabled = !state.loading)
                    TooltipIconButton(R.drawable.ic_settings_24, stringResource(R.string.action_settings), onOpenSettings)
                },
                scrollBehavior = scrollBehavior,
                windowInsets = contentInsets,
            )
        },
    ) { padding ->
        val pullState = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = state.loading,
            onRefresh = onRefresh,
            state = pullState,
            modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()),
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    state = pullState,
                    isRefreshing = state.loading,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            },
        ) {
            if (!state.hasData && state.error == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
            } else {
                HomeContent(state, padding, onRefresh, onLogin)
            }
        }
    }
}

@Composable
private fun HomeContent(state: UiState, padding: PaddingValues, onRefresh: () -> Unit, onLogin: () -> Unit) {
    val now = rememberNowInfo(state.lessons, state.today, state.settings.bells)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth(),
            contentPadding = contentPaddingOf(padding),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item(key = "status") {
                StatusBanner(state, onRetry = onRefresh, onLogin = onLogin)
            }
            if (!state.hasData) {
                item(key = "empty") { EmptyNote(stringResource(R.string.not_loaded)) }
            } else {
                if (now != null && now.status.shown()) item(key = "now") { NowSection(state, now) }
                item(key = "new") { NewMarksSection(state) }
                item(key = "today") { TodaySection(state) }
                item(key = "homework") { HomeworkSection(state) }
                state.lastSync?.let { at ->
                    item(key = "synced") {
                        Text(
                            text = stringResource(R.string.home_updated, Ru.whenText(at, state.today)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

// ---------- что сейчас ----------

private fun NowStatus.shown() = this is NowStatus.InLesson || this is NowStatus.Break || this is NowStatus.Before

/** Идёт урок — карточка урока; перемена — блок «Перемена» и под ним следующий урок с ДЗ; до уроков — первый урок. */
@Composable
private fun NowSection(state: UiState, now: NowInfo) {
    val homework = homeworkFor(state.lessons, state.today, state.settings.hwMode)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (val st = now.status) {
            is NowStatus.InLesson -> LessonNowCard(st.cur, st.next, now.seconds)
            is NowStatus.Break -> {
                BreakCard(st.prev.end, st.next.start, now.seconds)
                NextLessonCard(st.next, stringResource(R.string.now_next, st.next.num, Ru.clock(st.next.start)), homework)
            }
            is NowStatus.Before -> NextLessonCard(
                st.next,
                stringResource(R.string.now_first, Ru.duration(minutesLeft(now.seconds, st.next.start)), Ru.clock(st.next.start)),
                homework,
            )
            else -> Unit
        }
    }
}

// ---------- плитки отметок ----------

/** Отметки плитками по две в ряд: отметка крупно, под ней предмет и подпись. */
@Composable
private fun MarkTiles(lessons: List<Lesson>, meta: @Composable (Int) -> String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        lessons.indices.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { i -> MarkTile(lessons[i], meta(i), Modifier.weight(1f).fillMaxHeight()) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MarkTile(l: Lesson, meta: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.mergedRow(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MarksRow(l)
            Text(subjectTitle(l.subject), style = MaterialTheme.typography.titleMedium)
            Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---------- новые отметки ----------

@Composable
private fun NewMarksSection(state: UiState) {
    val list = state.newMarks
    val since = state.baseline?.let { Ru.whenText(it.at, state.today) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!list.isNullOrEmpty() && since != null) {
            NewMarksHero(count = list.size, since = since)
        } else {
            SectionHeader(stringResource(R.string.home_new_marks))
        }
        when {
            list == null -> SegmentedNote(stringResource(R.string.home_new_first))
            list.isEmpty() -> SegmentedNote(stringResource(R.string.home_new_none, since.orEmpty()))
            else -> MarkTiles(list.map { it.lesson }) { i -> newMarkMeta(list[i]) }
        }
    }
}

/** Главный акцент экрана: крупный контейнер с числом новых отметок в «печенье» MaterialShapes. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class) // MaterialShapes — экспериментальные в 1.5.0-beta
@Composable
private fun NewMarksHero(count: Int, since: String) {
    val countDescription = stringResource(R.string.home_new_count_cd, count)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Row(
            Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.home_new_marks),
                    style = MaterialTheme.typography.headlineSmallEmphasized,
                    modifier = Modifier.semantics { heading() },
                )
                Text(text = stringResource(R.string.home_new_since, since), style = MaterialTheme.typography.bodyMedium)
            }
            Surface(
                modifier = Modifier.size(56.dp).semantics { contentDescription = countDescription },
                shape = MaterialShapes.Cookie9Sided.toShape(),
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(count.toString(), style = MaterialTheme.typography.titleLargeEmphasized)
                }
            }
        }
    }
}

@Composable
private fun newMarkMeta(item: NewMark): String {
    val l = item.lesson
    val corrected = item.was?.let { stringResource(R.string.mark_corrected, it) }
    return buildList {
        add(Ru.dateShort(l.date))
        add(stringResource(R.string.lesson_number, l.num.toString()))
        corrected?.let(::add)
    }.joinToString(" · ")
}

// ---------- сегодня ----------

@Composable
private fun TodaySection(state: UiState) {
    val today = state.today
    val marked = todayMarks(state.lessons, today)
    val hasLessons = state.lessons.any { it.date == today }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(
            title = stringResource(R.string.home_today),
            subtitle = "${Ru.dayName(today).lowercase()}, ${Ru.dateLong(today)}",
        )
        when {
            marked.isNotEmpty() -> MarkTiles(marked) { i -> stringResource(R.string.lesson_number, marked[i].num.toString()) }
            !hasLessons -> SegmentedNote(
                stringResource(if (today.dayOfWeek.value == 7) R.string.home_today_weekend else R.string.home_today_no_lessons),
            )
            else -> SegmentedNote(stringResource(R.string.home_today_no_marks))
        }
    }
}

// ---------- ДЗ ----------

@Composable
private fun HomeworkSection(state: UiState) {
    val today = state.today
    val target = nextSchoolDay(state.lessons, today)
    val items = homeworkFor(state.lessons, target, state.settings.hwMode)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(
            title = stringResource(R.string.home_homework, Ru.relDay(target, today)),
            subtitle = Ru.dateLong(target),
        )
        if (items.isEmpty()) {
            SegmentedNote(stringResource(R.string.home_homework_no_schedule, Ru.dateLong(target)))
        } else {
            SegmentedList(items.size) { index, shapes -> HomeworkRow(items[index], showFrom = state.settings.hwMode == io.github.akuraneee.reschools.core.HwMode.Assigned, shapes) }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HomeworkRow(item: HomeworkItem, showFrom: Boolean, shapes: androidx.compose.material3.ListItemShapes) {
    val nums = if (item.nums.size > 1) "${item.nums.first()}–${item.nums.last()}" else item.nums.first().toString()
    SegmentedListItem(
        shapes = shapes,
        colors = segmentedColors(),
        modifier = Modifier.mergedRow(),
        verticalAlignment = Alignment.Top,
        leadingContent = { LessonNumber(nums) },
        supportingContent = {
            Column {
                if (item.hw.isNotEmpty()) {
                    Text(rememberLinkified(item.hw), style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(stringResource(R.string.home_homework_none), style = MaterialTheme.typography.bodyMedium)
                }
                if (showFrom && item.hw.isNotEmpty() && item.from != null) {
                    Text(
                        text = stringResource(R.string.home_homework_from, Ru.dateShort(item.from!!)),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        },
    ) {
        Text(subjectTitle(item.subject))
    }
}
