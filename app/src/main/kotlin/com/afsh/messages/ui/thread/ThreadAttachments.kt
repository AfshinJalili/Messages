package com.afsh.messages.ui.thread

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.afsh.commons.extensions.formatSize
import com.afsh.messages.R
import com.afsh.messages.extensions.getFileSizeFromUri
import com.afsh.messages.extensions.isImageMimeType
import com.afsh.messages.extensions.isVCardMimeType
import com.afsh.messages.extensions.isVideoMimeType
import com.afsh.messages.helpers.attachmentIcon
import com.afsh.messages.helpers.parseNameFromVCard
import com.afsh.messages.helpers.parseVCardFromUri
import com.afsh.messages.models.Attachment
import com.afsh.messages.ui.Afsh
import com.afsh.messages.ui.components.AfshIcon
import com.afsh.messages.ui.components.ContentText
import com.afsh.messages.ui.rememberBitmap
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

private const val MAX_MEDIA_HEIGHT_RATIO = 3

@Composable
internal fun AttachmentView(attachment: Attachment, onClick: () -> Unit) {
    val mimetype = attachment.mimetype
    // Long press belongs to the bubble, which selects the whole message.
    val modifier = Modifier.clickable(onClickLabel = attachment.filename.ifEmpty { null }, onClick = onClick)
    when {
        mimetype.isImageMimeType() || mimetype.isVideoMimeType() -> ImageAttachment(attachment, modifier)
        mimetype.isVCardMimeType() -> VCardAttachment(attachment, modifier)
        else -> FileAttachment(attachment, modifier)
    }
}

@Composable
private fun ImageAttachment(attachment: Attachment, clickable: Modifier) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val widthPx = with(LocalDensity.current) { maxWidth.roundToPx() }
        val bitmap = rememberBitmap(attachment.uriString, widthPx, widthPx * MAX_MEDIA_HEIGHT_RATIO)
        // Known dimensions reserve the right height before the bitmap arrives, so the list doesn't jump.
        val ratio = when {
            bitmap != null -> bitmap.width.toFloat() / bitmap.height
            attachment.width > 0 && attachment.height > 0 -> attachment.width.toFloat() / attachment.height
            else -> null
        }?.coerceAtLeast(1f / MAX_MEDIA_HEIGHT_RATIO)
        Surface(
            Modifier
                .fillMaxWidth()
                .then(if (ratio != null) Modifier.aspectRatio(ratio) else Modifier.height(180.dp))
                .clip(MaterialTheme.shapes.small)
                .then(clickable),
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (bitmap != null) {
                    Image(bitmap, contentDescription = attachment.filename.ifEmpty { null }, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                }
                if (attachment.mimetype.isVideoMimeType()) {
                    Icon(
                        AfshIcon.Play.painter(),
                        contentDescription = null,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(56.dp),
                    )
                }
            }
        }
    }
}

/** The first contact's name and a "Contact" / "and N others" line; empty until the card is parsed. */
@Composable
internal fun rememberVCardSummary(uri: Uri): Pair<String, String> {
    val context = LocalContext.current
    val unknown = stringResource(com.afsh.commons.R.string.unknown_error_occurred)
    val summary by produceState<Pair<String, Int>?>(null, uri) {
        // Cancellable: the parser calls back on its own thread, possibly after the tile has left.
        value = suspendCancellableCoroutine { continuation ->
            parseVCardFromUri(context, uri) { cards ->
                if (continuation.isActive) {
                    continuation.resume((cards.firstOrNull()?.parseNameFromVCard() ?: unknown) to (cards.size - 1).coerceAtLeast(0))
                }
            }
        }
    }
    val (name, others) = summary ?: return "" to ""
    return name to if (others > 0) pluralStringResource(R.plurals.and_other_contacts, others, others) else stringResource(R.string.contact)
}

@Composable
internal fun rememberFileSize(uri: Uri): String {
    val context = LocalContext.current
    val size by produceState("", uri) {
        value = withContext(Dispatchers.IO) { runCatching { context.getFileSizeFromUri(uri).formatSize() }.getOrDefault("") }
    }
    return size
}

@Composable
private fun VCardAttachment(attachment: Attachment, clickable: Modifier) {
    val (name, subtitle) = rememberVCardSummary(attachment.getUri())
    AttachmentTile(AfshIcon.Person, name, subtitle, clickable)
}

@Composable
private fun FileAttachment(attachment: Attachment, clickable: Modifier) {
    val size = rememberFileSize(attachment.getUri())
    AttachmentTile(attachmentIcon(attachment.mimetype), attachment.filename.ifEmpty { attachment.mimetype }, size, clickable)
}

@Composable
private fun AttachmentTile(icon: AfshIcon, title: String, subtitle: String, clickable: Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(MaterialTheme.shapes.small)
            .then(clickable),
        shape = MaterialTheme.shapes.small,
        color = colors.surfaceContainerHigh,
    ) {
        Row(
            Modifier.padding(Afsh.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Afsh.spacing.md),
        ) {
            Surface(Modifier.size(40.dp), shape = CircleShape, color = Afsh.colors.badge.container, contentColor = Afsh.colors.badge.content) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon.painter(), contentDescription = null, modifier = Modifier.size(22.dp))
                }
            }
            Column(Modifier.weight(1f)) {
                ContentText(title, style = MaterialTheme.typography.labelMedium, color = colors.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (subtitle.isNotEmpty()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}
