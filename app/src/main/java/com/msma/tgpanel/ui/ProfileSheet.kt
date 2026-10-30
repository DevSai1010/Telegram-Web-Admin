@file:OptIn(ExperimentalMaterial3Api::class)

package com.msma.tgpanel.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.msma.tgpanel.R
import com.msma.tgpanel.data.ApiResult
import com.msma.tgpanel.data.ChatMediaDto
import com.msma.tgpanel.data.MediaItemDto
import com.msma.tgpanel.data.ProfileDto
import kotlinx.coroutines.launch

/**
 * #profileModal — what you get by tapping the chat header: photo, id / name / username / bio, the shared-media tabs
 * (photos · videos · voice · files) and the four action buttons (pin · mute · AI panel · delete). On the web it is a bottom sheet on phones too.
 */
@Composable
fun ProfileSheet(chatId: String, onDismiss: () -> Unit, onChatDeleted: () -> Unit) {
    val app = appOf()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    val chat by app.repo.chat(chatId).collectAsStateWithLifecycle(initialValue = null)
    var profile by remember { mutableStateOf<ProfileDto?>(null) }
    var profileFailed by remember { mutableStateOf(false) }
    var media by remember { mutableStateOf<ChatMediaDto?>(null) }
    var tab by remember { mutableIntStateOf(0) }
    var aiOn by remember { mutableStateOf<Boolean?>(null) }
    var showAi by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var viewPhoto by remember { mutableStateOf<String?>(null) }
    var viewVideo by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(chatId) {
        when (val r = app.api.profile(chatId)) {
            is ApiResult.Ok -> if (r.value.error.isNotEmpty()) profileFailed = true else profile = r.value
            else -> profileFailed = true
        }
    }
    LaunchedEffect(chatId) { (app.api.chatMedia(chatId) as? ApiResult.Ok)?.let { media = it.value } }
    LaunchedEffect(chatId) { (app.api.chatSettings(chatId) as? ApiResult.Ok)?.let { aiOn = it.value.ai_mode } }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = cs.surfaceContainerHigh) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            // .modal-header: title with an icon, close button at the end
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Badge, null, tint = cs.primary)
                Text(stringResource(R.string.profile), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).padding(start = 8.dp))
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, stringResource(R.string.close), tint = cs.onSurfaceVariant) }
            }

            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
                Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(134.dp).border(3.dp, cs.primary, CircleShape).padding(3.dp)) {
                        Avatar(chatId, listOfNotNull(profile?.first_name, profile?.last_name).joinToString(" ").ifBlank { chat?.title.orEmpty() }, 128.dp)
                    }
                }

                val p = profile
                when {
                    p != null -> {
                        val name = listOf(p.first_name, p.last_name).filter { it.isNotEmpty() }.joinToString(" ")
                        Field(stringResource(R.string.pf_id), p.id.ifEmpty { chatId }, mono = true)
                        Field(stringResource(R.string.pf_name), name.ifEmpty { chat?.title.orEmpty() })
                        Field(stringResource(R.string.pf_username), if (p.username.isNotEmpty()) "@" + p.username.removePrefix("@") else stringResource(R.string.pf_none))
                        Field(stringResource(R.string.pf_bio), p.bio.ifEmpty { stringResource(R.string.pf_bio_none) })
                        if (p.limited) {
                            Surface(shape = RoundedCornerShape(12.dp), color = LocalExtColors.current.amberContainer, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                Text(p.limited_reason.ifEmpty { stringResource(R.string.pf_unavailable) }, color = LocalExtColors.current.onAmberContainer, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(12.dp))
                            }
                        }
                    }
                    profileFailed -> {
                        Field(stringResource(R.string.pf_id), chatId, mono = true)
                        Field(stringResource(R.string.pf_name), chat?.title.orEmpty())
                        Text(stringResource(R.string.pf_unavailable), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                    }
                    else -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp) }
                }

                // .pm-tabs — primary tabs with a 3 dp indicator
                Row(Modifier.fillMaxWidth().padding(top = 16.dp)) {
                    listOf(R.string.tab_photo, R.string.tab_video, R.string.tab_voice, R.string.tab_file).forEachIndexed { i, res ->
                        val on = tab == i
                        Column(Modifier.weight(1f).clickable { tab = i }, horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                stringResource(res), style = MaterialTheme.typography.labelLarge, color = if (on) cs.primary else cs.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 14.dp),
                            )
                            Box(Modifier.fillMaxWidth(0.6f).height(3.dp).clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)).background(if (on) cs.primary else Color.Transparent))
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(cs.outlineVariant))

                val m = media
                val items: List<MediaItemDto> = when (tab) { 0 -> m?.photo; 1 -> m?.video; 2 -> m?.voice; else -> m?.document }.orEmpty()
                when {
                    m == null -> Text(stringResource(R.string.pm_loading), color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(24.dp))
                    items.isEmpty() -> Text(stringResource(R.string.pm_empty), color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(24.dp))
                    tab <= 1 -> Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items.take(60).chunked(3).forEach { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                row.forEach { it ->
                                    MediaTile(it, isPhoto = tab == 0, modifier = Modifier.weight(1f)) { if (tab == 0) viewPhoto = it.file_id else viewVideo = it.file_id }
                                }
                                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                    else -> Column(Modifier.padding(vertical = 8.dp)) {
                        items.take(60).forEach { it ->
                            if (tab == 2) {
                                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.weight(1f)) { VoiceBar(it.file_id) }
                                    Text(it.time, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp))
                                }
                            } else {
                                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.InsertDriveFile, null, tint = cs.primary)
                                    Text(stringResource(R.string.media_doc), modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                                    Text(it.time, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // .profile-actions — four tonal buttons, equal width
            val c = chat
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton(
                    Icons.Rounded.PushPin, stringResource(if (c?.pinned == true) R.string.pa_unpin else R.string.pa_pin), Modifier.weight(1f),
                ) { scope.launch { app.repo.pinChat(chatId, c?.pinned != true) } }
                ActionButton(
                    if (c?.muted == true) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsOff,
                    stringResource(if (c?.muted == true) R.string.pa_unmute else R.string.pa_mute), Modifier.weight(1f),
                ) { scope.launch { app.repo.muteChat(chatId, c?.muted != true) } }
                ActionButton(
                    Icons.Rounded.SmartToy, stringResource(R.string.pa_ai), Modifier.weight(1f),
                    sub = aiOn?.let { stringResource(if (it) R.string.ai_on else R.string.ai_off) },
                ) { showAi = true }
                ActionButton(Icons.Rounded.Delete, stringResource(R.string.pa_delete), Modifier.weight(1f), danger = true) { confirmDelete = true }
            }
        }
    }

    if (showAi) {
        AiChatDialog(chatId, onDone = { on -> aiOn = on; showAi = false }, onDismiss = { showAi = false })
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            text = { Text(stringResource(R.string.delete_chat_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch { if (app.repo.deleteChat(chatId) is ApiResult.Ok) onChatDeleted() }
                }) { Text(stringResource(R.string.pa_delete), color = cs.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    viewPhoto?.let { fid ->
        Dialog(onDismissRequest = { viewPhoto = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(Color.Black).clickable { viewPhoto = null }, contentAlignment = Alignment.Center) {
                AsyncImage(
                    model = ImageRequest.Builder(ctx).data(app.api.mediaUrl(fid)).addHeader("Authorization", "Bearer " + app.prefs.token).build(),
                    contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
    viewVideo?.let { fid -> VideoDialog(fid, loop = false) { viewVideo = null } }
}

@Composable
private fun Field(label: String, value: String, mono: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text("$label: ", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
        Text(
            value, style = MaterialTheme.typography.bodyLarge.copy(fontFamily = if (mono) FontFamily.Monospace else null),
            color = if (mono) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun MediaTile(item: MediaItemDto, isPhoto: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val app = appOf()
    val ctx = LocalContext.current
    Box(modifier.aspectRatio(1f).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        if (isPhoto) {
            AsyncImage(
                model = ImageRequest.Builder(ctx).data(app.api.mediaUrl(item.file_id)).addHeader("Authorization", "Bearer " + app.prefs.token).crossfade(true).build(),
                contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(Icons.Rounded.PlayCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
        }
    }
}

/** .pa-btn — icon over label, radius 16, secondaryContainer (errorContainer when dangerous). */
@Composable
private fun ActionButton(icon: ImageVector, label: String, modifier: Modifier, danger: Boolean = false, sub: String? = null, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        onClick = onClick, shape = RoundedCornerShape(16.dp), modifier = modifier,
        color = if (danger) cs.errorContainer else cs.secondaryContainer, contentColor = if (danger) cs.onErrorContainer else cs.onSecondaryContainer,
    ) {
        Column(Modifier.padding(horizontal = 4.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, null, Modifier.size(24.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 2)
            if (sub != null) Text(sub, style = MaterialTheme.typography.labelSmall, color = if (sub == stringResource(R.string.ai_on)) LocalExtColors.current.green else cs.error)
        }
    }
}

/** #chatSP — per-chat AI switch and a custom prompt. */
@Composable
private fun AiChatDialog(chatId: String, onDone: (Boolean) -> Unit, onDismiss: () -> Unit) {
    val app = appOf()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var on by remember { mutableStateOf(true) }
    var prompt by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(chatId) {
        (app.api.chatSettings(chatId) as? ApiResult.Ok)?.let { on = it.value.ai_mode; prompt = it.value.system_prompt }
        loaded = true
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.SmartToy, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(stringResource(R.string.cs_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.cs_toggle), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = on, onCheckedChange = { on = it }, enabled = loaded)
                }
                Text(stringResource(R.string.cs_prompt_label), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextField(prompt, { prompt = it }, placeholder = { Text(stringResource(R.string.cs_prompt_hint)) }, minLines = 4, enabled = loaded, modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(enabled = loaded && !busy, onClick = {
                busy = true; error = null
                scope.launch {
                    when (val r = app.api.saveChatSettings(chatId, on, prompt.trim())) {
                        is ApiResult.Ok -> onDone(on)
                        is ApiResult.Err -> error = if (r.code == 0) ctx.getString(R.string.err_network) else r.message.ifEmpty { ctx.getString(R.string.err_generic) }
                        else -> {}
                    }
                    busy = false
                }
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
