package app.komori.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.komori.data.SavedImage
import app.komori.data.SavedImages
import coil.compose.AsyncImage
import coil.request.ImageRequest

@Composable
internal fun SavedImagesSection(vm: ReaderViewModel, modifier: Modifier = Modifier) {
    val images by vm.savedImages.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var deleteTarget by remember { mutableStateOf<SavedImage?>(null) }
    val failed = remember { mutableStateMapOf<String, Boolean>() }
    LaunchedEffect(Unit) { vm.pruneMissingSavedImages() }
    if (images.isEmpty()) {
        EmptyState(
            KomoriIcons.Image,
            "No saved images yet",
            "Long-press a page in the reader, then choose Save Image. Saved copies appear in Pictures/${SavedImages.ALBUM} and stay separate from chapter downloads.",
        )
        return
    }
    LazyVerticalGrid(
        GridCells.Adaptive(150.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 28.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(images, key = { it.id }, contentType = { "saved-image" }) { image ->
            Card(shape = RoundedCornerShape(14.dp)) {
                Column {
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(0.78f)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        AsyncImage(
                            ImageRequest.Builder(context).data(Uri.parse(image.uri)).crossfade(false).build(),
                            "${image.mangaTitle}, ${image.chapterLabel}, page ${image.page + 1}",
                            Modifier.fillMaxSize().clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)),
                            contentScale = ContentScale.Crop,
                            onError = { failed[image.id] = true },
                            onSuccess = { failed.remove(image.id) },
                        )
                        if (failed[image.id] == true)
                            Column(
                                Modifier.align(Alignment.Center).padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Icon(KomoriIcons.Image, null, Modifier.size(26.dp))
                                Text(
                                    "Image unavailable",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                    }
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            image.mangaTitle.ifBlank { "Saved image" },
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${image.chapterLabel} · Page ${image.page + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(
                            Modifier.fillMaxWidth().padding(top = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(
                                onClick = {
                                    if (!SavedImages.canOpen(context, image)) {
                                        vm.error.value =
                                            "This image was moved or deleted outside Komori."
                                        vm.pruneMissingSavedImages(notify = true)
                                    } else {
                                        try {
                                            context.startActivity(
                                                Intent(Intent.ACTION_VIEW)
                                                    .setDataAndType(Uri.parse(image.uri), image.mimeType)
                                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            )
                                        } catch (_: Exception) {
                                            vm.error.value = "No installed app can open this image."
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            ) { Text("Open") }
                            IconButton(
                                onClick = { deleteTarget = image },
                                modifier = Modifier.semantics {
                                    contentDescription = "Delete saved image ${image.fileName}"
                                },
                            ) { Icon(KomoriIcons.Delete, "Delete saved image") }
                        }
                    }
                }
            }
        }
    }
    deleteTarget?.let { image ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete saved image?") },
            text = {
                Text(
                    "Remove this saved copy from Pictures/${SavedImages.ALBUM}? " +
                        "Chapter downloads, reading progress and the title in your library are not affected."
                )
            },
            confirmButton = {
                TextButton(onClick = { deleteTarget = null; vm.deleteSavedImage(image.id) }) {
                    Text("Delete")
                }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } },
        )
    }
}
