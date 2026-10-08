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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.s1nn3rv2.kartka.markdown.MdMath
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.material3.LocalContentColor
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.Image
import java.io.File
import androidx.compose.ui.unit.em
import androidx.compose.ui.text.style.BaselineShift
import com.s1nn3rv2.kartka.markdown.Inline
import com.s1nn3rv2.kartka.markdown.InlineStyle
import com.s1nn3rv2.kartka.markdown.MdBlock
import com.s1nn3rv2.kartka.markdown.InlineSpan
import com.s1nn3rv2.kartka.markdown.CodeHighlight
import com.s1nn3rv2.kartka.markdown.MdCallout
import com.s1nn3rv2.kartka.markdown.Token
import com.s1nn3rv2.kartka.markdown.MdFootnote
import com.s1nn3rv2.kartka.markdown.MdImage
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
    onLink: (InlineSpan) -> Unit,
    imageFile: (String) -> File?,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    state: LazyListState = rememberLazyListState(),
) {
    val visible = remember(blocks, folded) { visibleBlocks(blocks, folded) }
    val foldable = remember(blocks) { BooleanArray(blocks.size) { canFold(blocks, it) } }
    SelectionContainer(modifier) {
        LazyColumn(state = state, contentPadding = contentPadding) {
            itemsIndexed(visible, key = { _, i -> i }, contentType = { _, i -> blocks[i]::class }) { pos, i ->
                // Rows of one quote or callout are drawn as a single box.
                val joinPrev = joins(visible.getOrNull(pos - 1)?.let { blocks[it] }, blocks[i])
                val joinNext = joins(blocks[i], visible.getOrNull(pos + 1)?.let { blocks[it] })
                Row(Modifier.padding(start = 4.dp + IndentStep * blocks[i].indent, end = 20.dp)) {
                    FoldArrow(foldable[i], i in folded, top(blocks[i])) { onToggleFold(i) }
                    Block(blocks[i], joinPrev, joinNext, onToggleTask, onLink, imageFile)
                }
            }
        }
    }
}

private fun quoteOf(block: MdBlock?) = when (block) {
    is MdCallout -> block.type
    is MdParagraph -> block.quote
    else -> null
}

