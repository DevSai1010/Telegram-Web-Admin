@file:OptIn(ExperimentalMaterial3Api::class)

package com.msma.tgpanel.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.Gif
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.StickyNote2
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.msma.tgpanel.R
import com.msma.tgpanel.data.ApiResult
import com.msma.tgpanel.data.ChatEntity
import com.msma.tgpanel.data.MessageEntity

enum class AttachKind { Photo, Video, Gif, File, Camera }

private enum class Panel { None, Emoji, Sticker }

/**
 * .comp-wrap / .comp-dock: one rounded container (radius 28, surfaceContainerHigh) holding
 * attach, emoji, sticker, the text field, and a mic / send button; the reply strip sits on top, and the
 * emoji / sticker panels open above it. While recording, the row becomes cancel · timer · send.
 */
@Composable
fun Composer(
    chat: ChatEntity?, draft: String, onDraft: (String) -> Unit,
    replyTo: MessageEntity?, onCancelReply: () -> Unit,
    senderMode: String, onSenderMode: (String) -> Unit,
    sending: Boolean, onSend: () -> Unit,
    recording: Boolean, recordMs: Long, onMic: () -> Unit, onCancelRecording: () -> Unit, onSendRecording: () -> Unit,
    onAttach: (AttachKind) -> Unit,
    onSticker: (fileId: String, kind: String, emoji: String) -> Unit,
) {
    var attachMenu by remember { mutableStateOf(false) }
    var panel by remember { mutableStateOf(Panel.None) }
    val cs = MaterialTheme.colorScheme

    Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
        // business chats can be answered "as me" or as the bot (the web's #senderSelector)
        if (chat != null && chat.bizId.isNotEmpty()) {
            Row(Modifier.padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = senderMode == "bot", onClick = { onSenderMode("bot") }, label = { Text(stringResource(R.string.sender_bot)) })
                FilterChip(selected = senderMode == "me", onClick = { onSenderMode("me") }, label = { Text(stringResource(R.string.sender_me)) })
            }
        }

        if (panel == Panel.Emoji) EmojiPanel { onDraft(draft + it) }
        if (panel == Panel.Sticker) StickerPanel { f, k, e -> panel = Panel.None; onSticker(f, k, e) }
        if (panel != Panel.None) androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))

        Surface(shape = RoundedCornerShape(28.dp), color = cs.surfaceContainerHigh) {
            Column {
                androidx.compose.animation.AnimatedVisibility(visible = replyTo != null, enter = fadeIn(), exit = fadeOut()) {
                    if (replyTo != null) {
                        // .rstrip — tonal strip with a 3 dp primary edge
                        Row(
                            Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 8.dp).clip(RoundedCornerShape(12.dp)).background(cs.surfaceContainer)
                                .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(width = 3.dp, height = 28.dp).background(cs.primary))
                            Icon(Icons.AutoMirrored.Rounded.Reply, null, tint = cs.primary, modifier = Modifier.padding(start = 8.dp).size(20.dp))
                            Text(
                                stringResource(R.string.replying_to_fmt, replyTo.text.ifEmpty { replyTo.sender }.take(60)),
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                            )
                            IconButton(onClick = onCancelReply, modifier = Modifier.size(36.dp)) { Icon(Icons.Rounded.Close, stringResource(R.string.close), Modifier.size(18.dp)) }
                        }
                    }
                }

                if (recording) {
                    Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onCancelRecording) { Icon(Icons.Rounded.Close, stringResource(R.string.cancel), tint = cs.error) }
                        Box(Modifier.size(10.dp).clip(CircleShape).background(cs.error))
                        Text(
                            stringResource(R.string.recording) + "  " + (recordMs / 60000) + ":" + ((recordMs / 1000) % 60).toString().padStart(2, '0'),
                            modifier = Modifier.weight(1f).padding(horizontal = 10.dp), style = MaterialTheme.typography.bodyLarge,
                        )
                        FilledIconButton(onClick = onSendRecording, modifier = Modifier.size(48.dp)) { Icon(Icons.AutoMirrored.Rounded.Send, stringResource(R.string.send)) }
                    }
                } else {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Box(Modifier.padding(start = 4.dp, bottom = 4.dp)) {
                            IconButton(onClick = { attachMenu = true }, modifier = Modifier.size(40.dp)) { Icon(Icons.Rounded.AttachFile, stringResource(R.string.attach), tint = cs.onSurfaceVariant) }
                            DropdownMenu(expanded = attachMenu, onDismissRequest = { attachMenu = false }, shape = RoundedCornerShape(16.dp), containerColor = cs.surfaceContainer) {
                                AttachItem(Icons.Rounded.Image, R.string.att_photo) { attachMenu = false; onAttach(AttachKind.Photo) }
                                AttachItem(Icons.Rounded.Videocam, R.string.att_video) { attachMenu = false; onAttach(AttachKind.Video) }
                                AttachItem(Icons.Rounded.Gif, R.string.att_gif) { attachMenu = false; onAttach(AttachKind.Gif) }
                                AttachItem(Icons.Rounded.InsertDriveFile, R.string.attach_file) { attachMenu = false; onAttach(AttachKind.File) }
                                AttachItem(Icons.Rounded.PhotoCamera, R.string.attach_camera) { attachMenu = false; onAttach(AttachKind.Camera) }
                            }
                        }
                        IconButton(onClick = { panel = if (panel == Panel.Emoji) Panel.None else Panel.Emoji }, modifier = Modifier.padding(bottom = 4.dp).size(40.dp)) {
                            Icon(Icons.Rounded.EmojiEmotions, stringResource(R.string.emoji), tint = if (panel == Panel.Emoji) cs.primary else cs.onSurfaceVariant)
                        }
                        IconButton(onClick = { panel = if (panel == Panel.Sticker) Panel.None else Panel.Sticker }, modifier = Modifier.padding(bottom = 4.dp).size(40.dp)) {
                            Icon(Icons.Rounded.StickyNote2, stringResource(R.string.sticker), tint = if (panel == Panel.Sticker) cs.primary else cs.onSurfaceVariant)
                        }
                        TextField(
                            value = draft, onValueChange = onDraft,
                            placeholder = { Text(stringResource(R.string.type_message)) },
                            maxLines = 5,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, disabledIndicatorColor = Color.Transparent,
                            ),
                            modifier = Modifier.weight(1f),
                        )
                        // send when there is text, microphone when there is not
                        Box(Modifier.padding(4.dp)) {
                            androidx.compose.animation.AnimatedVisibility(visible = draft.isNotBlank(), enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
                                FilledIconButton(onClick = onSend, enabled = !sending, modifier = Modifier.size(48.dp)) { Icon(Icons.AutoMirrored.Rounded.Send, stringResource(R.string.send)) }
                            }
                            androidx.compose.animation.AnimatedVisibility(visible = draft.isBlank(), enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
                                IconButton(onClick = onMic, enabled = !sending, modifier = Modifier.size(48.dp)) { Icon(Icons.Rounded.Mic, stringResource(R.string.record_voice), tint = cs.onSurfaceVariant) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: Int, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(label)) },
        leadingIcon = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) },
        onClick = onClick, modifier = Modifier.height(48.dp),
    )
}

