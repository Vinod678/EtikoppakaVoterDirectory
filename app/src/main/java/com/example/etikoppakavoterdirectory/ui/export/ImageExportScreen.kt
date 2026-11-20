package com.example.etikoppakavoterdirectory.ui.export

import android.content.ActivityNotFoundException
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.etikoppakavoterdirectory.export.ImageLayout
import com.example.etikoppakavoterdirectory.export.ImageOptions
import com.example.etikoppakavoterdirectory.ui.ImageExportViewModel
import com.example.etikoppakavoterdirectory.ui.ImageStatus
import com.example.etikoppakavoterdirectory.ui.components.SectionCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Household image export (Compact or Detailed). [shareWhenReady] = "Share Image" from the
 * household menu: the share sheet opens as soon as the image is created.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageExportScreen(vm: ImageExportViewModel, householdId: Long, householdName: String?, shareWhenReady: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    val status by vm.status.collectAsState()
    var layoutName by rememberSaveable { mutableStateOf(ImageLayout.COMPACT.name) }
    var epic by rememberSaveable { mutableStateOf(false) }
    var assembly by rememberSaveable { mutableStateOf(false) }
    var shared by rememberSaveable { mutableStateOf(false) }
    var saveIndex by rememberSaveable { mutableIntStateOf(0) }

    fun share() {
        vm.shareIntent(context)?.let { try { context.startActivity(it) } catch (e: ActivityNotFoundException) { /* no receiving app */ } }
    }
    LaunchedEffect(status) {
        if (shareWhenReady && !shared && status is ImageStatus.Ready) { shared = true; share() }
    }
    val saveOne = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        if (uri != null) vm.saveOne(context.contentResolver, uri, saveIndex)
    }
    val saveAll = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.saveAll(context, uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(if (status is ImageStatus.Ready) "Image preview" else if (shareWhenReady) "Share Image" else "Export Image", maxLines = 1)
                        householdName?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { if (status is ImageStatus.Ready) { vm.reset(); shared = false } else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        when (val s = status) {
            is ImageStatus.Working -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(); Spacer(Modifier.height(12.dp)); Text(s.message)
                }
            }
            is ImageStatus.Ready -> Column(Modifier.fillMaxSize().padding(padding)) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Text(s.title, style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (s.files.size == 1) "1 image · PNG" else "${s.files.size} images (large household split into parts) · PNG",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                if (s.files.size == 1) { saveIndex = 0; saveOne.launch(s.files.first().name) } else saveAll.launch(null)
                            }, modifier = Modifier.weight(1f)) { Text(if (s.files.size == 1) "Save Image" else "Save all") }
                            FilledTonalButton(onClick = ::share, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp)); Text("Share Image")
                            }
                        }
                        TextButton(onClick = { vm.reset(); shared = false }) { Text("Change image settings") }
                    }
                }
                ImagePreview(s.files)
            }
            ImageStatus.Idle -> Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SectionCard(title = "Layout") {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Option("Compact", "Photos and names in a grid", layoutName == ImageLayout.COMPACT.name, Modifier.weight(1f)) { layoutName = ImageLayout.COMPACT.name }
                        Option("Detailed", "Names, age, gender, relation and IDs", layoutName == ImageLayout.DETAILED.name, Modifier.weight(1f)) { layoutName = ImageLayout.DETAILED.name }
                    }
                }
                if (layoutName == ImageLayout.DETAILED.name) {
                    SectionCard(title = "Sensitive – off by default") {
                        Toggle("EPIC number", epic) { epic = it }
                        Toggle("Assembly roll serial", assembly) { assembly = it }
                    }
                }
                Button(
                    onClick = { vm.generate(householdId, ImageOptions(ImageLayout.valueOf(layoutName), epic = epic, assemblySerial = assembly)) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text(if (shareWhenReady) "Create and share image" else "Create image") }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.tertiary)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "The image contains voter photos and personal details. It is created on this phone and only shared if you choose Share.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.navigationBarsPadding())
            }
        }
    }
}

@Composable
private fun Option(title: String, text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(10.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Shows the created images, decoded at screen width only (never the full-size bitmap). */
@Composable
private fun ImagePreview(files: List<File>) {
    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        val widthPx = with(LocalDensity.current) { (maxWidth - 32.dp).roundToPx() }.coerceIn(200, 1080)
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            itemsIndexed(files, key = { _, f -> f.name }) { i, f ->
                val bmp by produceState<Bitmap?>(null, f, widthPx) {
                    value = withContext(Dispatchers.IO) {
                        try {
                            val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            BitmapFactory.decodeFile(f.path, b)
                            var sample = 1
                            while (b.outWidth / (sample * 2) >= widthPx) sample *= 2
                            BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })
                        } catch (e: Throwable) {
                            null
                        }
                    }
                }
                Surface(shadowElevation = 2.dp, color = Color.White, modifier = Modifier.fillMaxWidth()) {
                    bmp?.let {
                        Image(
                            it.asImageBitmap(),
                            contentDescription = if (files.size > 1) "Household image part ${i + 1} of ${files.size}" else "Household image",
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } ?: Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) }
                }
            }
        }
    }
}
