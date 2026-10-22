package io.github.akuraneee.reschools.ui.diary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.animateFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.akuraneee.reschools.R
import io.github.akuraneee.reschools.UiState
import io.github.akuraneee.reschools.core.Bells
import io.github.akuraneee.reschools.core.Lesson
import io.github.akuraneee.reschools.core.NowStatus
import io.github.akuraneee.reschools.core.minutesLeft
import io.github.akuraneee.reschools.core.Ru
import io.github.akuraneee.reschools.core.Week
import io.github.akuraneee.reschools.core.formatMean
import io.github.akuraneee.reschools.core.mean
import io.github.akuraneee.reschools.core.mondayOf
import io.github.akuraneee.reschools.core.subjectTitle
import io.github.akuraneee.reschools.ui.components.ContentMaxWidth
import io.github.akuraneee.reschools.ui.components.EmptyNote
import io.github.akuraneee.reschools.ui.components.LessonNumber
import io.github.akuraneee.reschools.ui.components.MarksRow
import io.github.akuraneee.reschools.ui.components.SegmentedList
import io.github.akuraneee.reschools.ui.components.SegmentedNote
import io.github.akuraneee.reschools.ui.components.StatusBanner
import io.github.akuraneee.reschools.ui.components.TooltipIconButton
import io.github.akuraneee.reschools.ui.components.contentPaddingOf
import io.github.akuraneee.reschools.ui.components.mergedRow
import io.github.akuraneee.reschools.ui.components.rememberNowInfo
import io.github.akuraneee.reschools.ui.components.segmentedColors
import io.github.akuraneee.reschools.ui.components.rememberLinkified
import java.time.LocalDate
import kotlinx.coroutines.launch

// Неделя — страница пейджера; страница в середине — текущая неделя. Хватает на два года в обе стороны.
private const val CENTER_PAGE = 104
private const val PAGE_COUNT = CENTER_PAGE * 2 + 1