/** .emoji-picker — category strip (52 dp) over an 8-column grid, in a 28 dp rounded surface. */
@Composable
private fun EmojiPanel(onPick: (String) -> Unit) {
    var cat by remember { mutableIntStateOf(0) }
    val cs = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(28.dp), color = cs.surfaceContainer, shadowElevation = 3.dp, modifier = Modifier.fillMaxWidth().height(320.dp)) {
        Column {
            Row(Modifier.fillMaxWidth().height(52.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                EMOJI_CATEGORIES.forEachIndexed { i, c ->
                    Box(
                        Modifier.padding(horizontal = 1.dp).clip(RoundedCornerShape(50))
                            .background(if (cat == i) cs.secondaryContainer else Color.Transparent).clickable { cat = i }.padding(horizontal = 10.dp, vertical = 6.dp),
                    ) { Text(c.icon, fontSize = 19.sp) }
                }
            }
            HorizontalDivider(color = cs.outlineVariant)
            LazyVerticalGrid(columns = GridCells.Fixed(8), modifier = Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp)) {
                items(EMOJI_CATEGORIES[cat].emojis) { e ->
                    Box(Modifier.padding(1.dp).clip(RoundedCornerShape(12.dp)).clickable { onPick(e) }.padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                        Text(e, fontSize = 22.sp)
                    }
                }
            }
        }
    }
}

