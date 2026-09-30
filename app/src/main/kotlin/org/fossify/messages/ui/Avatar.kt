package org.fossify.messages.ui

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.bumptech.glide.Glide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
