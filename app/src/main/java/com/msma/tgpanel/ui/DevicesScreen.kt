@file:OptIn(ExperimentalMaterial3Api::class)

package com.msma.tgpanel.ui

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.msma.tgpanel.R
import com.msma.tgpanel.data.ApiResult
import com.msma.tgpanel.data.SessionDto
import kotlinx.coroutines.launch

private fun platformIcon(p: String): ImageVector = when (p) {
    "web" -> Icons.Rounded.Language
    "pwa" -> Icons.Rounded.InstallMobile
    "android" -> Icons.Rounded.Android
    "windows" -> Icons.Rounded.DesktopWindows
    else -> Icons.Rounded.Devices
}

@Composable
fun DevicesScreen(onBack: () -> Unit) {
    val app = appOf()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    var sessions by remember { mutableStateOf<List<SessionDto>?>(null) }
    var renaming by remember { mutableStateOf<SessionDto?>(null) }
    var kicking by remember { mutableStateOf<SessionDto?>(null) }
    var nameDraft by remember { mutableStateOf("") }

    fun load() {
        scope.launch { val r = app.api.sessions(); if (r is ApiResult.Ok) sessions = r.value }
    }
    LaunchedEffect(Unit) { load() }

    fun platformLabel(s: SessionDto): String = ctx.getString(
        when (s.device.platform) {
            "web" -> R.string.platform_web
            "pwa" -> R.string.platform_pwa
            "android" -> R.string.platform_android
            "windows" -> R.string.platform_windows
            else -> R.string.platform_unknown
        }
    )

    fun ago(s: SessionDto): String {
        if (s.current) return ctx.getString(R.string.last_active_now)
        val ts = if (s.last_seen > 0) s.last_seen else s.created
        if (ts <= 0) return "—"
        val now = System.currentTimeMillis()
        if (now - ts < 10 * DateUtils.MINUTE_IN_MILLIS) return ctx.getString(R.string.last_active_recent)
        return DateUtils.getRelativeTimeSpanString(ts, now, DateUtils.MINUTE_IN_MILLIS).toString()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.devices)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.devices_hint), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            items(sessions.orEmpty(), key = { it.id }) { s ->
                val current = s.current
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (current) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape, modifier = Modifier.size(44.dp),
                            color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    platformIcon(s.device.platform), null, modifier = Modifier.size(24.dp),
                                    tint = if (current) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    s.device.name.ifEmpty { platformLabel(s) }, style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                                )
                                if (current) {
                                    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp)) {
                                        Text(
                                            stringResource(R.string.this_device), style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        )
                                    }
                                }
                            }
                            val signedIn = if (s.created > 0) {
                                ctx.getString(
                                    R.string.signed_in_fmt,
                                    DateUtils.formatDateTime(ctx, s.created, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH),
                                )
                            } else ""
                            Text(
                                listOf(platformLabel(s) + if (s.device.app.isNotEmpty()) " " + s.device.app else "", ctx.getString(R.string.last_active_fmt, ago(s)), signedIn)
                                    .filter { it.isNotEmpty() }.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (current) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { nameDraft = s.device.name; renaming = s }) { Icon(Icons.Rounded.Edit, stringResource(R.string.rename)) }
                        IconButton(onClick = { kicking = s }) {
                            Icon(Icons.AutoMirrored.Rounded.Logout, stringResource(R.string.kick), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }

    renaming?.let { s ->
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text(stringResource(R.string.rename_title)) },
            text = {
                TextField(
                    value = nameDraft, onValueChange = { if (it.length <= 60) nameDraft = it }, singleLine = true,
                    placeholder = { Text(stringResource(R.string.device_name_hint)) },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = nameDraft.trim()
                    renaming = null
                    if (name.isNotEmpty()) scope.launch {
                        when (val r = app.api.renameSession(s.id, name)) {
                            is ApiResult.Ok -> { snack.showSnackbar(ctx.getString(R.string.renamed)); load() }
                            is ApiResult.Err -> snack.showSnackbar(r.message.ifEmpty { ctx.getString(R.string.err_generic) })
                            else -> {}
                        }
                    }
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    kicking?.let { s ->
        val label = s.device.name.ifEmpty { platformLabel(s) }
        AlertDialog(
            onDismissRequest = { kicking = null },
            title = { Text(stringResource(R.string.kick_title)) },
            text = { Text(if (s.current) stringResource(R.string.logout_confirm) else stringResource(R.string.kick_confirm_fmt, label)) },
            confirmButton = {
                TextButton(onClick = {
                    kicking = null
                    if (s.current) { app.logout(); return@TextButton }
                    scope.launch {
                        when (val r = app.api.revokeSession(s.id)) {
                            is ApiResult.Ok -> { snack.showSnackbar(ctx.getString(R.string.kicked)); load() }
                            is ApiResult.Err -> snack.showSnackbar(r.message.ifEmpty { ctx.getString(R.string.err_generic) })
                            else -> {}
                        }
                    }
                }) { Text(stringResource(R.string.ok), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { kicking = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
