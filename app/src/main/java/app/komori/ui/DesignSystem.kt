@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package app.komori.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.komori.R

internal val Ink = Color(0xFF000000)
internal val Panel = Color(0xFF0B100D)
internal val Green = Color(0xFF39D353)
internal val Lime = Color(0xFFD5FA39)
internal val Night =
    darkColorScheme(
        primary = Lime,
        onPrimary = Ink,
        primaryContainer = Color(0xFF17351E),
        onPrimaryContainer = Color(0xFFB9F9C2),
        secondary = Green,
        onSecondary = Ink,
        secondaryContainer = Color(0xFF183222),
        onSecondaryContainer = Color(0xFFA8EBB9),
        tertiary = Green,
        background = Ink,
        onBackground = Color(0xFFF2F6EF),
        surface = Panel,
        onSurface = Color(0xFFF2F6EF),
        surfaceVariant = Color(0xFF151C17),
        onSurfaceVariant = Color(0xFFA0AAA2),
        surfaceContainer = Color(0xFF111813),
        surfaceContainerLow = Color(0xFF080C09),
        surfaceContainerHigh = Color(0xFF182019),
        surfaceContainerHighest = Color(0xFF202A23),
        surfaceContainerLowest = Ink,
        outline = Color(0xFF526157),
        outlineVariant = Color(0xFF29352D),
        surfaceTint = Color.Transparent,
    )
internal val Day =
    lightColorScheme(
        primary = Color(0xFF226B32),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFD9F2CC),
        onPrimaryContainer = Color(0xFF153D20),
        secondary = Color(0xFF28743B),
        secondaryContainer = Color(0xFFE2EDE0),
        onSecondaryContainer = Color(0xFF23452D),
        background = Color(0xFFF5F8F2),
        onBackground = Color(0xFF152019),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF152019),
        surfaceVariant = Color(0xFFE8EEE5),
        onSurfaceVariant = Color(0xFF556359),
        surfaceContainer = Color(0xFFE8EEE5),
        outline = Color(0xFF748277),
        outlineVariant = Color(0xFFCBD6CA),
        surfaceTint = Color.Transparent,
    )
private val Outfit =
    FontFamily(
        Font(
            R.font.outfit,
            weight = FontWeight.Normal,
            variationSettings = FontVariation.Settings(FontVariation.weight(400)),
        ),
        Font(
            R.font.outfit,
            weight = FontWeight.Medium,
            variationSettings = FontVariation.Settings(FontVariation.weight(500)),
        ),
        Font(
            R.font.outfit,
            weight = FontWeight.SemiBold,
            variationSettings = FontVariation.Settings(FontVariation.weight(600)),
        ),
    )

private fun type(size: Int, height: Int, weight: FontWeight = FontWeight.Normal) =
    TextStyle(fontFamily = Outfit, fontSize = size.sp, lineHeight = height.sp, fontWeight = weight)

internal val ReaderTypography =
    Typography(
        displayLarge = type(40, 48, FontWeight.Medium),
        displayMedium = type(36, 44),
        displaySmall = type(32, 40),
        headlineLarge = type(30, 38, FontWeight.Medium),
        headlineMedium = type(26, 34, FontWeight.Medium),
        headlineSmall = type(23, 30, FontWeight.Medium),
        titleLarge = type(20, 27, FontWeight.Medium),
        titleMedium = type(16, 22, FontWeight.Medium),
        titleSmall = type(14, 20, FontWeight.Medium),
        bodyLarge = type(16, 24),
        bodyMedium = type(14, 21),
        bodySmall = type(12, 18),
        labelLarge = type(14, 20, FontWeight.Medium),
        labelMedium = type(12, 17, FontWeight.Medium),
        labelSmall = type(11, 15),
    )
internal val ReaderShapes =
    Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = RoundedCornerShape(12.dp),
        medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(22.dp),
        extraLarge = RoundedCornerShape(28.dp),
    )

@Composable
internal fun ActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = Color.Transparent,
        contentColor = if (enabled) Ink else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Row(
            Modifier.background(
                    if (enabled) Brush.horizontalGradient(listOf(Green, Lime))
                    else
                        Brush.horizontalGradient(
                            listOf(
                                MaterialTheme.colorScheme.surfaceVariant,
                                MaterialTheme.colorScheme.surfaceVariant,
                            )
                        )
                )
                .heightIn(min = 48.dp)
                .padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProvideTextStyle(MaterialTheme.typography.labelLarge) { content() }
        }
    }
}

@Composable
internal fun RoundControl(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(onClick, modifier.size(48.dp).clip(CircleShape)) {
        Box(
            Modifier.size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, label, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
internal fun RefreshControl(label: String, refreshing: Boolean, onClick: () -> Unit, enabled: Boolean = true) {
    IconButton(onClick = onClick, enabled = enabled && !refreshing, modifier = Modifier.size(48.dp).clip(CircleShape).semantics { contentDescription = if (refreshing) "$label in progress" else label }) {
        if (refreshing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        else Icon(KomoriIcons.Refresh, null, Modifier.size(18.dp))
    }
}

@Composable
internal fun AppHeading(
    title: String,
    subtitle: String,
    back: (() -> Unit)? = null,
    browse: () -> Unit = {},
    refresh: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (back != null)
            RoundControl(KomoriIcons.ArrowBack, "Back", back, Modifier.padding(end = 8.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            if (subtitle.isNotEmpty())
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
        }
        refresh?.invoke()
        if (back == null) RoundControl(KomoriIcons.Explore, "Browse sources", browse)
    }
}

@Composable
internal fun FloatingNavigation(selected: Int, choose: (Int) -> Unit) {
    val labels = listOf("Library", "Discover", "Authors", "Downloads", "Settings")
    val shortLabels = listOf("Library", "Browse", "Authors", "Queue", "Settings")
    val icons =
        listOf(
            KomoriIcons.CollectionsBookmark,
            KomoriIcons.Explore,
            KomoriIcons.PersonOutline,
            KomoriIcons.Download,
            KomoriIcons.Tune,
        )
    Row(
        Modifier.navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 10.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(32.dp))
            .padding(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        labels.forEachIndexed { index, label ->
            val active = selected == index
            Row(
                Modifier.weight(if (active) 1.9f else 1f)
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(
                        if (active) MaterialTheme.colorScheme.secondaryContainer
                        else Color.Transparent
                    )
                    .selectable(active, role = Role.Tab, onClick = { choose(index) })
                    .semantics { contentDescription = label }
                    .padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    icons[index],
                    null,
                    Modifier.size(20.dp),
                    tint =
                        if (active) MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (active) {
                    Spacer(Modifier.width(5.dp))
                    Text(
                        shortLabels[index],
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
internal fun OutlinePanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}
