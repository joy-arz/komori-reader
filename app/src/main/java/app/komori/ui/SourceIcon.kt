package app.komori.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.komori.R
import coil.compose.AsyncImage

internal fun sourceIconResource(key: String): Int = when (key) {
    "asura" -> R.drawable.source_asura
    "hive" -> R.drawable.source_hive
    "demonic" -> R.drawable.source_demonic
    "weeb" -> R.drawable.source_weeb
    "atsu", "atsuadult" -> R.drawable.source_atsu
    "mangadex" -> R.drawable.source_mangadex
    "zap" -> R.drawable.source_zap
    "imhentai" -> R.drawable.source_imhentai
    "nhentai" -> R.drawable.source_nhentai
    "manhwa18" -> R.drawable.source_manhwa18
    "manhwa18cc" -> R.drawable.source_manhwa18cc
    else -> error("No icon registered for source $key")
}

/** Decorative beside the source name; selected state and touch targets belong to the parent. */
@Composable
internal fun SourceIcon(key: String, size: Dp = 24.dp) {
    Box(Modifier.size(size).testTag("source-icon-$key").clip(CircleShape)
        .background(Color(0xFF153A20)).border(1.dp, Green, CircleShape).padding(3.dp)) {
        AsyncImage(sourceIconResource(key), contentDescription = null,
            modifier = Modifier.size(size - 6.dp), contentScale = ContentScale.Fit)
    }
}
