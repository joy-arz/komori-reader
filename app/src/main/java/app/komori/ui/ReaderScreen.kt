@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.komori.ui

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.komori.domain.ReadingMode
import app.komori.domain.Release
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView

@Composable
fun ReaderScreen(vm: ReaderViewModel, chapter: Release) {
    val page by vm.readerPage.collectAsStateWithLifecycle()
    val count by vm.pageCount.collectAsStateWithLifecycle()
    val file by vm.readerFile.collectAsStateWithLifecycle()
    val error by vm.readerError.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val preferences by vm.preferences.collectAsStateWithLifecycle()
    val ready by vm.readerReady.collectAsStateWithLifecycle()
    val override = preferences.find { it.mangaId == chapter.mangaId }?.readerMode.orEmpty()
    val mode = ReadingMode.resolve(override, settings.webtoon, settings.rtl)
    var controls by rememberSaveable { mutableStateOf(true) }
    var options by rememberSaveable { mutableStateOf(false) }
    val view = LocalView.current
    val window = (view.context as? Activity)?.window
    LaunchedEffect(mode, ready) {
        if (ready && mode != ReadingMode.WEBTOON) vm.page(vm.readerPage.value)
    }
    DisposableEffect(window, controls) {
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (controls) controller?.show(WindowInsetsCompat.Type.systemBars())
        else controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
    DisposableEffect(settings.keepScreenOn) {
        val before = view.keepScreenOn
        view.keepScreenOn = settings.keepScreenOn
        onDispose { view.keepScreenOn = before }
    }
    BackHandler { if (!controls) controls = true else vm.closeReader() }
    fun navigate(delta: Int) {
        if (delta > 0 && page + 1 >= count) vm.finishChapter() else vm.page(page + delta)
    }
    Box(Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding()) {
        Column(Modifier.fillMaxSize()) {
            if (controls)
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = vm::closeReader) {
                        Icon(KomoriIcons.ArrowBack, "Close reader")
                    }
                    Column(Modifier.weight(1f)) {
                        Text(chapter.label, style = MaterialTheme.typography.titleSmall)
                        Text(
                            chapter.groupNames,
                            color = Color.LightGray,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                        )
                    }
                    Text(
                        "${page + 1} / $count",
                        Modifier.padding(8.dp),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    IconButton(onClick = { options = true }) {
                        Icon(KomoriIcons.Tune, "Reader settings")
                    }
                }
            if (mode == ReadingMode.WEBTOON && ready) {
                WebtoonReader(vm, chapter, Modifier.weight(1f), onTap = { controls = !controls })
            } else
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (error != null)
                        Column(
                            Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(error!!)
                            Button(onClick = { vm.page(page) }) { Text("Retry") }
                        }
                    else if (file == null) CircularProgressIndicator()
                    else
                        key(file!!.path) {
                            AndroidView(
                                factory = { context ->
                                    ReaderImageView(context).apply {
                                        setMinimumScaleType(
                                            SubsamplingScaleImageView.SCALE_TYPE_CENTER_INSIDE
                                        )
                                        setMaxScale(5f)
                                        setImage(ImageSource.uri(Uri.fromFile(file)))
                                        setOnImageEventListener(
                                            object :
                                                SubsamplingScaleImageView.DefaultOnImageEventListener() {
                                                override fun onImageLoadError(e: Exception) {
                                                    vm.readerError.value =
                                                        "Could not decode this page. Retry or use another release."
                                                }
                                            }
                                        )
                                    }
                                },
                                update = { image ->
                                    image.onReaderTap = { fraction ->
                                        val delta = mode.tapDelta(fraction)
                                        if (delta == null) controls = !controls else navigate(delta)
                                    }
                                    image.onReaderSwipe = { navigate(mode.swipeDelta(it)) }
                                },
                                modifier = Modifier.fillMaxSize().clipToBounds(),
                                onReset = null,
                                onRelease = { it.recycle() },
                            )
                        }
                }
            if (controls) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(onClick = { options = true }) { Text(mode.label) }
                    TextButton(onClick = { controls = false }) { Text("Hide controls") }
                }
                if (mode != ReadingMode.WEBTOON) {
                    Text(
                        "Swipe or tap edges to turn · Pinch to zoom",
                        Modifier.align(Alignment.CenterHorizontally),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.LightGray,
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        val previous: @Composable () -> Unit = {
                            OutlinedButton(
                                onClick = { navigate(-1) },
                                enabled = page > 0 && file != null,
                            ) {
                                Text("Previous")
                            }
                        }
                        val next: @Composable () -> Unit = {
                            Button(onClick = { navigate(1) }, enabled = file != null) {
                                Text(if (page + 1 < count) "Next page" else "Finish chapter")
                            }
                        }
                        if (mode == ReadingMode.RTL) {
                            next()
                            previous()
                        } else {
                            previous()
                            next()
                        }
                    }
                }
            }
        }
        if (!controls)
            Surface(
                onClick = { controls = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                color = Color.Black.copy(alpha = 0.8f),
                shape = RoundedCornerShape(24.dp),
            ) {
                Row(
                    Modifier.heightIn(min = 48.dp).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${page + 1} / $count",
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Spacer(Modifier.width(12.dp))
                    Icon(
                        KomoriIcons.Tune,
                        "Show reader controls",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
    }
    if (options)
        ModalBottomSheet(
            onDismissRequest = { options = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(
                Modifier.fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp)
            ) {
                Text("Reading mode", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Saved for this title. Other titles keep their own settings.",
                    Modifier.padding(top = 8.dp, bottom = 16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val choices =
                    listOf("" to "Use app default") + ReadingMode.entries.map { it.key to it.label }
                choices.forEach { (key, label) ->
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { vm.titleReaderMode(key) }
                            .heightIn(min = 52.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(override == key, onClick = { vm.titleReaderMode(key) })
                        Text(label, Modifier.padding(start = 8.dp))
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                Text(
                    "Paged mode: tap the center to hide controls; use edges or swipes to turn pages. When zoomed in, drag to pan. Webtoon: tap any image to hide controls.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(
                    onClick = { options = false },
                    modifier = Modifier.align(Alignment.End).padding(top = 16.dp),
                ) {
                    Text("Done")
                }
            }
        }
}
