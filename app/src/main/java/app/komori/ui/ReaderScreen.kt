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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import app.komori.domain.ReaderTapZones
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.komori.domain.ReaderContinuity
import app.komori.MainActivity
import app.komori.domain.ReadingMode
import app.komori.domain.Release
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView

@Suppress("DEPRECATION")
@Composable
fun ReaderScreen(vm: ReaderViewModel, chapter: Release) {
    val page by vm.readerPage.collectAsStateWithLifecycle()
    val count by vm.pageCount.collectAsStateWithLifecycle()
    val file by vm.readerFile.collectAsStateWithLifecycle()
    val error by vm.readerError.collectAsStateWithLifecycle()
    val retryAllowed by vm.readerRetryAllowed.collectAsStateWithLifecycle()
    val retryAt by vm.readerRetryAt.collectAsStateWithLifecycle()
    val sourceAction by vm.readerSourceAction.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val preferences by vm.preferences.collectAsStateWithLifecycle()
    val ready by vm.readerReady.collectAsStateWithLifecycle()
    val refreshing by vm.refreshingReader.collectAsStateWithLifecycle()
    val retryCountdown =
        key(chapter.id, page) {
            imageRetryCountdown(error != null && retryAllowed, settings.retrySeconds, retryAt) {
                vm.retryPage(manual = false)
            }
        }
    val override = preferences.find { it.mangaId == chapter.mangaId }?.readerMode.orEmpty()
    val mode = ReadingMode.resolve(override, settings.webtoon, settings.rtl)
    var viewportBounds by remember { mutableStateOf<Rect?>(null) }
    var controls by rememberSaveable { mutableStateOf(false) }
    var options by rememberSaveable { mutableStateOf(false) }
    var jump by rememberSaveable { mutableStateOf(false) }
    val gapTarget by vm.readerGapTarget.collectAsStateWithLifecycle()
    val view = LocalView.current
    val window = (view.context as? Activity)?.window
    LaunchedEffect(mode, ready) {
        if (ready && mode != ReadingMode.WEBTOON) vm.page(vm.readerPage.value)
    }
    DisposableEffect(window, controls) {
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val decor = window?.decorView
        val legacyMask = android.view.View.SYSTEM_UI_FLAG_IMMERSIVE or android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        val legacyBefore = decor?.systemUiVisibility?.and(legacyMask) ?: 0
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        // Android 11 loses controller behavior after LayoutParams changes such as reader brightness.
        if (android.os.Build.VERSION.SDK_INT == 30 && decor != null) {
            decor.systemUiVisibility = (decor.systemUiVisibility and android.view.View.SYSTEM_UI_FLAG_IMMERSIVE.inv()) or
                android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        }
        if (controls) controller?.show(WindowInsetsCompat.Type.systemBars())
        else controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            if (android.os.Build.VERSION.SDK_INT == 30 && decor != null) {
                decor.systemUiVisibility = (decor.systemUiVisibility and legacyMask.inv()) or legacyBefore
            }
        }
    }
    DisposableEffect(settings.keepScreenOn) {
        val before = view.keepScreenOn
        view.keepScreenOn = settings.keepScreenOn
        onDispose { view.keepScreenOn = before }
    }
    BackHandler { vm.closeReader() }
    fun navigate(delta: Int) = vm.navigateReader(delta)
    Box(Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding().onGloballyPositioned { viewportBounds = it.boundsInWindow() }.semantics {
        contentDescription = "Reading area"
        customActions = listOf(CustomAccessibilityAction(if (controls) "Hide reader controls" else "Show reader controls") {
            controls = !controls
            true
        })
    }) {
        Column(Modifier.fillMaxSize()) {
            if (controls)
                Row(
                    Modifier.fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RoundControl(KomoriIcons.ArrowBack, "Close reader", vm::closeReader)
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
                        Modifier.clickable { jump = true }.heightIn(min = 48.dp).wrapContentHeight().padding(8.dp),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    RefreshControl("Refresh reading", refreshing || !ready && error == null, vm::refreshReader)
                    RoundControl(KomoriIcons.Tune, "Reader settings", { options = true })
                }
            if (mode == ReadingMode.WEBTOON && ready) {
                WebtoonReader(vm, chapter, Modifier.weight(1f).graphicsLayer(alpha = 1f - settings.dim / 100f), onTap = { controls = !controls }, controlBounds = { viewportBounds })
            } else
                Box(
                    Modifier.weight(1f).fillMaxWidth().then(
                        if (file == null || error != null) Modifier.readerCenterTap({ viewportBounds }) { controls = !controls }
                        else Modifier
                    ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (error != null)
                        Column(
                            Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(error!!)
                            if (sourceAction)
                                VerifySourceButton(vm, app.komori.data.sourceKey(chapter.mangaId))
                            if (retryCountdown > 0) Text("Retrying this page in ${retryCountdown}s")
                            Button(onClick = { vm.retryPage() }) { Text("Retry") }
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
                                                    vm.readerRetryAllowed.value = false
                                                    vm.readerSourceAction.value = false
                                                    vm.readerRetryAt.value = 0L
                                                    vm.readerError.value =
                                                        "Could not decode this page. Retry or use another release."
                                                }
                                            }
                                        )
                                    }
                                },
                                update = { image ->
                                    image.onReaderTap = { x, y ->
                                        val delta = mode.tapDelta(x)
                                        if (delta != null) navigate(delta)
                                        else {
                                            val viewport = viewportBounds
                                            val origin = IntArray(2)
                                            image.getLocationInWindow(origin)
                                            val screenX = viewport?.takeIf { it.width > 0f }?.let { (origin[0] + x * image.width - it.left) / it.width } ?: x
                                            val screenY = viewport?.takeIf { it.height > 0f }?.let { (origin[1] + y * image.height - it.top) / it.height } ?: y
                                            if (ReaderTapZones.isControlTap(screenX, screenY)) controls = !controls
                                        }
                                    }
                                    image.onReaderSwipe = { navigate(mode.swipeDelta(it)) }
                                },
                                modifier = Modifier.fillMaxSize().clipToBounds().graphicsLayer(alpha = 1f - settings.dim / 100f),
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
                    TextButton(onClick = { jump = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Go to chapter / page") }
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        val previous: @Composable () -> Unit = {
                            OutlinedButton(
                                onClick = { navigate(-1) },
                                enabled = (page > 0 || vm.previousChapter(chapter) != null) && file != null,
                            ) {
                                Text("Previous")
                            }
                        }
                        val next: @Composable () -> Unit = {
                            val endOfTitle = page + 1 >= count && vm.nextChapter(chapter) == null
                            ActionButton(onClick = { if (endOfTitle) vm.startReading(true) else navigate(1) }, enabled = file != null) {
                                Text(if (endOfTitle) "Read again" else if (page + 1 < count) "Next page" else "Next chapter")
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
                modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                color = Color.Black.copy(alpha = 0.8f),
                shape = RoundedCornerShape(24.dp),
            ) {
                Text(
                    "${page + 1} / $count",
                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
    }
    gapTarget?.let { next ->
        AlertDialog(onDismissRequest = { vm.readerGapTarget.value = null }, title = { Text("Chapter gap") },
            text = { Text(ReaderContinuity.gap(chapter, next).orEmpty()) },
            confirmButton = { TextButton(onClick = { vm.readerGapTarget.value = null; vm.finishChapter() }) { Text("Continue") } },
            dismissButton = { TextButton(onClick = { vm.readerGapTarget.value = null }) { Text("Stay here") } })
    }
    if (jump) ReaderJumpDialog(vm, chapter, page, count) { jump = false }
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
                ReaderTools(vm, settings)
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                Text(
                    "Tap the middle of the screen to show or hide controls. Paged mode: use edges or swipes to turn pages; pinch to zoom and drag to pan. Webtoon: scroll to read and pinch to zoom.",
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
