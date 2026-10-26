package app.komori.ui

import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clipToBounds
import app.komori.domain.ReaderContinuity

import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.komori.data.ContentFailure
import app.komori.data.automaticRetryAllowed
import app.komori.data.connectionMessage
import app.komori.data.requiresSourceAction
import app.komori.data.retryAtMillis
import app.komori.data.sourceKey
import app.komori.domain.Release
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

private const val STRIP_HEIGHT = 1024

private data class ScrollImage(val file: File, val width: Int, val height: Int) {
    val strips
        get() = (height + STRIP_HEIGHT - 1) / STRIP_HEIGHT
}

@Composable
internal fun ChapterSeparator(label: String) {
    Row(
        Modifier.fillMaxWidth()
            .background(Color.Black)
            .padding(horizontal = 24.dp, vertical = 24.dp)
            .semantics(mergeDescendants = true) { heading() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HorizontalDivider(Modifier.weight(1f), color = Color(0xFF29382F))
        Surface(
            shape = RoundedCornerShape(50),
            color = Color(0xFF101713),
            border = BorderStroke(1.dp, Color(0xFF29382F)),
        ) {
            Text(
                label,
                Modifier.widthIn(max = 240.dp).padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFFD7DFD8),
                maxLines = 2,
                textAlign = TextAlign.Center,
                overflow = TextOverflow.Ellipsis,
            )
        }
        HorizontalDivider(Modifier.weight(1f), color = Color(0xFF29382F))
    }
}

