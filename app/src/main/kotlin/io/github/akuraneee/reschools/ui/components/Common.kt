package io.github.akuraneee.reschools.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.akuraneee.reschools.R
import io.github.akuraneee.reschools.core.Lesson
import io.github.akuraneee.reschools.core.markLevel
import io.github.akuraneee.reschools.ui.theme.LocalMarkPalette

/** На широких экранах (планшет, развёрнутый складной телефон) содержимое не растягиваем дальше ~640dp. */
val ContentMaxWidth = 640.dp

/**
 * Отступы содержимого списка: то, что дал Scaffold (системные панели, app bar), плюс собственные поля.
 * Верхний отступ Scaffold применяем снаружи (к самому списку), поэтому здесь только боковые и нижний.
 */
@Composable
fun contentPaddingOf(scaffold: PaddingValues, horizontal: Dp = 16.dp, top: Dp = 8.dp, bottom: Dp = 24.dp): PaddingValues {
    val direction = LocalLayoutDirection.current
    return PaddingValues(
        start = horizontal + scaffold.calculateStartPadding(direction),
        end = horizontal + scaffold.calculateEndPadding(direction),
        top = top,
        bottom = bottom + scaffold.calculateBottomPadding(),
    )
}

/** Кнопка-иконка с подсказкой: у каждой кнопки без текста есть описание и tooltip. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class) // TooltipAnchorPosition и shapes у IconButton — экспериментальные в 1.5.0-beta
@Composable
fun TooltipIconButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
        tooltip = { PlainTooltip { Text(contentDescription) } },
        state = rememberTooltipState(),
    ) {
        IconButton(onClick = onClick, modifier = modifier, enabled = enabled, shapes = IconButtonDefaults.shapes()) {
            Icon(painterResource(icon), contentDescription = contentDescription)
        }
    }
}

/** Заголовок раздела: название и, мелко, пояснение. */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, subtitle: String? = null, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier = modifier.padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
        if (subtitle != null) {
            Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (trailing != null) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { trailing() }
        }
    }
}

/** Одна отметка: цвет по уровню белорусской 10-балльной шкалы. */
@Composable
fun MarkBadge(value: Int, modifier: Modifier = Modifier, compact: Boolean = false) {
    val colors = LocalMarkPalette.current[markLevel(value)]
    val description = stringResource(R.string.mark_cd, value)
    val side = if (compact) 32.dp else 40.dp
    Surface(
        modifier = modifier
            .defaultMinSize(minWidth = side, minHeight = side)
            .semantics { contentDescription = description },
        shape = if (compact) MaterialTheme.shapes.small else MaterialTheme.shapes.medium,
        color = colors.container,
        contentColor = colors.onContainer,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = value.toString(),
                style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLargeEmphasized,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
        }
    }
}

/** «н» — не был на уроке: нейтральный цвет, а не оценка. */
@Composable
fun AbsentBadge(modifier: Modifier = Modifier, compact: Boolean = false) {
    val description = stringResource(R.string.mark_absent_cd)
    val side = if (compact) 32.dp else 40.dp
    Surface(
        modifier = modifier
            .defaultMinSize(minWidth = side, minHeight = side)
            .semantics { contentDescription = description },
        shape = if (compact) MaterialTheme.shapes.small else MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.mark_absent),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
        }
    }
}

/** Отметки урока подряд (и «н», если не был). */
@Composable
fun MarksRow(lesson: Lesson, modifier: Modifier = Modifier, compact: Boolean = false) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        lesson.marks.forEach { MarkBadge(it, compact = compact) }
        if (lesson.absent) AbsentBadge(compact = compact)
    }
}

/** Номер урока (или «2–3» у сдвоенных) в круге. */
@Composable
fun LessonNumber(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.defaultMinSize(minWidth = 40.dp, minHeight = 40.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
    }
}

private val URL_REGEX = Regex("""\bhttps?://[^\s<]+[^\s<.,;:!?)»"']""")

/** Текст ДЗ со ссылками, на которые можно нажать. */
@Composable
fun rememberLinkified(text: String): AnnotatedString {
    val linkColor = MaterialTheme.colorScheme.primary
    return remember(text, linkColor) {
        buildAnnotatedString {
            var last = 0
            for (m in URL_REGEX.findAll(text)) {
                append(text.substring(last, m.range.first))
                withLink(
                    LinkAnnotation.Url(
                        m.value,
                        TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline, fontWeight = FontWeight.Medium)),
                    ),
                ) { append(m.value) }
                last = m.range.last + 1
            }
            append(text.substring(last))
        }
    }
}

/** Пустое состояние внутри раздела: короткая подпись. */
@Composable
fun EmptyNote(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
}
