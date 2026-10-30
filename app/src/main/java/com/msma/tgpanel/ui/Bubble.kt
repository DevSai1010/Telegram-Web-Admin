@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)

package com.msma.tgpanel.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Stars
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.msma.tgpanel.R
import com.msma.tgpanel.core.Side
import com.msma.tgpanel.core.sideOf
import com.msma.tgpanel.data.MessageEntity
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** Everything a message can do; the chat screen decides how each one is carried out. */
class BubbleActions(
    val onReplyQuote: () -> Unit,
    val onReply: () -> Unit,
    val onReact: (String) -> Unit,
    val onEdit: () -> Unit,
    val onForward: () -> Unit,
    val onPin: () -> Unit,
    val onDelete: () -> Unit,
    val onAddToPanel: () -> Unit,
)

private val OUT_ROLES = setOf("owner", "admin", "bot_auto", "model")

@Composable
fun MessageBubble(m: MessageEntity, contPrev: Boolean, showSender: Boolean, isGroup: Boolean, flash: Boolean, actions: BubbleActions) {
    val app = appOf()
    val ext = LocalExtColors.current
    val cs = MaterialTheme.colorScheme
    val side = sideOf(m.role)
    val (bg, fg) = when (m.role) {
        "owner" -> cs.primaryContainer to cs.onPrimaryContainer
        "admin", "model", "bot_auto" -> ext.greenContainer to ext.onGreenContainer
        else -> cs.surfaceContainerHigh to cs.onSurface
    }
    val accent = when (m.role) { "owner" -> cs.onPrimaryContainer; "admin", "model", "bot_auto" -> ext.green; else -> cs.primary }

    // flat corner on the side that faces the screen edge; top one only when the bubble continues a group, bottom one always (web rule)
    val big = 20.dp
    val small = 6.dp
    val shape = if (side == Side.IN) {
        RoundedCornerShape(topStart = if (contPrev) small else big, topEnd = big, bottomEnd = big, bottomStart = small)
    } else {
        RoundedCornerShape(topStart = big, topEnd = if (contPrev) small else big, bottomEnd = small, bottomStart = big)
    }

    val flashAlpha by animateFloatAsState(if (flash) 0.35f else 0f, tween(if (flash) 160 else 500), label = "flash")
    var menu by remember { mutableStateOf(false) }
    var allReactions by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    // ---- swipe to reply (web: drag past 60 px on a row) ----
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val swipe = remember { Animatable(0f) }
    val thresholdPx = with(density) { 60.dp.toPx() }
    val maxPx = with(density) { 90.dp.toPx() }

    Column(Modifier.fillMaxWidth().padding(top = if (contPrev) 0.dp else 6.dp)) {
        Row(
            Modifier.fillMaxWidth().pointerInput(m.id) {
                detectHorizontalDragGestures(
                    onDragEnd = { scope.launch { if (abs(swipe.value) > thresholdPx * 0.6f && m.msgId.isNotEmpty()) actions.onReply(); swipe.animateTo(0f, tween(180)) } },
                    onDragCancel = { scope.launch { swipe.animateTo(0f, tween(180)) } },
                ) { _, dx -> scope.launch { swipe.snapTo((swipe.value + dx * 0.6f).coerceIn(-maxPx, maxPx)) } }
            },
            horizontalArrangement = if (side == Side.OUT) Arrangement.End else Arrangement.Start,
        ) {
            Box(Modifier.offset { IntOffset(swipe.value.roundToInt(), 0) }) {
                Surface(
                    color = bg, contentColor = fg, shape = shape,
                    modifier = Modifier.widthIn(max = 300.dp).clip(shape)
                        .drawWithContent {
                            drawContent()
                            if (flashAlpha > 0.001f) drawOutline(shape.createOutline(size, layoutDirection, this), color = cs.primary, alpha = flashAlpha)
                        }
                        .combinedClickable(onClick = {}, onLongClick = { menu = true }),
                ) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                        val label = when {
                            m.role == "owner" -> stringResource(R.string.role_owner)
                            m.role == "admin" -> stringResource(R.string.role_admin)
                            m.role == "model" -> stringResource(R.string.role_model)
                            m.role == "bot_auto" -> stringResource(R.string.role_auto)
                            showSender && m.sender.isNotEmpty() -> m.sender
                            else -> ""
                        }
                        if (label.isNotEmpty() && !contPrev) Text(label, style = MaterialTheme.typography.labelMedium, color = accent)
                        if (m.forwardedFrom.isNotEmpty()) {
                            Text(stringResource(R.string.forwarded_from_fmt, m.forwardedFrom), style = MaterialTheme.typography.labelSmall, color = fg.copy(alpha = 0.75f))
                        }
                        if (m.replyPreview.isNotEmpty()) ReplyQuote(m.replyPreview, accent, actions.onReplyQuote)
                        MediaBlock(m)
                        if (m.text.isNotEmpty() && m.type != "sticker") {
                            Text(m.text, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 22.sp))
                        }
                        Text(m.time, style = MaterialTheme.typography.labelSmall, color = fg.copy(alpha = 0.65f), modifier = Modifier.align(Alignment.End).padding(top = 2.dp))
                    }
                }

                // ---- long-press menu: reaction row on top, then the actions (same list and order as the web panel) ----
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false; allReactions = false }, shape = RoundedCornerShape(16.dp), containerColor = cs.surfaceContainer) {
                    if (m.msgId.isNotEmpty()) {
                        val usage = remember(menu) { app.prefs.usageCounts() }
                        val quick = remember(menu) { (QUICK_REACTIONS + ALL_REACTIONS).distinct().sortedByDescending { usage[it] ?: 0 }.take(6) }
                        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            quick.forEach { e -> EmojiButton(e, selected = m.myReaction == e) { menu = false; allReactions = false; actions.onReact(e) } }
                            Icon(
                                if (allReactions) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown, null,
                                tint = cs.onSurfaceVariant,
                                modifier = Modifier.padding(start = 4.dp).size(28.dp).clip(RoundedCornerShape(50)).clickable { allReactions = !allReactions },
                            )
                        }
                        if (allReactions) {
                            FlowRow(Modifier.widthIn(max = 300.dp).padding(horizontal = 8.dp), maxItemsInEachRow = 8) {
                                ALL_REACTIONS.forEach { e -> EmojiButton(e, selected = m.myReaction == e, size = 34) { menu = false; allReactions = false; actions.onReact(e) } }
                            }
                        }
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    }
                    if (m.msgId.isNotEmpty()) MenuItem(Icons.AutoMirrored.Rounded.Reply, R.string.ctx_reply) { menu = false; actions.onReply() }
                    if (m.msgId.isNotEmpty() && m.role in OUT_ROLES && m.text.isNotEmpty() && m.type.isEmpty()) MenuItem(Icons.Rounded.Edit, R.string.ctx_edit) { menu = false; actions.onEdit() }
                    if (m.text.isNotEmpty()) MenuItem(Icons.Rounded.ContentCopy, R.string.ctx_copy) { menu = false; clipboard.setText(AnnotatedString(m.text)) }
                    if ((m.type == "sticker" || m.type == "animation") && m.fileId.isNotEmpty()) MenuItem(Icons.Rounded.Stars, R.string.ctx_add_panel) { menu = false; actions.onAddToPanel() }
                    if (m.msgId.isNotEmpty()) MenuItem(Icons.AutoMirrored.Rounded.ArrowForward, R.string.ctx_forward) { menu = false; actions.onForward() }
                    if (isGroup && m.msgId.isNotEmpty()) MenuItem(Icons.Rounded.PushPin, R.string.ctx_pin) { menu = false; actions.onPin() }
                    if (m.msgId.isNotEmpty()) MenuItem(Icons.Rounded.Delete, R.string.ctx_delete, danger = true) { menu = false; actions.onDelete() }
                }
            }
        }

        // ---- reaction chips under the bubble: the contact's and ours ----
        if (m.theirReaction.isNotEmpty() || m.myReaction.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = if (side == Side.OUT) Arrangement.End else Arrangement.Start,
            ) {
                if (m.theirReaction.isNotEmpty()) ReactionChip(m.theirReaction, stringResource(R.string.reaction_user), mine = false) { actions.onReact(m.theirReaction) }
                if (m.myReaction.isNotEmpty()) {
                    Box(Modifier.width(4.dp))
                    ReactionChip(m.myReaction, stringResource(R.string.sender_me), mine = true) { actions.onReact(m.myReaction) }
                }
            }
        }
    }
}

