package org.fossify.messages.ui.media

import android.os.Bundle
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.fossify.messages.R
import org.fossify.messages.extensions.isVideoMimeType
import org.fossify.messages.ui.OpenLine
import org.fossify.messages.ui.forUserText
import org.fossify.messages.ui.message.MessageFacts
import org.fossify.messages.ui.rememberBitmap

private val ActionIconSize = 21.dp
private val PlayIconSize = 56.dp
private const val ZOOM_IN = 2.5f
private const val MAX_ZOOM = 5f

/** One photo or video in the conversation, with who sent it, when, and the message around it. */
@Immutable
data class MediaItem(
    val uri: String,
    val mimetype: String,
    val filename: String,
    val sender: String,
    val time: String,
    val caption: String,
    val facts: MessageFacts,
) {
    fun toBundle() = Bundle().apply {
        putString(URI, uri)
        putString(MIMETYPE, mimetype)
        putString(FILENAME, filename)
        putString(SENDER, sender)
        putString(TIME, time)
        putString(CAPTION, caption)
        putBundle(FACTS, facts.toBundle())
    }

    companion object {
        private const val URI = "uri"
        private const val MIMETYPE = "mimetype"
        private const val FILENAME = "filename"
        private const val SENDER = "sender"
        private const val TIME = "time"
        private const val CAPTION = "caption"
        private const val FACTS = "facts"

        fun from(bundle: Bundle) = MediaItem(
            uri = bundle.getString(URI).orEmpty(),
            mimetype = bundle.getString(MIMETYPE).orEmpty(),
            filename = bundle.getString(FILENAME).orEmpty(),
            sender = bundle.getString(SENDER).orEmpty(),
            time = bundle.getString(TIME).orEmpty(),
            caption = bundle.getString(CAPTION).orEmpty(),
            facts = MessageFacts.from(bundle.getBundle(FACTS) ?: Bundle()),
        )
    }
}

sealed interface MediaEvent {
    data object Close : MediaEvent
    data class Save(val item: MediaItem) : MediaEvent
    data class Share(val item: MediaItem) : MediaEvent
    data class Details(val item: MediaItem) : MediaEvent
    data class OpenElsewhere(val item: MediaItem) : MediaEvent
}

/**
 * 45 · Media / full screen, always dark (R1-04): close, "1 of 2" and more on top; the media,
 * swiped between the conversation's photos and videos; who sent it and when; Save, Share, Details.
 * Double-tap zooms a photo; while zoomed it pans instead of paging.
 */
@Composable
fun MediaViewerScreen(items: List<MediaItem>, initial: Int, onEvent: (MediaEvent) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val pager = rememberPagerState(initialPage = initial.coerceIn(0, (items.size - 1).coerceAtLeast(0))) { items.size }
    var zoomed by remember { mutableStateOf(false) }
    val current = items.getOrNull(pager.currentPage) ?: return
    Column(
        modifier
            .fillMaxSize()
            .background(colors.surface)
            .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.safeDrawing.only(androidx.compose.foundation.layout.WindowInsetsSides.Horizontal))),
    ) {
        TopControls(pager.currentPage, items.size, current, onEvent)
        HorizontalPager(
            state = pager,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(colors.surface),
            userScrollEnabled = !zoomed,
            key = { items[it].uri },
        ) { page ->
            MediaPage(items[page], onZoomChange = { if (page == pager.currentPage) zoomed = it }, onPlay = { onEvent(MediaEvent.OpenElsewhere(items[page])) })
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(current.sender, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
            Text(current.time, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            if (current.caption.isNotEmpty()) {
                Text(current.caption, style = MaterialTheme.typography.bodyMedium.forUserText(current.caption), color = colors.onSurface, maxLines = 3)
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .background(colors.surfaceContainer)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            MediaAction(R.drawable.ic_lucide_download, stringResource(R.string.save)) { onEvent(MediaEvent.Save(current)) }
            MediaAction(R.drawable.ic_lucide_share_2, stringResource(org.fossify.commons.R.string.share)) { onEvent(MediaEvent.Share(current)) }
            MediaAction(R.drawable.ic_lucide_info, stringResource(R.string.media_details)) { onEvent(MediaEvent.Details(current)) }
        }
    }
}

@Composable
private fun TopControls(page: Int, count: Int, current: MediaItem, onEvent: (MediaEvent) -> Unit) {
    val colors = MaterialTheme.colorScheme
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val close = stringResource(org.fossify.commons.R.string.close)
        IconButton(onClick = { onEvent(MediaEvent.Close) }, modifier = Modifier.semantics { contentDescription = close }) {
            Icon(painterResource(R.drawable.ic_lucide_x), contentDescription = null, tint = colors.onSurface)
        }
        Text(
            stringResource(R.string.media_position, page + 1, count),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = colors.onSurface,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Box {
            val more = stringResource(org.fossify.commons.R.string.more_options)
            IconButton(onClick = { menu = true }, modifier = Modifier.semantics { contentDescription = more }) {
                Icon(painterResource(R.drawable.ic_lucide_ellipsis_vertical), contentDescription = null, tint = colors.onSurface)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = colors.surfaceContainer, shape = MaterialTheme.shapes.medium) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.open_in_another_app), style = MaterialTheme.typography.bodyMedium) },
                    onClick = {
                        menu = false
                        onEvent(MediaEvent.OpenElsewhere(current))
                    },
                )
            }
        }
    }
}

/** A photo fitted to the page, or a video's frame with a play button that opens a player. */
@Composable
private fun MediaPage(item: MediaItem, onZoomChange: (Boolean) -> Unit, onPlay: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val density = LocalDensity.current
        val bitmap = rememberBitmap(item.uri, with(density) { maxWidth.roundToPx() }, with(density) { maxHeight.roundToPx() })
        val video = item.mimetype.isVideoMimeType()
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        fun setScale(value: Float) {
            scale = value
            if (value == 1f) offset = Offset.Zero
            onZoomChange(value > 1f)
        }
        val zoom = if (video) Modifier else Modifier
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { setScale(if (scale > 1f) 1f else ZOOM_IN) })
            }
            .pointerInput(scale > 1f) {
                // Only while zoomed: otherwise the pager takes the horizontal drag.
                if (scale > 1f) {
                    detectTransformGestures { _, pan, gesture, _ ->
                        setScale((scale * gesture).coerceIn(1f, MAX_ZOOM))
                        if (scale > 1f) offset += pan
                    }
                }
            }
        if (bitmap != null) {
            Image(
                bitmap,
                contentDescription = item.filename.ifEmpty { null },
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .then(zoom)
                    .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
            )
        }
        if (video) {
            val play = stringResource(R.string.play_video)
            Icon(
                painterResource(org.fossify.commons.R.drawable.ic_play_outline_vector),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier
                    .size(PlayIconSize)
                    .clickable(role = Role.Button, onClickLabel = play, onClick = onPlay),
            )
        }
    }
}

@Composable
private fun MediaAction(icon: Int, label: String, onClick: () -> Unit) {
    Column(
        Modifier
            .widthIn(min = 90.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = OpenLine.colors.accent, modifier = Modifier.size(ActionIconSize))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}
