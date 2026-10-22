package io.github.akuraneee.reschools.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics

/**
 * Строки «сегментами»: вместо разделителей — зазор и скругление по краям группы.
 * [item] получает номер строки и её форму.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class) // сегментированные списки — экспериментальные в 1.5.0-beta
@Composable
fun SegmentedList(count: Int, modifier: Modifier = Modifier, item: @Composable (index: Int, shapes: ListItemShapes) -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        repeat(count) { index -> item(index, ListItemDefaults.segmentedShapes(index = index, count = count)) }
    }
}

/** Одна строка-пояснение в сегментированном списке («Отметок пока нет»). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SegmentedNote(text: String, modifier: Modifier = Modifier) {
    SegmentedList(count = 1, modifier = modifier) { _, shapes ->
        SegmentedListItem(shapes = shapes, colors = segmentedColors()) {
            Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Цвета строк сегментированного списка. Стандартный контейнер (surfaceBright) в светлой теме совпадает с фоном
 * экрана, и сегментов не видно, поэтому берём surfaceContainerLow; выбранная строка остаётся secondaryContainer.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun segmentedColors(): ListItemColors =
    ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)

/** Строка урока читается TalkBack одной фразой. */
fun Modifier.mergedRow(): Modifier = semantics(mergeDescendants = true) {}