@Composable
private fun MenuItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: Int, danger: Boolean = false, onClick: () -> Unit) {
    val c = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    DropdownMenuItem(
        text = { Text(stringResource(label), color = c) },
        leadingIcon = { Icon(icon, null, tint = if (danger) c else MaterialTheme.colorScheme.onSurfaceVariant) },
        onClick = onClick,
        modifier = Modifier.heightIn(min = 48.dp),
    )
}

@Composable
private fun EmojiButton(e: String, selected: Boolean, size: Int = 40, onClick: () -> Unit) {
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape(12.dp))
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(e, fontSize = (size * 0.55f).sp) }
}

/** .rchip — height 28, radius 8; ours is primaryContainer. */
@Composable
private fun ReactionChip(emoji: String, who: String, mine: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.height(28.dp).clip(RoundedCornerShape(8.dp))
            .background(if (mine) cs.primaryContainer else cs.surfaceContainerHigh)
            .then(if (mine) Modifier else Modifier.border(1.dp, cs.outlineVariant, RoundedCornerShape(8.dp)))
            .clickable(onClick = onClick).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(emoji, fontSize = 14.sp)
        Text(who, style = MaterialTheme.typography.labelSmall, color = (if (mine) cs.onPrimaryContainer else cs.onSurface).copy(alpha = 0.7f), modifier = Modifier.padding(start = 4.dp))
    }
}

