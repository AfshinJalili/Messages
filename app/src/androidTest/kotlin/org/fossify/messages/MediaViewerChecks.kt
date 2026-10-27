package org.fossify.messages

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.fossify.messages.ui.OpenLineTheme
import org.fossify.messages.ui.media.MediaEvent
import org.fossify.messages.ui.media.MediaItem
import org.fossify.messages.ui.media.MediaViewerScreen
import org.fossify.messages.ui.message.DeliveryMark
import org.fossify.messages.ui.message.MessageFacts
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** 45 on two sample photos: paging, the actions, and captures (always dark; LTR and Persian). */
@RunWith(AndroidJUnit4::class)
class MediaViewerChecks {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()
    private fun text(id: Int, vararg args: Any) = compose.activity.getString(id, *args)

    /** A gradient photo in app cache, so the viewer has a real image to fit. */
    private fun photo(name: String, from: Int, to: Int): String {
        val bitmap = Bitmap.createBitmap(1200, 900, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawRect(0f, 0f, 1200f, 900f, Paint().apply { shader = LinearGradient(0f, 0f, 1200f, 900f, from, to, Shader.TileMode.CLAMP) })
        val file = File(compose.activity.cacheDir, name)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return file.toURI().toString()
    }

    private fun items() = listOf(
        MediaItem(photo("album.png", 0xFF1F5A4E.toInt(), 0xFFDDF26B.toInt()), "image/png", "album.png", "Dad", "Yesterday · 18:05", "Old family album",
            MessageFacts("Old family album", "18:05", DeliveryMark.NONE, listOf("Type" to "MMS"))),
        MediaItem(photo("park.png", 0xFF3B3F8F.toInt(), 0xFFB9BEE3.toInt()), "image/png", "park.png", "You", "Today · 09:12", "",
            MessageFacts("", "09:12", DeliveryMark.SENT, listOf("Type" to "MMS"))),
    )

    @Test
    fun pagesThroughTheConversationMediaAndActsOnTheCurrentOne() {
        val events = mutableListOf<MediaEvent>()
        val media = items()
        compose.setContent { OpenLineTheme(dark = true) { MediaViewerScreen(media, 0, { events += it }) } }
        compose.onNodeWithText(text(R.string.media_position, 1, 2)).assertExists()
        compose.onNodeWithText("Old family album").assertExists()
        compose.onRoot().performTouchInput { swipeLeft() }
        compose.onNodeWithText(text(R.string.media_position, 2, 2)).assertExists()
        compose.onNodeWithText(text(R.string.save)).performClick()
        compose.onNodeWithText(text(R.string.media_details)).performClick()
        check(events == listOf(MediaEvent.Save(media[1]), MediaEvent.Details(media[1]))) { "Events $events" }
    }

    @Test
    fun mediaViewerIsCaptured() {
        var variant by mutableStateOf(DesignVariant.LIGHT)
        val media = items()
        compose.setContent {
            DesignVariantContent(variant) { OpenLineTheme(dark = true) { MediaViewerScreen(media, 0, {}) } }
        }
        listOf(DesignVariant.LIGHT, DesignVariant.PERSIAN).forEach {
            variant = it
            compose.waitForIdle()
            Screenshots.capture("45-media" + it.suffix)
        }
    }
}