/** True when [next] continues the quote or callout that [prev] is part of; a callout header always starts a new box. */
private fun joins(prev: MdBlock?, next: MdBlock?) =
    next !is MdCallout && quoteOf(next) != null && quoteOf(prev) == quoteOf(next) && prev?.indent == next?.indent

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
private fun Block(
    block: MdBlock,
    joinPrev: Boolean,
    joinNext: Boolean,
    onToggleTask: (Task) -> Unit,
    onLink: (InlineSpan) -> Unit,
    imageFile: (String) -> File?,
) {
    val colors = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    val modifier = Modifier.padding(
        top = if (joinPrev) 0.dp else top(block),
        bottom = if (joinNext) 0.dp else if (block is MdListItem) 2.dp else 6.dp,
    )
    val quoteShape = RoundedCornerShape(
        topStart = if (joinPrev) 0.dp else 12.dp, topEnd = if (joinPrev) 0.dp else 12.dp,
        bottomStart = if (joinNext) 0.dp else 12.dp, bottomEnd = if (joinNext) 0.dp else 12.dp,
    )
    when (block) {
        is MdHeading -> MdText(
            block.text, onLink,
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

        is MdParagraph -> if (block.quote != null) {
            val (container, content) = calloutColors(block.quote)
            MdText(
                block.text, onLink,
                style = type.bodyLarge,
                color = content,
                modifier = modifier.fillMaxWidth().background(container, quoteShape)
                    .padding(start = 12.dp, end = 12.dp, top = if (joinPrev) 4.dp else 12.dp, bottom = 12.dp),
            )
        } else {
            MdText(block.text, onLink, style = type.bodyLarge, modifier = modifier)
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
            MdText(
                block.text, onLink,
                style = if (block.task?.checked == true) {
                    type.bodyLarge.copy(color = colors.onSurfaceVariant, textDecoration = TextDecoration.LineThrough)
                } else {
                    type.bodyLarge
                },
            )
        }

        is MdMath -> MathBlockView(block.latex, modifier)

        is MdImage -> LocalImage(remember(block.url) { imageFile(block.url) }, block.url, block.alt, modifier)

        is MdFootnote -> Row(modifier) {
            Text("${block.number}.", style = type.bodyMedium, color = colors.primary, modifier = Modifier.width(Gutter))
            MdText(block.text, onLink, style = type.bodyMedium, color = colors.onSurfaceVariant)
        }

        is MdCode -> Column(modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.surfaceContainerHigh).padding(12.dp)) {
            block.language?.let { Text(it, style = type.labelSmall, color = colors.onSurfaceVariant) }
            Text(
                highlighted(block),
                style = type.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                softWrap = false,
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            )
        }

        is MdTable -> Column(modifier.clip(RoundedCornerShape(8.dp)).border(1.dp, colors.outlineVariant, RoundedCornerShape(8.dp))) {
            // A column gets room for its longest word, plus a share of how much longer its cells run.
            val weights = block.columns.map { (word, cell) -> (word + (cell - word) * 0.5f).coerceAtLeast(3f) }
            TableRow(block.header, weights, header = true, onLink)
            block.rows.forEach { TableRow(it, weights, header = false, onLink) }
        }

        is MdCallout -> {
            val (container, content) = calloutColors(block.type)
            Row(
                modifier.fillMaxWidth().background(container, quoteShape).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(calloutIcon(block.type), contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(block.title, style = type.titleSmallEmphasized, color = content)
            }
        }

        is MdRule -> HorizontalDivider(modifier.padding(vertical = 8.dp), color = colors.outlineVariant)
    }
}

/** Code with syntax colors from the theme. */
@Composable
private fun highlighted(block: MdCode): AnnotatedString {
    val colors = MaterialTheme.colorScheme
    return remember(block, colors) {
        buildAnnotatedString {
            append(block.code)
            for (t in CodeHighlight.tokens(block.code, block.language)) {
                val style = when (t.token) {
                    Token.KEYWORD -> SpanStyle(color = colors.primary)
                    Token.STRING -> SpanStyle(color = colors.tertiary)
                    Token.NUMBER -> SpanStyle(color = colors.secondary)
                    Token.COMMENT -> SpanStyle(color = colors.onSurfaceVariant, fontStyle = FontStyle.Italic)
                }
                addStyle(style, t.start, t.end)
            }
        }
    }
}

/** Callout tints from the theme by kind: info, positive, cautionary or negative. "" is a plain quote. */
@Composable
private fun calloutColors(type: String): Pair<Color, Color> {
    val c = MaterialTheme.colorScheme
    return when (type) {
        "" -> c.surfaceContainer to c.onSurfaceVariant
        "tip", "hint", "important", "success", "check", "done", "example" -> c.tertiaryContainer to c.onTertiaryContainer
        "warning", "caution", "attention", "question", "help", "faq" -> c.secondaryContainer to c.onSecondaryContainer
        "danger", "error", "bug", "failure", "fail", "missing" -> c.errorContainer to c.onErrorContainer
        else -> c.primaryContainer to c.onPrimaryContainer
    }
}

private fun calloutIcon(type: String): ImageVector = when (type) {
    "tip", "hint", "important" -> Icons.Outlined.Lightbulb
    "success", "check", "done" -> Icons.Outlined.CheckCircle
    "warning", "caution", "attention" -> Icons.Outlined.Warning
    "question", "help", "faq" -> Icons.AutoMirrored.Outlined.HelpOutline
    "danger", "error", "bug", "failure", "fail", "missing" -> Icons.Outlined.ErrorOutline
    else -> Icons.Outlined.Info
}

@Composable
private fun TableRow(cells: List<Inline>, weights: List<Float>, header: Boolean, onLink: (InlineSpan) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.height(IntrinsicSize.Min).then(if (header) Modifier.background(colors.secondaryContainer) else Modifier)) {
        weights.forEachIndexed { i, w ->
            Box(Modifier.weight(w).fillMaxHeight().border(0.5.dp, colors.outlineVariant).padding(8.dp)) {
                MdText(
                    cells.getOrNull(i) ?: Inline(""), onLink,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (header) FontWeight.Bold else null,
                )
            }
        }
    }
}