@Composable
private fun ReplyQuote(preview: String, accent: Color, onClick: () -> Unit) {
    Row(
        Modifier.padding(top = 2.dp, bottom = 6.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick)
            .background(accent.copy(alpha = 0.12f)).height(IntrinsicSize.Min),
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(accent))
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Rounded.Reply, null, Modifier.size(15.dp), tint = accent)
            Text(preview, modifier = Modifier.padding(start = 6.dp), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Photos and stickers render inline; voice / video / GIF use the players; documents show a labelled chip. */
@Composable
private fun MediaBlock(m: MessageEntity) {
    if (m.type.isEmpty() || m.fileId.isEmpty()) return
    val app = appOf()
    val ctx = LocalContext.current
    fun request() = ImageRequest.Builder(ctx).data(app.api.mediaUrl(m.fileId)).addHeader("Authorization", "Bearer " + app.prefs.token).crossfade(true).build()

    when (m.type) {
        "photo" -> AsyncImage(
            model = request(), contentDescription = stringResource(R.string.media_photo), contentScale = ContentScale.Fit,
            modifier = Modifier.padding(bottom = 4.dp).widthIn(max = 280.dp).heightIn(max = 340.dp).clip(RoundedCornerShape(12.dp)),
        )
        "sticker" -> Box(Modifier.size(128.dp), contentAlignment = Alignment.Center) {
            Text(m.text, fontSize = 52.sp)                       // the emoji shows if the sticker's format can't be decoded
            AsyncImage(model = request(), contentDescription = stringResource(R.string.media_sticker), contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().height(128.dp))
        }
        "voice" -> VoiceBar(m.fileId)
        "video" -> VideoTile(m.fileId, stringResource(R.string.media_video), loop = false)
        "animation" -> VideoTile(m.fileId, stringResource(R.string.media_gif), loop = true)
        else -> Row(
            Modifier.padding(vertical = 4.dp).clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.12f)).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.InsertDriveFile, null, Modifier.size(28.dp))
            Text(stringResource(R.string.media_doc), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 10.dp))
        }
    }
}
