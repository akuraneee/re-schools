package io.github.akuraneee.reschools.ui.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.akuraneee.reschools.R
import io.github.akuraneee.reschools.UiState
import io.github.akuraneee.reschools.core.Lesson
import io.github.akuraneee.reschools.core.Ru
import io.github.akuraneee.reschools.core.mondayOf
import io.github.akuraneee.reschools.core.subjectTitle
import io.github.akuraneee.reschools.ui.components.ContentMaxWidth
import io.github.akuraneee.reschools.ui.components.LessonNumber
import io.github.akuraneee.reschools.ui.components.SegmentedList
import io.github.akuraneee.reschools.ui.components.SegmentedNote
import io.github.akuraneee.reschools.ui.components.StatusBanner
import io.github.akuraneee.reschools.ui.components.TooltipIconButton
import io.github.akuraneee.reschools.ui.components.contentPaddingOf
import io.github.akuraneee.reschools.ui.components.mergedRow
import io.github.akuraneee.reschools.ui.components.segmentedColors
import java.time.LocalDate
import kotlinx.coroutines.launch

private const val CENTER_PAGE = 104
private const val PAGE_COUNT = CENTER_PAGE * 2 + 1

/**
 * «Расписание»: уроки недели без отметок и ДЗ. Берём их из дневника — расписания класса отдельным запросом
 * приложение не читает. Листается по неделям; недели вперёд подгружаются с сайта, как в дневнике.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ScheduleScreen(
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

    LaunchedEffect(pagerState.settledPage, currentMonday) { onOpenWeek(mondayOf(pagerState.settledPage), false) }
    fun goTo(page: Int) {
        scope.launch { pagerState.animateScrollToPage(page, animationSpec = motion.defaultSpatialSpec()) }
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = contentInsets,
        topBar = {
            TopAppBar(
                title = { Text(Ru.weekRange(shownMonday), maxLines = 1) },
                subtitle = { Text(stringResource(R.string.schedule_title)) },
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
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            Box(Modifier.padding(horizontal = 16.dp).widthIn(max = ContentMaxWidth).align(Alignment.CenterHorizontally)) {
                StatusBanner(state, onRetry = { onOpenWeek(shownMonday, true) }, onLogin = onLogin)
            }
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize(), key = { it }) { page ->
                SchedulePage(mondayOf(page), state, padding)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class) // LoadingIndicator — экспериментальный в 1.5.0-beta
@Composable
private fun SchedulePage(monday: LocalDate, state: UiState, padding: PaddingValues) {
    val week = state.weeks[monday]
    if (week == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (monday in state.weekLoading || state.loading) LoadingIndicator()
            else SegmentedNote(stringResource(R.string.diary_no_data), Modifier.padding(16.dp))
        }
        return
    }
    val days = remember(week) {
        week.lessons.groupBy { it.date }.toSortedMap().map { (date, ls) -> date to ls.sortedBy { it.num } }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth(),
            contentPadding = contentPaddingOf(padding),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            when {
                week.none -> item { SegmentedNote(stringResource(R.string.diary_vacation)) }
                days.isEmpty() -> item { SegmentedNote(stringResource(R.string.schedule_empty)) }
                else -> {
                    items(days, key = { it.first.toString() }) { (date, lessons) ->
                        DaySchedule(date, lessons, isToday = date == state.today)
                    }
                    item {
                        Text(
                            stringResource(R.string.schedule_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DaySchedule(date: LocalDate, lessons: List<Lesson>, isToday: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val title: @Composable () -> Unit = {
            Text(
                text = "${Ru.dayName(date)}, ${Ru.dateLong(date)}",
                style = if (isToday) MaterialTheme.typography.titleLargeEmphasized else MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .padding(horizontal = if (isToday) 20.dp else 4.dp, vertical = if (isToday) 12.dp else 0.dp)
                    .semantics { heading() },
            )
        }
        if (isToday) {
            // Сегодня — главный акцент экрана
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                content = title,
            )
        } else {
            title()
        }
        SegmentedList(lessons.size) { index, shapes -> ScheduleRow(lessons[index], shapes) }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ScheduleRow(l: Lesson, shapes: ListItemShapes) {
    SegmentedListItem(
        shapes = shapes,
        colors = segmentedColors(),
        modifier = Modifier.mergedRow(),
        leadingContent = { LessonNumber(l.num.toString()) },
    ) {
        Text(subjectTitle(l.subject))
    }
}
