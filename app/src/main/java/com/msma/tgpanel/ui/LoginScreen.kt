package com.msma.tgpanel.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.msma.tgpanel.BuildConfig
import com.msma.tgpanel.R
import com.msma.tgpanel.data.ApiResult
import kotlinx.coroutines.launch

private enum class AuthMode { Login, Forgot, Force }

/**
 * Same card as the web panel: brand mark, one title, a hint line that changes per mode, centred filled fields,
 * a full-width primary button and a text link underneath. The server address (the web gets it from the URL) sits behind a small link.
 */
@Composable
fun LoginScreen() {
    val app = appOf()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var mode by rememberSaveable { mutableStateOf(AuthMode.Login) }
    var server by rememberSaveable { mutableStateOf(app.prefs.baseUrl) }
    var showServer by rememberSaveable { mutableStateOf(false) }
    var user by rememberSaveable { mutableStateOf("") }
    var pass by rememberSaveable { mutableStateOf("") }
    var forgotUser by rememberSaveable { mutableStateOf("") }
    var new1 by rememberSaveable { mutableStateOf("") }
    var new2 by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    var suspended by remember { mutableStateOf(false) }
    var tempToken by remember { mutableStateOf("") }       // session of a temporary-password sign-in, until a new password is set
    var tempUser by remember { mutableStateOf("") }

    fun go(m: AuthMode) { mode = m; error = null; info = null }

    fun login() {
        if (busy || pass.isEmpty()) return
        busy = true; error = null; suspended = false
        scope.launch {
            app.prefs.baseUrl = server
            val ver = app.api.version()
            if (ver is ApiResult.Err) { error = ctx.getString(R.string.err_network); busy = false; return@launch }
            if (ver is ApiResult.Ok && ver.value.min_client > BuildConfig.VERSION_CODE) { error = ctx.getString(R.string.err_server_old); busy = false; return@launch }
            when (val r = app.api.login(user.trim(), pass)) {
                is ApiResult.Ok -> {
                    if (r.value.must_change) {                  // signed in with a one-time recovery password
                        tempToken = r.value.token; tempUser = r.value.username
                        pass = ""; go(AuthMode.Force)
                    } else app.onSignedIn(r.value.token, r.value.username.ifEmpty { "owner" }, r.value.is_owner)
                }
                is ApiResult.Err -> when {
                    r.code == 0 -> error = ctx.getString(R.string.err_network)
                    r.code == 429 -> error = ctx.getString(R.string.err_rate, r.retryAfter)
                    r.message == "suspended" -> suspended = true
                    r.message.isNotEmpty() -> error = r.message
                    else -> error = ctx.getString(R.string.err_login)
                }
                else -> error = ctx.getString(R.string.err_login)
            }
            busy = false
        }
    }

    fun requestReset() {
        if (busy) return
        val u = forgotUser.trim()
        if (u.isEmpty()) { error = ctx.getString(R.string.username_required); return }
        busy = true; error = null; info = null
        scope.launch {
            app.prefs.baseUrl = server
            when (val r = app.api.requestReset(u)) {
                is ApiResult.Ok -> info = r.value.message.ifEmpty { ctx.getString(R.string.saved) }
                is ApiResult.Err -> error = if (r.code == 0) ctx.getString(R.string.err_network) else r.message.ifEmpty { ctx.getString(R.string.err_generic) }
                else -> {}
            }
            busy = false
        }
    }

    fun saveNewPassword() {
        if (busy) return
        if (new1.length < 6) { error = ctx.getString(R.string.pw_short); return }
        if (new1 != new2) { error = ctx.getString(R.string.pw_mismatch); return }
        busy = true; error = null
        scope.launch {
            when (val r = app.api.changePassword(tempToken, new1)) {
                is ApiResult.Ok -> app.onSignedIn(r.value.token.ifEmpty { tempToken }, tempUser.ifEmpty { "owner" }, false)
                is ApiResult.Err -> error = if (r.code == 0) ctx.getString(R.string.err_network) else r.message.ifEmpty { ctx.getString(R.string.err_generic) }
                else -> {}
            }
            busy = false
        }
    }

    Scaffold { padding ->
        Box(Modifier.fillMaxSize().padding(padding).imePadding(), contentAlignment = Alignment.Center) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // .auth-card: width min(360, screen - 32), padding 32 24 24, radius 28, surface-container
                Surface(
                    modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shadowElevation = 1.dp,
                ) {
                    Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 32.dp, bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(64.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.AutoMirrored.Rounded.Send, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(32.dp))
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            stringResource(R.string.login_title), style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(4.dp))

                        if (suspended) {
                            Surface(
                                shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.errorContainer,
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
                            ) {
                                Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.Block, null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(20.dp))
                                    Text(
                                        stringResource(R.string.err_suspended), color = MaterialTheme.colorScheme.onErrorContainer,
                                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 10.dp),
                                    )
                                }
                            }
                        }

                        AnimatedContent(
                            targetState = mode,
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            label = "authMode",
                        ) { m ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Hint(stringResource(when (m) { AuthMode.Login -> R.string.login_subtitle; AuthMode.Forgot -> R.string.forgot_hint; AuthMode.Force -> R.string.force_hint }))
                                when (m) {
                                    AuthMode.Login -> {
                                        CenteredField(user, { user = it }, stringResource(R.string.username), ImeAction.Next)
                                        Spacer(Modifier.height(8.dp))
                                        CenteredField(pass, { pass = it }, stringResource(R.string.password), ImeAction.Done, password = true, onDone = { login() })
                                        Spacer(Modifier.height(16.dp))
                                        PrimaryAction(stringResource(R.string.sign_in), busy, enabled = pass.isNotEmpty()) { login() }
                                        AuthLink(stringResource(R.string.forgot_link)) { go(AuthMode.Forgot) }
                                    }
                                    AuthMode.Forgot -> {
                                        CenteredField(forgotUser, { forgotUser = it }, stringResource(R.string.username), ImeAction.Done, onDone = { requestReset() })
                                        Spacer(Modifier.height(16.dp))
                                        PrimaryAction(stringResource(R.string.forgot_btn), busy) { requestReset() }
                                        AuthLink(stringResource(R.string.back_to_login)) { go(AuthMode.Login) }
                                    }
                                    AuthMode.Force -> {
                                        CenteredField(new1, { new1 = it }, stringResource(R.string.force_new1), ImeAction.Next, password = true)
                                        Spacer(Modifier.height(8.dp))
                                        CenteredField(new2, { new2 = it }, stringResource(R.string.force_new2), ImeAction.Done, password = true, onDone = { saveNewPassword() })
                                        Spacer(Modifier.height(16.dp))
                                        PrimaryAction(stringResource(R.string.force_btn), busy) { saveNewPassword() }
                                    }
                                }
                            }
                        }

                        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp), textAlign = TextAlign.Center) }
                        info?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp), textAlign = TextAlign.Center) }
                    }
                }

                // the web gets the server from its own address bar; here it is one tap away, collapsed by default
                if (mode == AuthMode.Login) {
                    Text(
                        stringResource(R.string.server_address) + ": " + server.removePrefix("https://").removePrefix("http://"),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 16.dp).clickable { showServer = !showServer }.padding(8.dp),
                    )
                    if (showServer) {
                        TextField(
                            value = server, onValueChange = { server = it }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
    )
}

/** .form-control.text-center — filled field, centred text, 48 dp tall. */
@Composable
private fun CenteredField(
    value: String, onChange: (String) -> Unit, placeholder: String, ime: ImeAction,
    password: Boolean = false, onDone: (() -> Unit)? = null,
) {
    TextField(
        value = value, onValueChange = onChange, singleLine = true,
        placeholder = { Text(placeholder, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
        textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.Center),
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else KeyboardType.Text, imeAction = ime),
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
        shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PrimaryAction(text: String, busy: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled && !busy, modifier = Modifier.fillMaxWidth().height(48.dp)) {
        if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary) else Text(text)
    }
}

/** .auth-link — a pill-shaped text link. */
@Composable
private fun AuthLink(text: String, onClick: () -> Unit) {
    Text(
        text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 6.dp),
    )
}