/** «Дневник»: недели листаются свайпом или стрелками, текущая неделя открывается на сегодняшнем дне. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class) // Scaffold-подпись и ExtendedFloatingActionButton с анимацией — экспериментальные в 1.5.0-beta
@Composable
fun DiaryScreen(
    state: UiState,
    contentInsets: WindowInsets,
    onOpenWeek: (LocalDate, Boolean) -> Unit,
    onLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentMonday = state.today.mondayOf()
    val pagerState = rememberPagerState(initialPage = CENTER_PAGE, pageCount = { PAGE_COUNT })
    val scope = rememberCoroutineScope()
    val motion = MaterialTheme.motionScheme
    fun mondayOf(page: Int): LocalDate = currentMonday.plusWeeks((page - CENTER_PAGE).toLong())

    val shownMonday = mondayOf(pagerState.currentPage)
    // Остановились на неделе — подгружаем её (из кэша, если уже грузили в этом заходе)
    LaunchedEffect(pagerState.settledPage, currentMonday) {
        onOpenWeek(mondayOf(pagerState.settledPage), false)
    }

    fun goTo(page: Int) {
        scope.launch { pagerState.animateScrollToPage(page, animationSpec = motion.defaultSpatialSpec()) }
    }

    val relative = when (shownMonday) {
        currentMonday -> stringResource(R.string.diary_this_week)
        currentMonday.plusWeeks(1) -> stringResource(R.string.diary_next_week_label)
        currentMonday.minusWeeks(1) -> stringResource(R.string.diary_prev_week_label)
        else -> null
    }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = contentInsets,
        topBar = {
            TopAppBar(
                title = { Text(Ru.weekRange(shownMonday), maxLines = 1) },
                subtitle = { relative?.let { Text(it) } },
                actions = {
                    TooltipIconButton(
                        R.drawable.ic_chevron_left_24, stringResource(R.string.diary_prev_week),
                        onClick = { goTo(pagerState.currentPage - 1) }, enabled = pagerState.currentPage > 0,
                    )
                    TooltipIconButton(
                        R.drawable.ic_chevron_right_24, stringResource(R.string.diary_next_week),
                        onClick = { goTo(pagerState.currentPage + 1) }, enabled = pagerState.currentPage < PAGE_COUNT - 1,
                    )
                },
                scrollBehavior = scrollBehavior,
                windowInsets = contentInsets,
            )
        },
        floatingActionButton = {
            // «К сегодняшней неделе» — единственная кнопка-действие экрана, показываем только вдали от неё
            ExtendedFloatingActionButton(
                onClick = { goTo(CENTER_PAGE) },
                icon = { Icon(painterResource(R.drawable.ic_today_24), contentDescription = null) },
                text = { Text(stringResource(R.string.diary_to_today)) },
                modifier = Modifier.animateFloatingActionButton(
                    visible = pagerState.currentPage != CENTER_PAGE,
                    alignment = Alignment.BottomEnd,
                ),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            Box(Modifier.padding(horizontal = 16.dp).widthIn(max = ContentMaxWidth).align(Alignment.CenterHorizontally)) {
                StatusBanner(state, onRetry = { onOpenWeek(shownMonday, true) }, onLogin = onLogin)
            }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                key = { it },
            ) { page ->
                val monday = mondayOf(page)
                WeekPage(
                    monday = monday,
                    state = state,
                    padding = padding,
                    isCurrentWeek = monday == currentMonday,
                    onReload = { onOpenWeek(monday, true) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WeekPage(monday: LocalDate, state: UiState, padding: PaddingValues, isCurrentWeek: Boolean, onReload: () -> Unit) {
    val week = state.weeks[monday]
    val loading = monday in state.weekLoading
    val pullState = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = loading && week != null,
        onRefresh = onReload,
        state = pullState,
        modifier = Modifier.fillMaxSize(),
        indicator = {
            PullToRefreshDefaults.LoadingIndicator(
                state = pullState,
                isRefreshing = loading && week != null,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        },
    ) {
        if (week == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (loading || state.loading) LoadingIndicator() else EmptyNote(stringResource(R.string.diary_no_data))
            }
        } else {
            WeekContent(monday, week, state.today, padding, isCurrentWeek, state.settings.bells)
        }
    }
}

@Composable
private fun WeekContent(monday: LocalDate, week: Week, today: LocalDate, padding: PaddingValues, isCurrentWeek: Boolean, bells: Bells) {
    val listState = rememberLazyListState()
    // запас снизу, чтобы кнопка «Сегодня» не закрывала последнюю строку
    val contentPadding = contentPaddingOf(padding, bottom = 96.dp)
    val days = remember(week) {
        val dates = (week.dates + week.lessons.map { it.date }).toSortedSet()
        dates.map { date -> date to week.lessons.filter { it.date == date }.sortedBy { it.num } }
            .filterNot { (date, lessons) -> lessons.isEmpty() && date.dayOfWeek.value == 6 }
    }
    // Открыли текущую неделю — сразу к сегодняшнему дню
    val todayIndex = days.indexOfFirst { it.first == today }
    LaunchedEffect(isCurrentWeek, todayIndex) {
        if (isCurrentWeek && todayIndex > 0) listState.scrollToItem(todayIndex)
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            state = listState,
            modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth(),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            when {
                week.none -> item { SegmentedNote(stringResource(R.string.diary_vacation)) }
                days.isEmpty() -> item { SegmentedNote(stringResource(R.string.diary_no_lessons_week)) }
                else -> items(days.size, key = { days[it].first.toString() }) { index ->
                    val (date, lessons) = days[index]
                    DaySection(date, lessons, isToday = date == today, bells = bells)
                }
            }
        }
    }
}

@Composable
private fun DaySection(date: LocalDate, lessons: List<Lesson>, isToday: Boolean, bells: Bells) {
    // Сегодня: идущий урок помечен, перемена — отдельной строкой между уроками
    val now = if (isToday) rememberNowInfo(lessons, date, bells) else null
    val rows = buildList<DayRow> {
        val st = now?.status
        val seconds = now?.seconds ?: 0
        for (l in lessons) {
            if (st is NowStatus.Break && l.num == st.next.num && (isEmpty() || last() !is DayRow.Pause)) {
                add(DayRow.Pause(st.next.start, minutesLeft(seconds, st.next.start)))
            }
            val left = if (st is NowStatus.InLesson && st.cur.num == l.num) minutesLeft(seconds, st.cur.end) else null
            add(DayRow.Item(l, left))
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DayHeader(date, lessons, isToday)
        if (lessons.isEmpty()) {
            SegmentedNote(stringResource(R.string.diary_no_lessons_day))
        } else {
            SegmentedList(rows.size) { index, shapes ->
                when (val r = rows[index]) {
                    is DayRow.Item -> LessonRow(r.lesson, shapes, r.left)
                    is DayRow.Pause -> PauseRow(r, shapes)
                }
            }
        }
    }
}

private sealed interface DayRow {
    /** [left] — урок идёт сейчас, до звонка столько минут. */
    data class Item(val lesson: Lesson, val left: Int?) : DayRow

    /** Перемена до урока, который начинается в [until]. */
    data class Pause(val until: Int, val left: Int) : DayRow
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PauseRow(r: DayRow.Pause, shapes: ListItemShapes) {
    SegmentedListItem(
        shapes = shapes,
        colors = segmentedColors(),
        modifier = Modifier.mergedRow(),
        supportingContent = { Text(stringResource(R.string.diary_break_until, Ru.duration(r.left), Ru.clock(r.until))) },
    ) {
        Text(stringResource(R.string.now_break), color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun DayHeader(date: LocalDate, lessons: List<Lesson>, isToday: Boolean) {
    val average = mean(lessons.flatMap { it.marks })
    val todayLabel = stringResource(R.string.diary_today_label)
    val content: @Composable () -> Unit = {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = if (isToday) 20.dp else 4.dp, vertical = if (isToday) 12.dp else 0.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = Ru.dayName(date),
                style = if (isToday) MaterialTheme.typography.titleLargeEmphasized else MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = if (isToday) "${Ru.dateLong(date)}, $todayLabel" else Ru.dateLong(date),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (average != null) {
                Text(
                    text = stringResource(R.string.diary_average, formatMean(average)),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                )
            }
        }
    }
    if (isToday) {
        // Сегодняшний день — главный акцент экрана
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            content = content,
        )
    } else {
        content()
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LessonRow(l: Lesson, shapes: ListItemShapes, left: Int? = null) {
    SegmentedListItem(
        shapes = shapes,
        colors = segmentedColors(),
        modifier = Modifier.mergedRow(),
        verticalAlignment = if (l.hw.isEmpty() && left == null) Alignment.CenterVertically else Alignment.Top,
        leadingContent = { LessonNumber(l.num.toString(), highlighted = left != null) },
        supportingContent = if (l.hw.isNotEmpty() || left != null) {
            {
                Column {
                    if (left != null) {
                        Text(
                            stringResource(R.string.diary_lesson_now, Ru.duration(left)),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (l.hw.isNotEmpty()) Text(rememberLinkified(l.hw), style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else null,
        trailingContent = if (l.marks.isNotEmpty() || l.absent) {
            { MarksRow(l, compact = true) }
        } else null,
    ) {
        Text(subjectTitle(l.subject))
    }
}
