package org.fossify.messages.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bumptech.glide.Glide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun Avatar(title: String, photoUri: String, isGroup: Boolean, size: Dp = 50.dp) {
    val extras = OpenLine.colors
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    val photo = rememberBitmap(photoUri, sizePx, sizePx, circle = true)
    if (photo != null) {
        Image(
            photo,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size)
                .clip(CircleShape),
        )
        return
    }
    val initials = remember(title) { initials(title) }
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(extras.avatars[Math.floorMod(title.hashCode(), extras.avatars.size)]),
        contentAlignment = Alignment.Center,
    ) {
        if (initials != null && !isGroup) {
            Text(initials, style = MaterialTheme.typography.labelLarge.copy(fontSize = (size.value * 0.34f).sp), color = extras.onAvatar)
        } else {
            val icon = if (isGroup) {
                org.fossify.commons.R.drawable.ic_groups_outline_vector
            } else {
                org.fossify.commons.R.drawable.ic_person_vector
            }
            Icon(painterResource(icon), contentDescription = null, tint = extras.onAvatar)
        }
    }
}

internal fun initials(title: String): String? = title.split(' ')
    .filter { it.firstOrNull()?.isLetter() == true }
    .take(2)
    .joinToString("") { it.first().uppercase() }
    .ifEmpty { null }

/** Null until loaded, and when [uri] is empty or fails to decode. Fits inside [widthPx] × [heightPx]. */
@Composable
fun rememberBitmap(uri: String, widthPx: Int, heightPx: Int, circle: Boolean = false): ImageBitmap? {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, uri, widthPx, heightPx) {
        // produceState keeps the previous key's value; a removed photo must not linger.
        value = null
        if (uri.isEmpty() || widthPx <= 0 || heightPx <= 0) return@produceState
        val glide = Glide.with(context.applicationContext)
        val request = glide.asBitmap().load(uri)
        val target = (if (circle) request.circleCrop() else request.fitCenter()).submit(widthPx, heightPx)
        // Glide recycles the bitmap it hands out once the target is cleared, so keep a private copy.
        try {
            value = withContext(Dispatchers.IO) {
                runCatching { target.get().let { it.copy(it.config ?: Bitmap.Config.ARGB_8888, false) }.asImageBitmap() }.getOrNull()
            }
        } finally {
            glide.clear(target)
        }
    }
    return bitmap
}