/** A bounded previous/current/next window. Only visible strips allocate image decoders. */
@OptIn(FlowPreview::class)
@Composable
fun WebtoonReader(
    vm: ReaderViewModel,
    chapter: Release,
    modifier: Modifier = Modifier,
    onTap: () -> Unit = {},
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val chapterFeed by vm.chapters.collectAsStateWithLifecycle()
    val titlePreferences by vm.preferences.collectAsStateWithLifecycle()
    val shelf by vm.shelf.collectAsStateWithLifecycle()
    val initialChapter = remember { chapter }
    val session = remember { vm.readerSession.value }
    val initialPage = remember { vm.readerPage.value }
    val initialFraction = remember { vm.readerScroll.value }
    var window by remember { mutableStateOf(listOf(chapter)) }
    val images = remember { mutableStateMapOf<String, ScrollImage>() }
    val list = remember { LazyListState(initialPage + 1) }
    val cacheOwner = remember { Any() }
    var restored by remember { mutableStateOf(false) }
    var nextError by remember { mutableStateOf<Exception?>(null) }
    var nextLoading by remember { mutableStateOf(false) }
    var nextAttempt by remember { mutableIntStateOf(0) }
    val head = window.first()
    var previousError by remember { mutableStateOf<Exception?>(null) }
    var previousLoading by remember { mutableStateOf(false) }
    var previousAttempt by remember { mutableIntStateOf(0) }
    val allowedGaps = remember { mutableStateListOf<String>() }
    val previous = remember(head, chapterFeed, titlePreferences, shelf, settings) { vm.previousChapter(head) }
    val tail = window.last()
    val next =
        remember(tail, chapterFeed, titlePreferences, shelf, settings) { vm.nextChapter(tail) }
    val nextCountdown =
        key(tail.id) {
            imageRetryCountdown(
                nextError != null && automaticRetryAllowed(nextError),
                settings.retrySeconds,
                retryAtMillis(nextError),
            ) {
                nextAttempt++
            }
        }

    LaunchedEffect(tail.id, next?.id, settings.continuousChapters, settings.gapWarnings, allowedGaps.toList(), nextAttempt) {
        nextError = null
        if (!settings.continuousChapters || next == null ||
            (settings.gapWarnings && ReaderContinuity.gap(tail, next) != null && next.id !in allowedGaps)) return@LaunchedEffect
        snapshotFlow {
                list.layoutInfo.visibleItemsInfo.any {
                    val parts = it.key.toString().split('|')
                    parts.size == 3 &&
                        parts[0] == tail.id &&
                        (parts[1].toIntOrNull() ?: -1) >= tail.pages - 2
                }
            }
            .first { it }
        nextLoading = true
        try {
            val resolved = vm.resolveChapter(next)
            if (window.none { it.id == resolved.id }) window = window + resolved
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            nextError = e
        } finally {
            nextLoading = false
        }
    }

    LaunchedEffect(head.id, previous?.id, settings.continuousChapters, previousAttempt, restored) {
        previousError = null
        if (!restored || !settings.continuousChapters || previous == null) return@LaunchedEffect
        snapshotFlow { list.firstVisibleItemIndex <= 2 }.first { it }
        if (settings.gapWarnings && ReaderContinuity.gap(previous, head) != null && previous.id !in allowedGaps)
            return@LaunchedEffect
        previousLoading = true
        try {
            val resolved = vm.resolveChapter(previous)
            if (window.none { it.id == resolved.id }) window = listOf(resolved) + window
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { previousError = e }
        finally { previousLoading = false }
    }

    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    BoxWithConstraints(modifier.fillMaxWidth().clipToBounds()) {
        val viewportWidth = with(LocalDensity.current) { maxWidth.toPx() }
        val viewportHeight = with(LocalDensity.current) { maxHeight.toPx() }
        val zoomState = rememberTransformableState { factor, movement, _ ->
            zoom = (zoom * factor).coerceIn(1f, 3f)
            val moved = pan + movement
            pan = Offset(moved.x.coerceIn(-viewportWidth * (zoom - 1) / 2, viewportWidth * (zoom - 1) / 2),
                moved.y.coerceIn(-viewportHeight * (zoom - 1) / 2, viewportHeight * (zoom - 1) / 2))
        }
        val widthPixels = with(LocalDensity.current) { maxWidth.toPx() }
        fun position(): Triple<Release, Int, Float>? {
            val item =
                list.layoutInfo.visibleItemsInfo.firstOrNull {
                    it.key.toString().split('|').size == 3
                } ?: return null
            val parts = item.key.toString().split('|')
            val release = window.find { it.id == parts[0] } ?: return null
            val page = parts[1].toIntOrNull() ?: return null
            val strip = parts[2].toIntOrNull() ?: return null
            val image = images["${release.id}|$page"] ?: return null
            val sourceOffset = (-item.offset).coerceAtLeast(0) * image.width / widthPixels
            return Triple(
                release,
                page,
                ((strip * STRIP_HEIGHT + sourceOffset) / image.height).coerceIn(0f, 1f),
            )
        }
        LaunchedEffect(images["${initialChapter.id}|$initialPage"], widthPixels) {
            val image = images["${initialChapter.id}|$initialPage"] ?: return@LaunchedEffect
            if (!restored) {
                val sourceY = (initialFraction * image.height).toInt().coerceIn(0, image.height - 1)
                val preceding =
                    (0 until initialPage).sumOf { images["${initialChapter.id}|$it"]?.strips ?: 1 }
                list.scrollToItem(
                    1 + preceding + sourceY / STRIP_HEIGHT,
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
                    .debounce(150)
                    .collect { (release, page, fraction) ->
                        vm.scrollChapterPosition(release, page, fraction, session)
                        val index = window.indexOfFirst { it.id == release.id }
                        if (index > 1 || window.size > 3) {
                            window = window.drop((index - 1).coerceAtLeast(0)).take(3)
                            val retained = window.map { it.id }.toSet()
                            images.keys
                                .filter { it.substringBefore('|') !in retained }
                                .forEach { images.remove(it) }
                        }
                    }
        }
        LaunchedEffect(list, window) {
            snapshotFlow { list.layoutInfo.visibleItemsInfo.mapNotNull { item ->
                val parts = item.key.toString().split('|')
                if (parts.size != 3) null else window.find { it.id == parts[0] }?.let { release ->
                    parts[1].toIntOrNull()?.let { release to it }
                }
            }.distinct() }.distinctUntilChanged().collect { vm.pinReaderPages(cacheOwner, it) }
        }
        DisposableEffect(Unit) {
            onDispose {
                vm.releaseReaderPages(cacheOwner)
                if (restored)
                    position()?.let { (release, page, fraction) ->
                        vm.scrollChapterPosition(release, page, fraction, session, flush = true)
                    }
            }
        }
        LazyColumn(state = list, modifier = Modifier.fillMaxSize().background(Color.Black)
            .graphicsLayer(scaleX = zoom, scaleY = zoom, translationX = pan.x, translationY = pan.y)
            .transformable(zoomState, canPan = { zoom > 1f })) {
            item(key = "previous-chapter") {
                Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (previousLoading) CircularProgressIndicator(Modifier.size(24.dp))
                    if (previousError != null) {
                        Text(connectionMessage(previousError!!), color = Color.LightGray)
                        if (requiresSourceAction(previousError)) VerifySourceButton(vm, sourceKey(head.mangaId))
                    }
                    if (previous != null && settings.continuousChapters) {
                        val gap = ReaderContinuity.gap(previous, head)
                        if (settings.gapWarnings && gap != null) Text(gap, color = Color.LightGray)
                        TextButton(onClick = {
                            allowedGaps.add(previous.id)
                            vm.resetChapterRetries(previous)
                            previousAttempt++
                        }) { Text(if (previousError != null) "Retry previous chapter" else "Load previous chapter") }
                    }
                }
            }
            window.forEachIndexed { index, release ->
                if (index > 0)
                    item(key = "chapter-${release.id}") { ChapterSeparator(release.label) }
                repeat(release.pages.coerceAtLeast(0)) { page ->
                    val imageKey = "${release.id}|$page"
                    val image = images[imageKey]
                    if (image == null)
                        item(key = "$imageKey|0") {
                            val pageLease = remember { Any() }
                            DisposableEffect(release.id, page) {
                                vm.pinReaderPages(pageLease, listOf(release to page))
                                onDispose { vm.releaseReaderPages(pageLease) }
                            }
                            var failure by remember { mutableStateOf<Exception?>(null) }
                            var attempt by remember { mutableIntStateOf(0) }
                            val countdown =
                                imageRetryCountdown(
                                    failure != null && automaticRetryAllowed(failure),
                                    settings.retrySeconds,
                                    retryAtMillis(failure),
                                ) {
                                    attempt++
                                }
                            LaunchedEffect(attempt) {
                                failure = null
                                try {
                                    if (attempt > 0) vm.invalidateCachedPage(release, page)
                                    val file = vm.pageFileFor(release, page)
                                    vm.prefetchAfter(release, page)
                                    images[imageKey] =
                                        withContext(Dispatchers.IO) {
                                            val bounds =
                                                BitmapFactory.Options().apply {
                                                    inJustDecodeBounds = true
                                                }
                                            BitmapFactory.decodeFile(file.path, bounds)
                                            if (bounds.outWidth <= 0 || bounds.outHeight <= 0)
                                                throw ContentFailure(
                                                    "Could not decode page ${page + 1}. Retry manually or use another release."
                                                )
                                            ScrollImage(file, bounds.outWidth, bounds.outHeight)
                                        }
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    failure = e
                                }
                            }
                            Column(
                                Modifier.fillMaxWidth().height(420.dp).padding(24.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text("${release.label} · Page ${page + 1}", color = Color.LightGray)
                                if (failure == null)
                                    CircularProgressIndicator(Modifier.padding(16.dp))
                                else {
                                    Text(connectionMessage(failure!!), color = Color.White)
                                    if (requiresSourceAction(failure))
                                        VerifySourceButton(vm, sourceKey(release.mangaId))
                                    if (countdown > 0)
                                        Text(
                                            "Retrying this page in ${countdown}s",
                                            color = Color.LightGray,
                                        )
                                    Button(
                                        onClick = {
                                            vm.resetPageRetries(release, page)
                                            attempt++
                                        }
                                    ) {
                                        Text("Retry page now")
                                    }
                                }
                            }
                        }
                    else
                        repeat(image.strips) { strip ->
                            item(key = "$imageKey|$strip") {
                                val pageLease = remember { Any() }
                                DisposableEffect(release.id, page) {
                                    vm.pinReaderPages(pageLease, listOf(release to page))
                                    onDispose { vm.releaseReaderPages(pageLease) }
                                }
                                val top = strip * STRIP_HEIGHT
                                val bottom = minOf(top + STRIP_HEIGHT, image.height)
                                var availableFile by
                                    remember(image.file) {
                                        mutableStateOf<File?>(image.file.takeIf { it.exists() })
                                    }
                                var decoded by remember { mutableStateOf(false) }
                                var decodeError by remember { mutableStateOf<Exception?>(null) }
                                var retry by remember { mutableIntStateOf(0) }
                                val countdown =
                                    imageRetryCountdown(
                                        decodeError != null && automaticRetryAllowed(decodeError),
                                        settings.retrySeconds,
                                        retryAtMillis(decodeError),
                                    ) {
                                        retry++
                                    }
                                LaunchedEffect(retry) {
                                    if (retry == 0 && availableFile != null) return@LaunchedEffect
                                    availableFile = null
                                    decoded = false
                                    decodeError = null
                                    try {
                                        if (retry > 0) vm.invalidateCachedPage(release, page)
                                        availableFile = vm.pageFileFor(release, page)
                                        vm.prefetchAfter(release, page)
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        decodeError = e
                                    }
                                }
                                val stripWidth = maxWidth
                                Box(
                                    Modifier.fillMaxWidth()
                                        .height(
                                            stripWidth * ((bottom - top).toFloat() / image.width)
                                        )
                                ) {
                                    availableFile?.let { file ->
                                        val displayWidth =
                                            with(LocalDensity.current) { stripWidth.roundToPx() }
                                        val stripKey =
                                            WebtoonStripCache.key(file, top, bottom, displayWidth)
                                        var bitmap by
                                            remember(stripKey, retry) {
                                                mutableStateOf(WebtoonStripCache.peek(stripKey))
                                            }
                                        LaunchedEffect(stripKey, retry) {
                                            try {
                                                bitmap =
                                                    bitmap
                                                        ?: WebtoonStripCache.load(
                                                            file,
                                                            top,
                                                            bottom,
                                                            image.width,
                                                            displayWidth,
                                                        )
                                                decoded = true
                                            } catch (e: CancellationException) {
                                                throw e
                                            } catch (e: Exception) {
                                                decodeError =
                                                    ContentFailure(
                                                        "Could not decode this page. Retry manually or use another release."
                                                    )
                                            }
                                        }
                                        bitmap?.let {
                                            Image(
                                                it.asImageBitmap(),
                                                "${release.label}, page ${page + 1}, part ${strip + 1} of ${image.strips}",
                                                Modifier.fillMaxSize().clickable { onTap() },
                                                contentScale = ContentScale.FillBounds,
                                            )
                                        }
                                    }
                                    if (!decoded && decodeError == null)
                                        CircularProgressIndicator(
                                            Modifier.size(24.dp).align(Alignment.Center),
                                            strokeWidth = 2.dp,
                                        )
                                    if (decodeError != null)
                                        Column(
                                            Modifier.align(Alignment.Center),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                        ) {
                                            Text(
                                                connectionMessage(decodeError!!),
                                                color = Color.White,
                                            )
                                            if (requiresSourceAction(decodeError))
                                                VerifySourceButton(vm, sourceKey(release.mangaId))
                                            else
                                                Button(
                                                    onClick = {
                                                        vm.resetPageRetries(release, page)
                                                        retry++
                                                    }
                                                ) {
                                                    Text(
                                                        if (countdown > 0)
                                                            "Retry image · ${countdown}s"
                                                        else "Retry image"
                                                    )
                                                }
                                        }
                                }
                            }
                        }
                }
            }
            item(key = "end-${tail.id}") {
                LaunchedEffect(tail.id, session) {
                    snapshotFlow {
                        list.layoutInfo.visibleItemsInfo.any { it.key == "end-${tail.id}" } &&
                            images["${tail.id}|${tail.pages - 1}"] != null
                    }.distinctUntilChanged().collect { visible ->
                        if (visible) vm.completeVisibleChapter(tail, session)
                    }
                }
                Column(
                    Modifier.fillMaxWidth().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (settings.continuousChapters && next != null) {
                        val gap = ReaderContinuity.gap(tail, next)
                        if (settings.gapWarnings && gap != null && next.id !in allowedGaps) {
                            Text(gap, color = Color.LightGray)
                            Button(onClick = { allowedGaps.add(next.id); nextAttempt++ }) { Text("Continue across gap") }
                        } else Text(
                            nextError?.let(::connectionMessage) ?: "Loading ${next.label}…",
                            color = Color.LightGray,
                        )
                        if (nextLoading) CircularProgressIndicator(Modifier.padding(12.dp))
                        if (requiresSourceAction(nextError))
                            VerifySourceButton(vm, sourceKey(next.mangaId))
                        if (nextError != null) {
                            if (nextCountdown > 0)
                                Text("Retrying in ${nextCountdown}s", color = Color.LightGray)
                            Button(
                                onClick = {
                                    vm.resetChapterRetries(next)
                                    nextAttempt++
                                }
                            ) {
                                Text("Retry next chapter")
                            }
                        }
                    } else {
                        Text(
                            if (next == null) "You're caught up" else "Chapter complete",
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Button(onClick = { if (next == null) vm.startReading(true) else vm.finishChapter() }) {
                            Text(if (next == null) "Read again" else "Continue to next chapter")
                        }
                    }
                }
            }
        }
        if (zoom > 1.01f) TextButton(onClick = { zoom = 1f; pan = Offset.Zero },
            modifier = Modifier.align(Alignment.TopEnd).background(Color.Black.copy(alpha = 0.9f))) {
            Text("Reset zoom", color = Color.White)
        }
    }
}
