package com.afsh.messages.ui

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.afsh.messages.ui.components.AfshIcon
import com.bumptech.glide.Glide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun Avatar(title: String, photoUri: String, isGroup: Boolean, size: Dp = 50.dp) {
    val extras = Afsh.colors
    // Empty URI is the common inbox case (synthetic seed, unknown numbers): skip produceState /
    // Glide entirely so scrolling 2k threads does not start a coroutine per row.
    val photo = if (photoUri.isEmpty()) {
        null
    } else {
        val sizePx = with(LocalDensity.current) { size.roundToPx() }
        rememberBitmap(photoUri, sizePx, sizePx, circle = true)
    }
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
    val avatarColor = remember(title, extras) {
        extras.avatars[Math.floorMod(title.hashCode(), extras.avatars.size)]
    }
    Surface(
        Modifier.size(size),
        shape = CircleShape,
        color = avatarColor,
        contentColor = extras.onAvatar,
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (initials != null && !isGroup) {
                Text(initials, style = MaterialTheme.typography.labelLarge.copy(fontSize = (size.value * 0.34f).sp))
            } else {
                val icon = if (isGroup) AfshIcon.People else AfshIcon.Person
                Icon(icon.painter(), contentDescription = null)
            }
        }
    }
}

internal fun initials(title: String): String? = title.split(' ')
    .filter { it.firstOrNull()?.isLetter() == true }
    .take(2)
    .joinToString("") { it.first().uppercase() }
    .ifEmpty { null }

/**
 * Decoded bitmaps survive row disposal so the inbox scroll does not decode the same URI again.
 * ~8 MB of ARGB bitmaps; keys are uri + size + crop.
 */
private val bitmapCache = object : LruCache<String, ImageBitmap>(8 * 1024 * 1024) {
    override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
}

/** Null until loaded, and when [uri] is empty or fails to decode. Fits inside [widthPx] × [heightPx]. */
@Composable
fun rememberBitmap(uri: String, widthPx: Int, heightPx: Int, circle: Boolean = false): ImageBitmap? {
    val context = LocalContext.current
    val cacheKey = "$uri|$widthPx|$heightPx|$circle"
    val bitmap by produceState<ImageBitmap?>(bitmapCache.get(cacheKey), uri, widthPx, heightPx, circle) {
        if (uri.isEmpty() || widthPx <= 0 || heightPx <= 0) {
            value = null
            return@produceState
        }
        bitmapCache.get(cacheKey)?.let {
            value = it
            return@produceState
        }
        val glide = Glide.with(context.applicationContext)
        val request = glide.asBitmap().load(uri)
        val target = (if (circle) request.circleCrop() else request.fitCenter()).submit(widthPx, heightPx)
        // Glide recycles the bitmap it hands out once the target is cleared, so keep a private copy.
        try {
            val decoded = withContext(Dispatchers.IO) {
                runCatching {
                    target.get().let { it.copy(it.config ?: Bitmap.Config.ARGB_8888, false) }.asImageBitmap()
                }.getOrNull()
            }
            if (decoded != null) bitmapCache.put(cacheKey, decoded)
            value = decoded
        } finally {
            glide.clear(target)
        }
    }
    return bitmap
}
