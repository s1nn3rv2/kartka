package com.s1nn3rv2.kartka.ui.note

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.s1nn3rv2.kartka.markdown.Inline
import com.s1nn3rv2.kartka.markdown.InlineStyle
import com.s1nn3rv2.kartka.markdown.MdBlock
import com.s1nn3rv2.kartka.markdown.MdCode
import com.s1nn3rv2.kartka.markdown.MdHeading
import com.s1nn3rv2.kartka.markdown.MdListItem
import com.s1nn3rv2.kartka.markdown.MdParagraph
import com.s1nn3rv2.kartka.markdown.MdRule
import com.s1nn3rv2.kartka.markdown.MdTable
import com.s1nn3rv2.kartka.markdown.Task
import com.s1nn3rv2.kartka.markdown.canFold
import com.s1nn3rv2.kartka.markdown.visibleBlocks

private val Gutter = 24.dp
private val IndentStep = 20.dp

/**
 * View mode: the note as a lazy list of rendered blocks, so only what is on screen gets built. Headings and list
 * items with children get an arrow in the left gutter that folds them.
 */
@Composable
fun MarkdownView(
    blocks: List<MdBlock>,
    folded: Set<Int>,
    onToggleFold: (Int) -> Unit,
    onToggleTask: (Task) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    state: LazyListState = rememberLazyListState(),
) {
    val visible = remember(blocks, folded) { visibleBlocks(blocks, folded) }
    val foldable = remember(blocks) { BooleanArray(blocks.size) { canFold(blocks, it) } }
    SelectionContainer(modifier) {
        LazyColumn(state = state, contentPadding = contentPadding) {
            items(visible, key = { it }) { i ->
                Row(Modifier.padding(start = 4.dp + IndentStep * blocks[i].indent, end = 20.dp)) {
                    FoldArrow(foldable[i], i in folded, top(blocks[i])) { onToggleFold(i) }
                    Block(blocks[i], onToggleTask)
                }
            }
        }
    }
}

/** Vertical space above a block; also offsets the fold arrow so it lines up with the block's first line. */
private fun top(block: MdBlock) = when (block) {
    is MdHeading -> if (block.level <= 2) 20.dp else 12.dp
    is MdListItem -> 2.dp
    else -> 6.dp
}

@Composable
private fun FoldArrow(foldable: Boolean, folded: Boolean, top: Dp, onClick: () -> Unit) {
    if (!foldable) return Spacer(Modifier.width(Gutter))
    Box(
        Modifier.padding(top = top).size(Gutter).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (folded) Icons.AutoMirrored.Filled.KeyboardArrowRight else Icons.Filled.KeyboardArrowDown,
            contentDescription = if (folded) "Unfold" else "Fold",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun Block(block: MdBlock, onToggleTask: (Task) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    val modifier = Modifier.padding(top = top(block), bottom = if (block is MdListItem) 2.dp else 6.dp)
    when (block) {
        is MdHeading -> Text(
            block.text.annotated(),
            style = when (block.level) {
                1 -> type.headlineMedium
                2 -> type.headlineSmall
                3 -> type.titleLarge
                4 -> type.titleMedium
                else -> type.titleSmall
            },
            fontWeight = FontWeight.Bold,
            modifier = modifier,
        )

        is MdParagraph -> if (block.quote) {
            Text(
                block.text.annotated(),
                style = type.bodyLarge,
                color = colors.onSurfaceVariant,
                modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.surfaceContainer).padding(12.dp),
            )
        } else {
            Text(block.text.annotated(), style = type.bodyLarge, modifier = modifier)
        }

        is MdListItem -> Row(modifier) {
            // Checkboxes are wider than a bullet dot, so they get a wider slot to keep a gap before the text.
            Box(Modifier.width(if (block.task != null) 32.dp else Gutter).height(24.dp), contentAlignment = Alignment.Center) {
                when {
                    block.task != null -> CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                        Checkbox(block.task.checked, onCheckedChange = { onToggleTask(block.task) }, Modifier.size(20.dp))
                    }
                    block.marker == "•" -> Box(Modifier.size(6.dp).clip(CircleShape).background(colors.onSurfaceVariant))
                    else -> Text(block.marker, style = type.bodyLarge, color = colors.primary)
                }
            }
            Text(
                block.text.annotated(),
                style = if (block.task?.checked == true) {
                    type.bodyLarge.copy(color = colors.onSurfaceVariant, textDecoration = TextDecoration.LineThrough)
                } else {
                    type.bodyLarge
                },
            )
        }

        is MdCode -> Column(modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.surfaceContainerHigh).padding(12.dp)) {
            block.language?.let { Text(it, style = type.labelSmall, color = colors.onSurfaceVariant) }
            Text(
                block.code,
                style = type.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                softWrap = false,
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            )
        }

        is MdTable -> Column(modifier.clip(RoundedCornerShape(8.dp)).border(1.dp, colors.outlineVariant, RoundedCornerShape(8.dp))) {
            // A column gets room for its longest word, plus a share of how much longer its cells run.
            val weights = block.columns.map { (word, cell) -> (word + (cell - word) * 0.5f).coerceAtLeast(3f) }
            TableRow(block.header, weights, header = true)
            block.rows.forEach { TableRow(it, weights, header = false) }
        }

        is MdRule -> HorizontalDivider(modifier.padding(vertical = 8.dp), color = colors.outlineVariant)
    }
}

@Composable
private fun TableRow(cells: List<Inline>, weights: List<Float>, header: Boolean) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.height(IntrinsicSize.Min).then(if (header) Modifier.background(colors.secondaryContainer) else Modifier)) {
        weights.forEachIndexed { i, w ->
            Box(Modifier.weight(w).fillMaxHeight().border(0.5.dp, colors.outlineVariant).padding(8.dp)) {
                Text(
                    cells.getOrNull(i)?.annotated() ?: AnnotatedString(""),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (header) FontWeight.Bold else null,
                )
            }
        }
    }
}

@Composable
private fun Inline.annotated(): AnnotatedString {
    val colors = MaterialTheme.colorScheme
    return remember(this, colors) {
        buildAnnotatedString {
            append(text)
            for (s in spans) when (s.style) {
                InlineStyle.STRONG -> addStyle(SpanStyle(fontWeight = FontWeight.Bold), s.start, s.end)
                InlineStyle.EMPH -> addStyle(SpanStyle(fontStyle = FontStyle.Italic), s.start, s.end)
                InlineStyle.STRIKE -> addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), s.start, s.end)
                InlineStyle.CODE -> addStyle(
                    SpanStyle(fontFamily = FontFamily.Monospace, color = colors.tertiary, background = colors.surfaceContainerHighest),
                    s.start, s.end,
                )
                InlineStyle.LINK -> addLink(
                    LinkAnnotation.Url(s.url.orEmpty(), TextLinkStyles(SpanStyle(color = colors.primary, textDecoration = TextDecoration.Underline))),
                    s.start, s.end,
                )
            }
        }
    }
}
