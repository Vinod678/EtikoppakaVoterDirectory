package com.example.etikoppakavoterdirectory.ui.auth

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.etikoppakavoterdirectory.auth.AttemptResult
import com.example.etikoppakavoterdirectory.auth.AuthManager
import com.example.etikoppakavoterdirectory.auth.LockReason
import com.example.etikoppakavoterdirectory.ui.components.AppIcons
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Shown instead of the whole app until the password is entered. No voter data is composed behind it. */
const val LOGIN_HELP = "For any information regarding login, please contact: 938one0638eight"

@Composable
fun LockScreen(auth: AuthManager, reason: LockReason) {
    val activity = LocalContext.current as? Activity
    val scope = rememberCoroutineScope()
    // The typed password lives only in this composition (not saved to the instance state).
    var password by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var wait by remember { mutableLongStateOf(auth.lockoutSecondsLeft()) }

    // Back leaves the app; it can never reveal the screens behind the lock.
    BackHandler { activity?.finish() }

    LaunchedEffect(wait) {
        if (wait > 0) {
            delay(1000)
            wait = auth.lockoutSecondsLeft()
        }
    }

    fun submit() {
        if (busy || password.isEmpty() || wait > 0) return
        busy = true
        val chars = password.toCharArray()
        password = ""
        scope.launch {
            when (val r = auth.attempt(chars)) {
                AttemptResult.Success -> error = null
                is AttemptResult.Wrong -> error = "Incorrect password. Passwords are case-sensitive." +
                    if (r.attemptsLeft <= 2) " ${r.attemptsLeft} attempt${if (r.attemptsLeft == 1) "" else "s"} left before a short wait." else ""
                is AttemptResult.LockedOut -> { error = "Too many incorrect attempts."; wait = r.secondsLeft }
            }
            busy = false
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding(), contentAlignment = Alignment.Center) {
            Column(
                Modifier.widthIn(max = 420.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                Text("Etikoppaka Voter Directory", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                Text(
                    "Enter the password to continue",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                reason.message?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary, textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it.take(128); error = null },
                    label = { Text("Password") },
                    singleLine = true,
                    enabled = !busy && wait == 0L,
                    isError = error != null,
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    trailingIcon = {
                        IconButton(onClick = { visible = !visible }) {
                            Icon(
                                if (visible) AppIcons.VisibilityOff else AppIcons.Visibility,
                                contentDescription = if (visible) "Hide password" else "Show password",
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("passwordField"),
                )
                val message = when {
                    wait > 0 -> "Too many incorrect attempts. Try again in $wait s."
                    else -> error
                }
                if (message != null) {
                    Text(
                        message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                Button(
                    onClick = ::submit,
                    enabled = !busy && password.isNotEmpty() && wait == 0L,
                    modifier = Modifier.fillMaxWidth().height(50.dp).testTag("unlockButton"),
                ) {
                    if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Unlock", fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    LOGIN_HELP,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
