package org.fossify.messages.activities

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import org.fossify.commons.extensions.copyToClipboard
import org.fossify.messages.ui.message.SelectTextScreen

/** 44 · Message / select text. [EXTRA_SENDER] is the "Mina Farahani · 12:41" line above the text. */
class SelectTextActivity : ComposePageActivity() {
    private val sender by lazy { intent.getStringExtra(EXTRA_SENDER).orEmpty() }
    private val body by lazy { intent.getStringExtra(EXTRA_BODY).orEmpty() }

    @Composable
    override fun Page() = SelectTextScreen(sender, body, onBack = ::finish, onCopy = ::copyToClipboard)

    companion object {
        private const val EXTRA_SENDER = "sender"
        private const val EXTRA_BODY = "body"

        fun intent(context: Context, sender: String, body: String): Intent =
            Intent(context, SelectTextActivity::class.java).putExtra(EXTRA_SENDER, sender).putExtra(EXTRA_BODY, body)
    }
}
