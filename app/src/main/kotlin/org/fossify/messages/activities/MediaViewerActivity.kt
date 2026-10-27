package org.fossify.messages.activities

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.core.content.IntentCompat
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import org.fossify.commons.extensions.getMyFileUri
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.extensions.toast
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.messages.R
import org.fossify.messages.extensions.launchViewIntent
import org.fossify.messages.ui.OpenLineTheme
import org.fossify.messages.ui.media.MediaEvent
import org.fossify.messages.ui.media.MediaItem
import org.fossify.messages.ui.media.MediaViewerScreen
import org.fossify.messages.ui.openLineTextScale
import java.io.File

/** 45 · Media / full screen. Always dark (R1-04), whatever the app theme. */
class MediaViewerActivity : ComposePageActivity() {
    private val items by lazy {
        IntentCompat.getParcelableArrayListExtra(intent, EXTRA_ITEMS, Bundle::class.java).orEmpty().map(MediaItem::from)
    }
    private val initial by lazy { intent.getIntExtra(EXTRA_INITIAL, 0) }
    private var saving: MediaItem? = null

    private val saveTo = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val target = result.data?.data
        val item = saving
        saving = null
        if (result.resultCode == Activity.RESULT_OK && target != null && item != null) copy(item, target)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (items.isEmpty()) finish()
    }

    override fun onResume() {
        super.onResume()
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars = false
    }

    @Composable
    override fun Page() = OpenLineTheme(dark = true, textScale = openLineTextScale()) {
        MediaViewerScreen(items, initial, ::onEvent)
    }

    private fun onEvent(event: MediaEvent) {
        when (event) {
            MediaEvent.Close -> finish()
            is MediaEvent.Save -> {
                saving = event.item
                saveTo.launch(
                    Intent(Intent.ACTION_CREATE_DOCUMENT)
                        .addCategory(Intent.CATEGORY_OPENABLE)
                        .setType(event.item.mimetype)
                        .putExtra(Intent.EXTRA_TITLE, event.item.fileName()),
                )
            }
            is MediaEvent.Share -> share(event.item)
            is MediaEvent.Details -> startActivity(MessageDetailsActivity.intent(this, event.item.facts))
            is MediaEvent.OpenElsewhere -> launchViewIntent(event.item.uri.toUri(), event.item.mimetype, event.item.filename)
        }
    }

    private fun copy(item: MediaItem, target: Uri) = ensureBackgroundThread {
        try {
            contentResolver.openInputStream(item.uri.toUri()).use { input ->
                contentResolver.openOutputStream(target).use { output -> checkNotNull(input).copyTo(checkNotNull(output)) }
            }
            runOnUiThread { toast(R.string.media_saved) }
        } catch (e: Exception) {
            runOnUiThread { showErrorToast(e) }
        }
    }

    /** MMS parts are only readable with SMS access, so another app gets a copy through our FileProvider. */
    private fun share(item: MediaItem) = ensureBackgroundThread {
        try {
            val dir = File(cacheDir, SHARED_DIR).apply { mkdirs() }
            val file = File(dir, item.fileName())
            contentResolver.openInputStream(item.uri.toUri()).use { input -> file.outputStream().use { checkNotNull(input).copyTo(it) } }
            val send = Intent(Intent.ACTION_SEND)
                .setType(item.mimetype)
                .putExtra(Intent.EXTRA_STREAM, getMyFileUri(file))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            runOnUiThread { startActivity(Intent.createChooser(send, null)) }
        } catch (e: Exception) {
            runOnUiThread { showErrorToast(e) }
        }
    }

    private fun MediaItem.fileName() = filename.ifEmpty { uri.substringAfterLast('/') }.replace(File.separatorChar, '_')

    companion object {
        private const val EXTRA_ITEMS = "items"
        private const val EXTRA_INITIAL = "initial"
        // Under cache/attachments/, which provider_paths.xml shares.
        private const val SHARED_DIR = "attachments/shared"

        fun intent(context: Context, items: List<MediaItem>, initial: Int): Intent =
            Intent(context, MediaViewerActivity::class.java)
                .putParcelableArrayListExtra(EXTRA_ITEMS, ArrayList(items.map { it.toBundle() }))
                .putExtra(EXTRA_INITIAL, initial)
    }
}
