@file:OptIn(ExperimentalMaterial3Api::class)

package com.msma.tgpanel.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.msma.tgpanel.BuildConfig
import com.msma.tgpanel.R

private fun notificationsOn(ctx: Context) = NotificationManagerCompat.from(ctx).areNotificationsEnabled()

private fun batteryExempt(ctx: Context): Boolean =
    (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(ctx.packageName)

@Composable
fun SettingsScreen(onBack: () -> Unit, onDevices: () -> Unit) {
    val app = appOf()
    val ctx = LocalContext.current
    var notifOk by remember { mutableStateOf(notificationsOn(ctx)) }
    var batteryOk by remember { mutableStateOf(batteryExempt(ctx)) }
    var confirmLogout by remember { mutableStateOf(false) }
    var keepOn by remember { mutableStateOf(app.prefs.keepConnected) }
    var pickLang by remember { mutableStateOf(false) }

    // the user may change these in system settings and come back — re-read on every resume
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) { notifOk = notificationsOn(ctx); batteryOk = batteryExempt(ctx) }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    fun openAppNotificationSettings() {
        try {
            ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
        } catch (e: Exception) { /* nothing sensible to fall back to */ }
    }

    val askNotif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notifOk = notificationsOn(ctx)
        if (!granted) openAppNotificationSettings()         // denied for good → send them to the system page instead
    }

    fun askBattery() {
        try {
            ctx.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).setData(Uri.parse("package:" + ctx.packageName)))
        } catch (e: ActivityNotFoundException) {
            try { ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) } catch (e2: Exception) { }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(app.prefs.username.ifEmpty { "owner" }, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 8.dp, bottom = 12.dp))

            SettingRow(Icons.Rounded.Devices, stringResource(R.string.devices), onClick = onDevices)

            // ---- notifications + battery: explained, never forced ----
            Text(
                stringResource(R.string.notif_section), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp, top = 20.dp, bottom = 8.dp),
            )
            PermissionCard(
                icon = Icons.Rounded.Notifications,
                title = stringResource(R.string.notif_perm_title),
                body = stringResource(R.string.notif_perm_body),
                done = notifOk,
                doneText = stringResource(R.string.notif_perm_done),
                buttonText = stringResource(R.string.notif_perm_btn),
                onClick = {
                    if (Build.VERSION.SDK_INT >= 33) askNotif.launch(Manifest.permission.POST_NOTIFICATIONS) else openAppNotificationSettings()
                },
            )
            Column(Modifier.padding(top = 12.dp)) {
                PermissionCard(
                    icon = Icons.Rounded.BatteryChargingFull,
                    title = stringResource(R.string.battery_title),
                    body = stringResource(R.string.battery_body),
                    done = batteryOk,
                    doneText = stringResource(R.string.battery_done),
                    buttonText = stringResource(R.string.battery_btn),
                    onClick = { askBattery() },
                )
            }
            // opt-in: a small permanent notification, in exchange for messages that keep arriving with the app closed
            ListItem(
                headlineContent = { Text(stringResource(R.string.keep_title)) },
                supportingContent = { Text(stringResource(R.string.keep_body)) },
                trailingContent = { Switch(checked = keepOn, onCheckedChange = { keepOn = it; app.setKeepConnected(it) }) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.padding(top = 8.dp),
            )

            Text(
                stringResource(R.string.about), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp, top = 20.dp, bottom = 8.dp),
            )
            SettingRow(
                Icons.Rounded.Language, stringResource(R.string.language),
                supporting = when (app.prefs.language) {
                    "fa" -> stringResource(R.string.language_fa)
                    "en" -> stringResource(R.string.language_en)
                    "ru" -> stringResource(R.string.language_ru)
                    else -> stringResource(R.string.language_system)
                },
                onClick = { pickLang = true },
            )
            SettingRow(Icons.Rounded.Info, stringResource(R.string.app_name), supporting = stringResource(R.string.version_fmt, BuildConfig.VERSION_NAME))
            SettingRow(
                Icons.AutoMirrored.Rounded.Logout, stringResource(R.string.logout), danger = true,
                onClick = { confirmLogout = true },
            )
        }
    }

    if (pickLang) {
        val options = listOf("" to R.string.language_system, "fa" to R.string.language_fa, "en" to R.string.language_en, "ru" to R.string.language_ru)
        AlertDialog(
            onDismissRequest = { pickLang = false },
            title = { Text(stringResource(R.string.language_pick)) },
            text = {
                Column {
                    options.forEach { (code, label) ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                pickLang = false
                                if (app.prefs.language != code) {
                                    app.prefs.language = code
                                    (ctx as? android.app.Activity)?.recreate()     // re-resolve strings + text direction
                                }
                            }.padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = app.prefs.language == code, onClick = null)
                            Text(stringResource(label), modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickLang = false }) { Text(stringResource(R.string.close)) } },
        )
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text(stringResource(R.string.logout)) },
            text = { Text(stringResource(R.string.logout_confirm)) },
            confirmButton = { TextButton(onClick = { confirmLogout = false; app.logout() }) { Text(stringResource(R.string.ok)) } },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun SettingRow(icon: ImageVector, title: String, supporting: String? = null, danger: Boolean = false, onClick: (() -> Unit)? = null) {
    val tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSecondaryContainer
    ListItem(
        headlineContent = { Text(title, color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface) },
        supportingContent = if (supporting != null) ({ Text(supporting) }) else null,
        leadingContent = {
            Surface(shape = RoundedCornerShape(50), color = if (danger) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(40.dp)) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, tint = if (danger) MaterialTheme.colorScheme.onErrorContainer else tint, modifier = Modifier.size(22.dp))
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.clickable(enabled = onClick != null) { onClick?.invoke() },
    )
}

@Composable
private fun PermissionCard(
    icon: ImageVector, title: String, body: String, done: Boolean, doneText: String, buttonText: String, onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (done) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (done) Icons.Rounded.CheckCircle else icon, null, tint = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSecondaryContainer)
                Text(
                    if (done) doneText else title, style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 12.dp),
                    color = if (done) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            if (!done) {
                Text(
                    body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
                )
                Button(onClick = onClick) { Text(buttonText) }
            }
        }
    }
}
