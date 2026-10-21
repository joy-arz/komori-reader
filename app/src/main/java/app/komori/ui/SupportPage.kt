package app.komori.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

internal const val SUPPORT_URL = "https://ko-fi.com/joyarz"

/** Native interpretation of UI Layouts' coffee motif, with Komori colors and a Ko-fi destination. */
@Composable
internal fun SupportPage(openSupport: (() -> Unit)? = null) {
    val uri = LocalUriHandler.current
    var openFailed by remember { mutableStateOf(false) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val artSize = if (maxHeight < 650.dp) 160.dp else 240.dp
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight).padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CoffeeArtwork(Modifier.size(artSize))
            Spacer(Modifier.height(24.dp))
            Text("A little coffee.\nMore Komori.", style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Text("Komori is an open-source project. If it makes your reading day better, a coffee helps support continued development and maintenance.",
                Modifier.widthIn(max = 420.dp), textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))
            Button(onClick = {
                openFailed = false
                try { if (openSupport != null) openSupport() else uri.openUri(SUPPORT_URL) }
                catch (_: IllegalArgumentException) { openFailed = true }
                catch (_: android.content.ActivityNotFoundException) { openFailed = true }
            }, modifier = Modifier.widthIn(max = 340.dp).fillMaxWidth().heightIn(min = 52.dp),
                shape = RoundedCornerShape(16.dp)) {
                Text("Support joyarz on Ko-fi", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
            Text("Opens ko-fi.com/joyarz in your browser.", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            if (openFailed) Text("No browser could open Ko-fi. Visit ko-fi.com/joyarz to support the project.",
                color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            Text("Support is always optional. Reading, reporting bugs, and contributing code help too. Thank you for keeping Komori growing.",
                Modifier.widthIn(max = 400.dp), style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CoffeeArtwork(modifier: Modifier) {
    val accent = MaterialTheme.colorScheme.secondary
    val ink = MaterialTheme.colorScheme.onSurface
    val panel = MaterialTheme.colorScheme.surface
    Box(modifier.clip(RoundedCornerShape(28.dp)).background(panel)
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(28.dp))
        .clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.argb(35, (ink.red * 255).toInt(), (ink.green * 255).toInt(), (ink.blue * 255).toInt())
                textSize = size.width * .20f
                typeface = android.graphics.Typeface.create("serif", android.graphics.Typeface.BOLD_ITALIC)
            }
            drawContext.canvas.nativeCanvas.apply {
                save()
                rotate(-22f, size.width / 2, size.height / 2)
                for (row in -1..5) for (column in -1..2)
                    drawText("coffee", column * size.width * .62f + if (row % 2 == 0) 0f else size.width * .24f,
                        row * size.height * .24f, paint)
                restore()
            }
        }
        Canvas(Modifier.fillMaxSize(.56f).clip(RoundedCornerShape(16.dp)).background(accent)) {
            val w = size.width
            val h = size.height
            val black = Color(0xFF111710)
            val cup = Path().apply {
                moveTo(w * .28f, h * .39f); lineTo(w * .72f, h * .39f)
                lineTo(w * .65f, h * .80f); quadraticTo(w * .5f, h * .87f, w * .35f, h * .80f); close()
            }
            drawPath(cup, Color.White)
            drawPath(cup, black, style = Stroke(w * .035f))
            val lid = Path().apply {
                moveTo(w * .23f, h * .39f); lineTo(w * .77f, h * .39f)
                lineTo(w * .74f, h * .30f); lineTo(w * .67f, h * .30f)
                lineTo(w * .65f, h * .22f); lineTo(w * .35f, h * .22f)
                lineTo(w * .33f, h * .30f); lineTo(w * .26f, h * .30f); close()
            }
            drawPath(lid, accent)
            drawPath(lid, black, style = Stroke(w * .035f))
            drawLine(black, androidx.compose.ui.geometry.Offset(w * .33f, h * .30f), androidx.compose.ui.geometry.Offset(w * .67f, h * .30f), w * .03f)
            val heart = Path().apply {
                moveTo(w * .5f, h * .70f)
                cubicTo(w * .25f, h * .54f, w * .43f, h * .45f, w * .5f, h * .55f)
                cubicTo(w * .57f, h * .45f, w * .75f, h * .54f, w * .5f, h * .70f)
                close()
            }
            drawPath(heart, Color(0xFFFF5E5B))
        }
    }
}
