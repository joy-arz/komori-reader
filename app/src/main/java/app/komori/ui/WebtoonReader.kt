package app.komori.ui

import android.graphics.BitmapFactory
import android.graphics.Rect
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.komori.data.connectionMessage
import app.komori.domain.Release
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

private const val STRIP_HEIGHT = 1024

private data class ScrollImage(val file: File, val width: Int, val height: Int) {
    val strips
        get() = (height + STRIP_HEIGHT - 1) / STRIP_HEIGHT
}

/** Only visible strips create decoders; no full chapter or tall page bitmap is allocated. */
@OptIn(FlowPreview::class)
@Composable
fun WebtoonReader(vm: ReaderViewModel, chapter: Release, modifier: Modifier = Modifier) {
    val initialPage = remember { vm.readerPage.value }
    val initialFraction = remember { vm.readerScroll.value }
    val images = remember { mutableStateMapOf<Int, ScrollImage>() }
    val list = rememberLazyListState(initialPage)
    var restored by remember { mutableStateOf(false) }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val widthPixels = with(LocalDensity.current) { maxWidth.toPx() }
        fun position(): Pair<Int, Float>? {
            val item = list.layoutInfo.visibleItemsInfo.firstOrNull() ?: return null
            val parts = item.key.toString().split(':')
            val page = parts.firstOrNull()?.toIntOrNull() ?: return null
            val strip = parts.getOrNull(1)?.toIntOrNull() ?: return null
            val image = images[page] ?: return null
            val sourceOffset = (-item.offset).coerceAtLeast(0) * image.width / widthPixels
            return page to ((strip * STRIP_HEIGHT + sourceOffset) / image.height).coerceIn(0f, 1f)
        }
        LaunchedEffect(images[initialPage], widthPixels) {
            val image = images[initialPage] ?: return@LaunchedEffect
            if (!restored) {
                val sourceY = (initialFraction * image.height).toInt().coerceIn(0, image.height - 1)
                val preceding = (0 until initialPage).sumOf { images[it]?.strips ?: 1 }
                list.scrollToItem(
                    preceding + sourceY / STRIP_HEIGHT,
                    ((sourceY % STRIP_HEIGHT) * widthPixels / image.width).toInt(),
                )
                restored = true
            }
        }
        LaunchedEffect(restored) {
            if (restored)
                snapshotFlow { position() }
                    .filterNotNull()
                    .distinctUntilChanged()
                    .debounce(200)
                    .collect { vm.scrollPosition(chapter, it.first, it.second) }
        }
        DisposableEffect(chapter.id) {
            onDispose {
                if (restored) position()?.let { vm.scrollPosition(chapter, it.first, it.second) }
            }
        }
        LazyColumn(state = list, modifier = Modifier.fillMaxSize().background(Color.Black)) {
            repeat(chapter.pages) { page ->
                val image = images[page]
                if (image == null) {
                    item(key = "$page:0") {
                        var failure by remember { mutableStateOf<String?>(null) }
                        var attempt by remember { mutableIntStateOf(0) }
                        LaunchedEffect(attempt) {
                            failure = null
                            try {
                                val file = vm.pageFileFor(chapter, page)
                                val dimensions =
                                    withContext(Dispatchers.IO) {
                                        val bounds =
                                            BitmapFactory.Options().apply {
                                                inJustDecodeBounds = true
                                            }
                                        BitmapFactory.decodeFile(file.path, bounds)
                                        check(bounds.outWidth > 0 && bounds.outHeight > 0) {
                                            "Could not decode page ${page + 1}"
                                        }
                                        ScrollImage(file, bounds.outWidth, bounds.outHeight)
                                    }
                                images[page] = dimensions
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                failure = connectionMessage(e)
                            }
                        }
                        Column(
                            Modifier.fillMaxWidth().height(420.dp).padding(24.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text("Page ${page + 1}", color = Color.LightGray)
                            if (failure == null) CircularProgressIndicator(Modifier.padding(16.dp))
                            else {
                                Text(failure!!, color = Color.White)
                                Button(onClick = { attempt++ }) { Text("Retry page") }
                            }
                        }
                    }
                } else
                    repeat(image.strips) { strip ->
                        item(key = "$page:$strip") {
                            val top = strip * STRIP_HEIGHT
                            val bottom = minOf(top + STRIP_HEIGHT, image.height)
                            var availableFile by remember { mutableStateOf<File?>(null) }
                            var decodeError by remember { mutableStateOf(false) }
                            var retry by remember { mutableIntStateOf(0) }
                            LaunchedEffect(retry) {
                                try {
                                    availableFile = vm.pageFileFor(chapter, page)
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    decodeError = true
                                }
                            }
                            Box(
                                Modifier.fillMaxWidth()
                                    .height(maxWidth * ((bottom - top).toFloat() / image.width))
                            ) {
                                if (availableFile != null)
                                    key(retry) {
                                        AndroidView(
                                            factory = { context ->
                                                SubsamplingScaleImageView(context).apply {
                                                    setZoomEnabled(false)
                                                    setPanEnabled(false)
                                                    setMinimumScaleType(
                                                        SubsamplingScaleImageView
                                                            .SCALE_TYPE_CENTER_INSIDE
                                                    )
                                                    setOnImageEventListener(
                                                        object :
                                                            SubsamplingScaleImageView.DefaultOnImageEventListener() {
                                                            override fun onImageLoadError(
                                                                e: Exception
                                                            ) {
                                                                decodeError = true
                                                            }

                                                            override fun onTileLoadError(
                                                                e: Exception
                                                            ) {
                                                                decodeError = true
                                                            }
                                                        }
                                                    )
                                                    setImage(
                                                        ImageSource.uri(Uri.fromFile(availableFile))
                                                            .region(
                                                                Rect(0, top, image.width, bottom)
                                                            )
                                                    )
                                                }
                                            },
                                            modifier = Modifier.fillMaxSize(),
                                            onReset = null,
                                            onRelease = { it.recycle() },
                                        )
                                    }
                                if (decodeError)
                                    Button(
                                        onClick = {
                                            decodeError = false
                                            retry++
                                        },
                                        modifier = Modifier.align(Alignment.Center),
                                    ) {
                                        Text("Retry image")
                                    }
                            }
                        }
                    }
            }
            item(key = "end") {
                Column(
                    Modifier.fillMaxWidth().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "Chapter complete",
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        "Continue to the next available chapter.",
                        color = Color.LightGray,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                    Button(onClick = { vm.finishChapter() }) { Text("Finish & continue") }
                }
            }
        }
    }
}