private class PanelItem(val fileId: String, val emoji: String, val kind: String)

/** .sticker-picker — "stickers" (recent + the packs named in settings) and "GIF" (recent). */
@Composable
private fun StickerPanel(onSend: (fileId: String, kind: String, emoji: String) -> Unit) {
    val app = appOf()
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var tab by remember { mutableIntStateOf(0) }
    var items by remember { mutableStateOf<List<PanelItem>?>(null) }

    LaunchedEffect(tab) {
        items = null
        val out = ArrayList<PanelItem>()
        if (tab == 0) {
            (app.api.recentMedia("sticker") as? ApiResult.Ok)?.value?.forEach { out.add(PanelItem(it.file_id, it.emoji.ifEmpty { "🎭" }, "sticker")) }
            val packs = ((app.api.stickerPacks() as? ApiResult.Ok)?.value?.sticker_packs ?: "").split(',').map { it.trim() }.filter { it.isNotEmpty() }
            for (p in packs) (app.api.stickerSet(p) as? ApiResult.Ok)?.value?.stickers?.forEach { out.add(PanelItem(it.file_id, it.emoji.ifEmpty { "🎭" }, "sticker")) }
        } else {
            (app.api.recentMedia("animation") as? ApiResult.Ok)?.value?.forEach { out.add(PanelItem(it.file_id, "", "animation")) }
        }
        items = out
    }

    Surface(shape = RoundedCornerShape(28.dp), color = cs.surfaceContainer, shadowElevation = 3.dp, modifier = Modifier.fillMaxWidth().height(320.dp)) {
        Column {
            Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(R.string.sk_tab_sticker, R.string.sk_tab_gif).forEachIndexed { i, res ->
                    Box(
                        Modifier.clip(RoundedCornerShape(50)).background(if (tab == i) cs.secondaryContainer else Color.Transparent).clickable { tab = i }.padding(horizontal = 16.dp, vertical = 8.dp),
                    ) { Text(stringResource(res), style = MaterialTheme.typography.labelLarge, color = if (tab == i) cs.onSecondaryContainer else cs.onSurfaceVariant) }
                }
            }
            HorizontalDivider(color = cs.outlineVariant)
            val list = items
            when {
                list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp) }
                list.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(if (tab == 0) R.string.sk_empty_sticker else R.string.sk_empty_gif),
                        style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, textAlign = TextAlign.Center,
                    )
                }
                else -> LazyVerticalGrid(columns = GridCells.Fixed(4), modifier = Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(list) { it ->
                        Box(
                            Modifier.aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(cs.surfaceContainerHigh).clickable { onSend(it.fileId, it.kind, it.emoji) },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (it.kind == "sticker") {
                                Text(it.emoji, fontSize = 30.sp)                        // shown when the format (animated .tgs / .webm) can't be decoded
                                AsyncImage(
                                    model = ImageRequest.Builder(ctx).data(app.api.mediaUrl(it.fileId)).addHeader("Authorization", "Bearer " + app.prefs.token).crossfade(true).build(),
                                    contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize(),
                                )
                            } else {
                                Icon(Icons.Rounded.PlayCircle, null, tint = cs.primary, modifier = Modifier.size(32.dp))
                                Text("GIF", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