/** Rich text of one block, with inline formulas drawn in place (sized to the text). */
@Composable
private fun MdText(
    inline: Inline,
    onLink: (InlineSpan) -> Unit,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
) {
    val maths = inline.spans.filter { it.style == InlineStyle.MATH }
    if (maths.isEmpty()) {
        return Text(inline.annotated(onLink, emptyMap()), modifier, color = color, fontWeight = fontWeight, style = style)
    }
    val density = LocalDensity.current
    val ink = color.takeOrElse { style.color.takeOrElse { LocalContentColor.current } }.toArgb()
    val px = with(density) { style.fontSize.toPx() }
    val images = remember(maths, px, ink) {
        maths.mapNotNull { s -> MathRender.render(s.url.orEmpty(), px, ink)?.let { s.start to it } }.toMap()
    }
    val content = images.entries.associate { (start, image) ->
        "math$start" to InlineTextContent(
            with(density) { Placeholder(image.width.toSp(), image.height.toSp(), PlaceholderVerticalAlign.TextCenter) },
        ) { Image(image, contentDescription = inline.text.substring(start, inline.spans.first { it.start == start }.end)) }
    }
    Text(inline.annotated(onLink, images), modifier, color = color, fontWeight = fontWeight, style = style, inlineContent = content)
}

/**
 * Wiki links, tags and footnotes are tappable and reported through [onLink]; web links open in the browser. Inline
 * math whose start is in [math] becomes an inline content slot ("math<start>"); other math shows as code.
 */
@Composable
private fun Inline.annotated(onLink: (InlineSpan) -> Unit, math: Map<Int, ImageBitmap>): AnnotatedString {
    val colors = MaterialTheme.colorScheme
    val currentOnLink by rememberUpdatedState(onLink)
    return remember(this, colors, math.keys) {
        buildAnnotatedString {
            // Formulas keep their source as alternate text, so offsets of the other styles stay valid.
            var pos = 0
            for (s in spans) {
                if (s.style != InlineStyle.MATH || s.start !in math) continue
                append(text, pos, s.start)
                appendInlineContent("math${s.start}", text.substring(s.start, s.end))
                pos = s.end
            }
            append(text, pos, text.length)
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
                InlineStyle.WIKILINK -> addLink(
                    LinkAnnotation.Clickable(s.url.orEmpty(), TextLinkStyles(SpanStyle(color = colors.primary, fontWeight = FontWeight.Medium))) { currentOnLink(s) },
                    s.start, s.end,
                )
                InlineStyle.TAG -> addLink(
                    LinkAnnotation.Clickable(s.url.orEmpty(), TextLinkStyles(SpanStyle(color = colors.tertiary))) { currentOnLink(s) },
                    s.start, s.end,
                )
                InlineStyle.FOOTNOTE -> addLink(
                    LinkAnnotation.Clickable(
                        s.url.orEmpty(),
                        TextLinkStyles(SpanStyle(color = colors.primary, fontSize = 0.75.em, baselineShift = BaselineShift.Superscript)),
                    ) { currentOnLink(s) },
                    s.start, s.end,
                )
                InlineStyle.MATH -> if (s.start !in math) {
                    addStyle(SpanStyle(fontFamily = FontFamily.Monospace, color = colors.error), s.start, s.end)
                }
                InlineStyle.HIGHLIGHT -> addStyle(SpanStyle(background = colors.tertiaryContainer, color = colors.onTertiaryContainer), s.start, s.end)
            }
        }
    }
}
