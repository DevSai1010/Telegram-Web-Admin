package com.msma.tgpanel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.msma.tgpanel.BuildConfig
import com.msma.tgpanel.R
import com.msma.tgpanel.data.ApiResult
import kotlinx.coroutines.launch

@Composable
fun LoginScreen() {
    val app = appOf()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var server by rememberSaveable { mutableStateOf(app.prefs.baseUrl) }
    var user by rememberSaveable { mutableStateOf("") }
    var pass by rememberSaveable { mutableStateOf("") }
    var showPass by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun submit() {
        if (busy || pass.isEmpty()) return
        busy = true
        error = null
        scope.launch {
            app.prefs.baseUrl = server
            // 1) is this server one we can talk to?
            val ver = app.api.version()
            if (ver is ApiResult.Err) {
                error = ctx.getString(R.string.err_network); busy = false; return@launch
            }
            if (ver is ApiResult.Ok && ver.value.min_client > BuildConfig.VERSION_CODE) {
                error = ctx.getString(R.string.err_server_old); busy = false; return@launch
            }
            // 2) sign in
            when (val r = app.api.login(user.trim(), pass)) {
                is ApiResult.Ok -> {
                    if (r.value.must_change) error = ctx.getString(R.string.err_must_change)
                    else app.onSignedIn(r.value.token, r.value.username.ifEmpty { "owner" }, r.value.is_owner)
                }
                is ApiResult.Err -> error = when {
                    r.code == 0 -> ctx.getString(R.string.err_network)
                    r.code == 429 -> ctx.getString(R.string.err_rate, r.retryAfter)
                    r.message == "suspended" -> ctx.getString(R.string.err_suspended)
                    r.message.isNotEmpty() -> r.message
                    else -> ctx.getString(R.string.err_login)
                }
                else -> error = ctx.getString(R.string.err_login)
            }
            busy = false
        }
    }

    Scaffold { padding ->
        Box(Modifier.fillMaxSize().padding(padding).imePadding(), contentAlignment = Alignment.Center) {
            Surface(
                modifier = Modifier.padding(16.dp).fillMaxWidth().padding(horizontal = 0.dp),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Column(
                    Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(64.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.AutoMirrored.Rounded.Send, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(30.dp))
                        }
                    }
                    Text(stringResource(R.string.login_title), style = MaterialTheme.typography.headlineSmall)
                    Text(
                        stringResource(R.string.login_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    TextField(
                        value = server, onValueChange = { server = it }, singleLine = true,
                        label = { Text(stringResource(R.string.server_address)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextField(
                        value = user, onValueChange = { user = it }, singleLine = true,
                        label = { Text(stringResource(R.string.username)) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextField(
                        value = pass, onValueChange = { pass = it }, singleLine = true,
                        label = { Text(stringResource(R.string.password)) },
                        visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                        trailingIcon = {
                            IconButton(onClick = { showPass = !showPass }) {
                                Icon(
                                    if (showPass) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = stringResource(if (showPass) R.string.hide_password else R.string.show_password),
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    }
                    Button(onClick = { submit() }, enabled = !busy && pass.isNotEmpty(), modifier = Modifier.fillMaxWidth().height(48.dp)) {
                        if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        else Text(stringResource(R.string.sign_in))
                    }
                }
            }
        }
    }
}
