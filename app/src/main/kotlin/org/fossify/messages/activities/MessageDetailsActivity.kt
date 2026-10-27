package org.fossify.messages.activities

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import org.fossify.messages.ui.message.MessageDetailsScreen
import org.fossify.messages.ui.message.MessageFacts

/** 13 · Message / details, for the facts the thread worked out. */
class MessageDetailsActivity : ComposePageActivity() {
    private val facts by lazy { MessageFacts.from(intent.getBundleExtra(EXTRA_FACTS) ?: android.os.Bundle()) }

    @Composable
    override fun Page() = MessageDetailsScreen(facts, onBack = ::finish)

    companion object {
        private const val EXTRA_FACTS = "facts"

        fun intent(context: Context, facts: MessageFacts): Intent =
            Intent(context, MessageDetailsActivity::class.java).putExtra(EXTRA_FACTS, facts.toBundle())
    }
}
