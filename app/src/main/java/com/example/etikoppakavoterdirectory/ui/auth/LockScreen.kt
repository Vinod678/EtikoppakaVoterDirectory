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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role as SemanticsRole
import com.example.etikoppakavoterdirectory.auth.Role
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

/** Shown when Admin is chosen from 3 Dec 2026 while no separate Admin password is configured. */
const val ADMIN_UNAVAILABLE =
    "Admin sign-in is not available yet: a separate Admin password has to be set up first. Please contact the developer."

/**
 * Sign-in: pick Admin or Viewer (Viewer by default), then enter that role's password. Only the
 * chosen role's credential is checked, and a correct password opens exactly that role.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LockScreen(auth: AuthManager, reason: LockReason) {
    val activity = LocalContext.current as? Activity
    val scope = rememberCoroutineScope()
    // The chosen role survives rotation; the typed password lives only in this composition.
    var roleName by rememberSaveable { mutableStateOf(Role.VIEWER.name) }
    val role = Role.valueOf(roleName)
    var password by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var wait by remember { mutableLongStateOf(auth.lockoutSecondsLeft()) }
    val roleAvailable = auth.isRoleAvailable(role)

    // Back leaves the app; it can never reveal the screens behind the lock.
    BackHandler { activity?.finish() }

    LaunchedEffect(wait) {
        if (wait > 0) {
            delay(1000)
            wait = auth.lockoutSecondsLeft()
        }
    }

    fun selectRole(r: Role) {
        if (busy || r == role) return
        roleName = r.name
        // A password typed for the other role is never kept.
        password = ""
        visible = false
        error = null
    }

    fun submit() {
        if (busy || password.isEmpty() || wait > 0 || !roleAvailable) return
        busy = true
        val chars = password.toCharArray()
        password = ""
        scope.launch {
            when (val r = auth.attempt(chars, role)) {
                AttemptResult.Success -> error = null
                AttemptResult.RoleUnavailable -> error = ADMIN_UNAVAILABLE
                is AttemptResult.Wrong -> error = "Incorrect password. Passwords are case-sensitive." +
                    if (r.attemptsLeft <= 2) " ${r.attemptsLeft} attempt${if (r.attemptsLeft == 1) "" else "s"} left before a short wait." else ""
                is AttemptResult.LockedOut -> { error = "Too many incorrect attempts."; wait = r.secondsLeft }
            }
            busy = false
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
      Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(
                Modifier.widthIn(max = 420.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                Text("Etikoppaka Voter Directory", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                Text(
                    "Choose your access and enter the password",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                reason.message?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary, textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(4.dp))
                Row(
                    Modifier.fillMaxWidth().height(IntrinsicSize.Min).selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    RoleCard(AppIcons.Shield, "Admin", "Full Access", role == Role.ADMIN, "roleAdmin", Modifier.weight(1f)) { selectRole(Role.ADMIN) }
                    RoleCard(AppIcons.Visibility, "Viewer", "Read-Only Access", role == Role.VIEWER, "roleViewer", Modifier.weight(1f)) { selectRole(Role.VIEWER) }
                }
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it.take(128); error = null },
                    label = { Text(if (role == Role.ADMIN) "Admin password" else "Viewer password") },
                    singleLine = true,
                    enabled = !busy && wait == 0L && roleAvailable,
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
                    !roleAvailable -> ADMIN_UNAVAILABLE
                    wait > 0 -> "Too many incorrect attempts. Try again in $wait s."
                    else -> error
                }
                if (message != null) {
                    Text(
                        message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth().testTag("loginMessage").semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                Button(
                    onClick = ::submit,
                    enabled = !busy && password.isNotEmpty() && wait == 0L && roleAvailable,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp).testTag("unlockButton"),
                ) {
                    if (busy) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text(if (role == Role.ADMIN) "Unlock as Admin" else "Unlock as Viewer", fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                    }
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
        // The credit steps aside while the keyboard is open, so it never pushes the Unlock button
        // out of view on small screens.
        if (!WindowInsets.isImeVisible) com.example.etikoppakavoterdirectory.ui.components.FooterCredit()
      }
    }
}

/** One of the two role choices: teal border and a light teal background when selected. */
@Composable
private fun RoleCard(
    icon: ImageVector,
    label: String,
    subtitle: String,
    selected: Boolean,
    tag: String,
    modifier: Modifier,
    onSelect: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) scheme.primaryContainer.copy(alpha = 0.45f) else scheme.surfaceContainerLowest,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) scheme.primary else scheme.outlineVariant),
        modifier = modifier
            .fillMaxHeight()
            .testTag(tag)
            .selectable(selected = selected, role = SemanticsRole.RadioButton, onClick = onSelect),
    ) {
        Column(
            Modifier.padding(horizontal = 10.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(icon, contentDescription = null, tint = if (selected) scheme.primary else scheme.onSurfaceVariant, modifier = Modifier.size(28.dp))
            Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}
