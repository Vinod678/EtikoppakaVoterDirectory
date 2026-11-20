package com.example.etikoppakavoterdirectory.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.etikoppakavoterdirectory.data.backup.BackupCodec
import com.example.etikoppakavoterdirectory.data.backup.BackupContent
import com.example.etikoppakavoterdirectory.ui.BackupUiState
import com.example.etikoppakavoterdirectory.ui.ImportState
import com.example.etikoppakavoterdirectory.ui.SettingsViewModel
import com.example.etikoppakavoterdirectory.ui.components.formatDateTime
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.etikoppakavoterdirectory.ui.components.AppIcons

private enum class SettingsPage(val title: String) { MENU("Settings"), BACKUP("Backup & Restore"), SECURITY("Sign-in & Security"), ABOUT("About"), TERMS("Terms & Conditions") }

/** Where Back goes from each page: Terms returns to About, the other pages to the menu. */
private val SettingsPage.parent get() = if (this == SettingsPage.TERMS) SettingsPage.ABOUT else SettingsPage.MENU

/** Shown on the Terms & Conditions page (Admin and Viewer). */
val TERMS_AND_CONDITIONS = listOf(
    "This application is intended for authorized directory reference and voter-record management.",
    "Voter information must be handled responsibly and used only for lawful, legitimate purposes.",
    "Do not copy, distribute, publish, or misuse personal information without appropriate authorization.",
    "Information is provided for reference and may contain errors or require updates. Verify important details against official records.",
    "Users are responsible for protecting their login credentials and preventing unauthorized access.",
    "This is an independently developed application and is not an official government or Election Commission application.",
)

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    vm: SettingsViewModel,
    /** The open ward (Admin); null for the Viewer settings opened from the home screen. */
    ward: Int?,
    importState: ImportState,
    onRetryImport: () -> Unit,
    onExportPdf: () -> Unit,
    onHome: () -> Unit,
    onLogout: () -> Unit,
    /** Viewer: only About and Sign out (no export, backup or other management pages). */
    viewer: Boolean = false,
) {
    val context = LocalContext.current
    val info by vm.datasetInfo.collectAsState()
    val backupState by vm.backup.collectAsState()
    val canUndo by vm.canUndo.collectAsState()

    var pageName by rememberSaveable { mutableStateOf(SettingsPage.MENU.name) }
    // A Viewer can only ever be on the menu, About or Terms & Conditions.
    val page = SettingsPage.valueOf(pageName).let {
        if (viewer && it != SettingsPage.ABOUT && it != SettingsPage.TERMS) SettingsPage.MENU else it
    }
    var showExportPassword by rememberSaveable { mutableStateOf(false) }
    var restoreUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var confirmUndo by rememberSaveable { mutableStateOf(false) }
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }
    androidx.activity.compose.BackHandler(enabled = page != SettingsPage.MENU) { pageName = page.parent.name }

    val createDoc = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) vm.cancelExport() else vm.writeBackup(context.contentResolver, uri)
    }
    val openDoc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) restoreUri = uri
    }

    val versionName = remember {
        try {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
        } catch (e: Exception) {
            "?"
        }
    }

    Scaffold(
        containerColor = com.example.etikoppakavoterdirectory.ui.theme.AppTheme.brand.page,
        topBar = {
            com.example.etikoppakavoterdirectory.ui.components.ScreenHeader(
                title = page.title,
                subtitle = if (ward != null) "Ward $ward" else "Viewer · read-only access",
                navIcon = Icons.AutoMirrored.Filled.ArrowBack,
                navDescription = when {
                    page == SettingsPage.TERMS -> "Back to About"
                    page != SettingsPage.MENU -> "Back to settings"
                    viewer -> "Back"
                    else -> "All wards (home)"
                },
                onNav = { if (page != SettingsPage.MENU) pageName = page.parent.name else onHome() },
            )
        },
    ) { padding ->
        // The credit sits at the bottom of the screen when the page is short and after the content
        // when it is long (it scrolls, so it never covers buttons or the navigation bar).
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
        val viewport = maxHeight
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Column(
            Modifier.fillMaxWidth().heightIn(min = viewport),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (page) {
                SettingsPage.MENU -> {
                    if (importState is ImportState.Failed) {
                        Text("The voter data could not be loaded.", color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = onRetryImport) { Text("Try again") }
                    }
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                    ) {
                        Column {
                            if (!viewer) {
                                MenuRow(Icons.Filled.Share, "Export PDF", "Create, preview, save or share a PDF", onClick = onExportPdf)
                                HorizontalDivider()
                                MenuRow(Icons.Filled.Refresh, "Backup & Restore", "Encrypted backup of your own data") { pageName = SettingsPage.BACKUP.name }
                                HorizontalDivider()
                                MenuRow(Icons.Filled.Lock, "Sign-in & Security", "Login help") { pageName = SettingsPage.SECURITY.name }
                                HorizontalDivider()
                            }
                            MenuRow(Icons.Filled.Info, "About", "Version $versionName") { pageName = SettingsPage.ABOUT.name }
                            HorizontalDivider()
                            MenuRow(AppIcons.Logout, "Sign out", null, tint = MaterialTheme.colorScheme.error) { confirmSignOut = true }
                        }
                    }
                }
                SettingsPage.BACKUP -> Section("Backup and restore") {
                    Text(
                        "Backups contain your favourites, visits, blocks, block labels, notes, tags and households. " +
                            "They are encrypted with a password you choose; without it the backup cannot be opened.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    val busy = backupState is BackupUiState.Working
                    // Buttons wrap to a second line on narrow phones / large fonts instead of squeezing.
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(onClick = { showExportPassword = true }, enabled = !busy) { Text("Create backup") }
                        OutlinedButton(onClick = { openDoc.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) }, enabled = !busy) {
                            Text("Restore")
                        }
                    }
                    if (canUndo) {
                        TextButton(onClick = { confirmUndo = true }, enabled = !busy) { Text("Undo last restore") }
                    }
                    (backupState as? BackupUiState.Working)?.let {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text(it.label)
                        }
                    }
                }
                SettingsPage.SECURITY -> Section("Sign-in help") {
                    Text(com.example.etikoppakavoterdirectory.ui.auth.LOGIN_HELP, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = { confirmSignOut = true }) { Text("Sign out") }
                }
                SettingsPage.ABOUT -> Section("Etikoppaka Voter Directory") {
                    Row2("Application", "Etikoppaka Voter Directory")
                    Row2("Version", versionName)
                    Row2("Created by", "Vinod Yedla")
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "An offline Android application for organizing and searching village voter-directory records.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    MenuRow(Icons.Filled.Info, "Terms & Conditions", "How this app and its information may be used") {
                        pageName = SettingsPage.TERMS.name
                    }
                }
                SettingsPage.TERMS -> Section("Terms & Conditions") {
                    TERMS_AND_CONDITIONS.forEachIndexed { i, term ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("${i + 1}.", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                            Text(term, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        com.example.etikoppakavoterdirectory.ui.components.FooterCredit(Modifier.padding(top = 8.dp, bottom = 4.dp))
        }
        }
        }
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("Sign out?") },
            text = { Text("You will need the password to open the app again. Your data stays on this phone.") },
            confirmButton = { TextButton(onClick = { confirmSignOut = false; onLogout() }) { Text("Sign out") } },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Cancel") } },
        )
    }

    // ---------- dialogs ----------

    if (showExportPassword) {
        PasswordDialog(
            title = "Protect backup with a password",
            confirmLabel = "Choose file…",
            askTwice = true,
            onDismiss = { showExportPassword = false },
            onConfirm = { pw ->
                showExportPassword = false
                vm.prepareExport(pw)
                val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                createDoc.launch("etikoppaka_backup_$date.json")
            },
        )
    }
    restoreUri?.let { uri ->
        PasswordDialog(
            title = "Enter the backup password",
            confirmLabel = "Check backup",
            askTwice = false,
            onDismiss = { restoreUri = null },
            onConfirm = { pw ->
                restoreUri = null
                vm.readBackup(context.contentResolver, uri, pw)
            },
        )
    }
    (backupState as? BackupUiState.ConfirmRestore)?.let { state ->
        val currentDataset = if (state.content.isLegacyWard11Only) info.firstOrNull { it.wardNo == 11 }?.generatedAt
        else info.joinToString(";") { "${it.wardNo}=${it.generatedAt}" }.ifEmpty { null }
        ConfirmRestoreDialog(state.content, currentDataset, otherWards = info.any { it.wardNo != 11 }, onConfirm = vm::confirmRestore, onDismiss = vm::dismissRestore)
    }
    if (confirmUndo) {
        AlertDialog(
            onDismissRequest = { confirmUndo = false },
            title = { Text("Undo last restore?") },
            text = { Text("Your data will go back to how it was just before the last restore. Changes made since then will be lost.") },
            confirmButton = { TextButton(onClick = { confirmUndo = false; vm.undoLastRestore() }) { Text("Undo restore") } },
            dismissButton = { TextButton(onClick = { confirmUndo = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ConfirmRestoreDialog(content: BackupContent, currentDataset: String?, otherWards: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Replace your current data?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("The backup is valid. It was created on ${formatDateTime(content.createdAt)} and contains:")
                Text("• ${content.favourites} favourite(s)")
                Text("• ${content.visited} visited voter(s)")
                Text("• ${content.blocksAssigned} block assignment(s)")
                Text("• ${content.notes} note(s)")
                Text("• ${content.tags.size} tag(s), ${content.voterTags.size} tag assignment(s)")
                Text(
                    content.households?.let { "• ${it.size} household(s)" }
                        ?: "• No household information (made with app version 1). Your current households are kept."
                )
                if (content.isLegacyWard11Only && otherWards) {
                    Text(
                        "This backup was made by an older app version and only contains Ward 11. " +
                            "Restoring it also replaces your data for the other wards.",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (content.datasetGeneratedAt != null && content.datasetGeneratedAt != currentDataset) {
                    Text(
                        "Note: the backup was made with a different version of the voter dataset.",
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
                Text(
                    "All current favourites, visits, blocks, notes and tags of all wards" +
                        (if (content.households != null) " and households" else "") + " will be replaced. " +
                        "A safety copy is kept so you can undo this once.",
                    fontWeight = FontWeight.Medium,
                )
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Replace and restore") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PasswordDialog(
    title: String,
    confirmLabel: String,
    askTwice: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (CharArray) -> Unit,
) {
    // Deliberately not rememberSaveable: passwords are never written to saved instance state.
    var pw by remember { mutableStateOf("") }
    var pw2 by remember { mutableStateOf("") }
    val tooShort = pw.length < BackupCodec.MIN_PASSWORD_LENGTH
    val mismatch = askTwice && pw != pw2
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = pw,
                    onValueChange = { pw = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    supportingText = if (askTwice) {
                        { Text("At least ${BackupCodec.MIN_PASSWORD_LENGTH} characters") }
                    } else null,
                )
                if (askTwice) {
                    OutlinedTextField(
                        value = pw2,
                        onValueChange = { pw2 = it },
                        label = { Text("Repeat password") },
                        singleLine = true,
                        isError = pw2.isNotEmpty() && mismatch,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        supportingText = if (pw2.isNotEmpty() && mismatch) {
                            { Text("Passwords do not match") }
                        } else null,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(pw.toCharArray()) }, enabled = !tooShort && !mismatch) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) =
    com.example.etikoppakavoterdirectory.ui.components.SectionCard(title = title, content = content)

/** "2026-11-19T03:14:19" -> "Nov 19, 2026, 3:14 AM"; anything unexpected is shown unchanged. */
private fun formatDatasetVersion(raw: String): String = try {
    val parsed = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).parse(raw)
    if (parsed != null) formatDateTime(parsed.time) else raw
} catch (e: Exception) {
    raw
}

@Composable
private fun Row2(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.45f))
        Text(value, modifier = Modifier.weight(0.55f))
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun Bullet(text: String) {
    Text("• $text", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp))
}

@Composable
private fun MenuRow(icon: ImageVector, title: String, subtitle: String?, tint: Color = Color.Unspecified, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = if (tint == Color.Unspecified) MaterialTheme.colorScheme.primary else tint)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = if (tint == Color.Unspecified) Color.Unspecified else tint)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
    }
}
