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
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
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
import androidx.compose.ui.draw.clip
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
const val LOGIN_HELP = "For any information regarding login, please contact: 938one06338eight"

/** Shown when Admin is chosen from 3 Dec 2026 while no separate Admin password is configured. */
const val ADMIN_UNAVAILABLE =
    "Admin sign-in is not available yet: a separate Admin password has to be set up first. Please contact the developer."

/**
 * Sign-in: pick Member (the Viewer role, shown first and selected by default) or Admin, then enter that role's password. Only the
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

    val scheme = MaterialTheme.colorScheme
    val fieldShape = RoundedCornerShape(14.dp)
    val roleLabel = if (role == Role.ADMIN) "Admin" else "Member"

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
      Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(
                Modifier.widthIn(max = 420.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Lock badge: outlined lock in a soft teal rounded square.
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = scheme.primaryContainer.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, scheme.primary.copy(alpha = 0.15f)),
                    modifier = Modifier.size(64.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(AppIcons.LockOutline, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(30.dp))
                    }
                }
                Text(
                    "Etikoppaka Voter Directory",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                reason.message?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = scheme.tertiary, textAlign = TextAlign.Center)
                }
                Row(
                    Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(top = 4.dp).selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    RoleCard(AppIcons.PersonSearch, "Member", "Search & View", role == Role.VIEWER, "roleViewer", Modifier.weight(1f)) { selectRole(Role.VIEWER) }
                    RoleCard(AppIcons.Shield, "Admin", null, role == Role.ADMIN, "roleAdmin", Modifier.weight(1f)) { selectRole(Role.ADMIN) }
                }
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it.take(128); error = null },
                    placeholder = { Text("$roleLabel password") },
                    singleLine = true,
                    shape = fieldShape,
                    enabled = !busy && wait == 0L && roleAvailable,
                    isError = error != null,
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = scheme.surfaceContainerLowest,
                        focusedContainerColor = scheme.surfaceContainerLowest,
                        disabledContainerColor = scheme.surfaceContainerLowest,
                        unfocusedBorderColor = scheme.outlineVariant,
                    ),
                    trailingIcon = {
                        IconButton(onClick = { visible = !visible }) {
                            Icon(
                                if (visible) AppIcons.VisibilityOff else AppIcons.VisibilityOutlined,
                                contentDescription = if (visible) "Hide password" else "Show password",
                                tint = scheme.onSurfaceVariant,
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("passwordField"),
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
                    shape = fieldShape,
                    colors = ButtonDefaults.buttonColors(
                        disabledContainerColor = scheme.surfaceContainerHigh,
                        disabledContentColor = scheme.onSurfaceVariant.copy(alpha = 0.6f),
                    ),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("unlockButton"),
                ) {
                    if (busy) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Unlock as $roleLabel", fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                    }
                }
                LoginHelpCard()
                // The credit sits just under the help card (part of the scrolling content, so it
                // never pushes the Unlock button out of view while the keyboard is open).
                com.example.etikoppakavoterdirectory.ui.components.FooterCredit()
            }
        }
      }
    }
}

/** "For any information regarding login..." in a soft card with a phone icon; the number is bold. */
@Composable
private fun LoginHelpCard() {
    val scheme = MaterialTheme.colorScheme
    val split = LOGIN_HELP.lastIndexOf(": ") + 2
    val text = buildAnnotatedString {
        append(LOGIN_HELP.substring(0, split))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = scheme.onSurface)) { append(LOGIN_HELP.substring(split)) }
    }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = scheme.surfaceContainerLow,
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(AppIcons.PhoneOutline, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(18.dp))
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).padding(start = 8.dp),
            )
        }
    }
}

/**
 * One of the two role choices. Selected: teal border, light teal background, a tinted icon tile and a
 * check badge in the corner. Unselected: white card with a thin grey border.
 */
@Composable
private fun RoleCard(
    icon: ImageVector,
    label: String,
    subtitle: String?,
    selected: Boolean,
    tag: String,
    modifier: Modifier,
    onSelect: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(18.dp)
    Surface(
        shape = shape,
        color = if (selected) scheme.primaryContainer.copy(alpha = 0.35f) else scheme.surfaceContainerLowest,
        border = BorderStroke(if (selected) 2.dp else 1.5.dp, if (selected) scheme.primary else scheme.outlineVariant),
        modifier = modifier
            .fillMaxHeight()
            .heightIn(min = 120.dp)
            .testTag(tag)
            // Keeps the press highlight inside the rounded card.
            .clip(shape)
            .selectable(selected = selected, role = SemanticsRole.RadioButton, onClick = onSelect),
    ) {
        Box(Modifier.fillMaxSize()) {
            if (selected) {
                Surface(shape = CircleShape, color = scheme.primary, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(18.dp)) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = scheme.onPrimary, modifier = Modifier.padding(3.dp))
                }
            }
            Column(
                Modifier.align(Alignment.Center).padding(horizontal = 10.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (selected) scheme.primary.copy(alpha = 0.15f) else scheme.surfaceContainerHigh,
                    modifier = Modifier.size(40.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, tint = if (selected) scheme.primary else scheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                    }
                }
                Text(
                    label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) scheme.primary else scheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = if (selected) scheme.primary else scheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            }
        }
    }
}
