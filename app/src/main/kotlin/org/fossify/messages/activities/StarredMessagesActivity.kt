package org.fossify.messages.activities

import android.content.Intent
import android.text.TextUtils
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.messages.extensions.config
import org.fossify.messages.extensions.messagesDB
import org.fossify.messages.helpers.SEARCHED_MESSAGE_ID
import org.fossify.messages.helpers.SEARCHED_MESSAGE_IS_MMS
import org.fossify.messages.helpers.THREAD_ID
import org.fossify.messages.helpers.THREAD_TITLE
import org.fossify.messages.models.Message
import org.fossify.messages.ui.library.StarredMessage
import org.fossify.messages.ui.library.StarredScreen
import org.fossify.messages.ui.library.StarredUiState

/** 18 · Starred: every starred message, newest first, each opening at its place in the thread. */
class StarredMessagesActivity : ComposePageActivity() {
    private var state by mutableStateOf(StarredUiState())

    @Composable
    override fun Page() {
        StarredScreen(state, libraryNavigation(), onBack = ::finish, onOpen = ::openMessage)
    }

    override fun onResume() {
        super.onResume()
        loadStarredMessages()
    }

    private fun loadStarredMessages() {
        ensureBackgroundThread {
            val messages = config.starredMessages.mapNotNull { it.removePrefix("mms:").toLongOrNull() }
                .distinct()
                .chunked(ROOM_MAX_BIND_ARGS)
                .flatMap { messagesDB.getMessagesWithIds(it) }
                .filter { config.isMessageStarred(it.id, it.isMMS) }
                .sortedByDescending { it.date }
                .map { it.toStarred() }
            runOnUiThread { state = StarredUiState(loading = false, messages = messages) }
        }
    }

    private fun Message.toStarred() = StarredMessage(
        messageId = id,
        isMms = isMMS,
        threadId = threadId,
        sender = senderName.ifEmpty { TextUtils.join(", ", participants.map { it.name }) },
        body = body,
        millis = millis(),
    )

    private fun openMessage(message: StarredMessage) {
        Intent(this, ThreadActivity::class.java).apply {
            putExtra(THREAD_ID, message.threadId)
            putExtra(THREAD_TITLE, message.sender)
            putExtra(SEARCHED_MESSAGE_ID, message.messageId)
            putExtra(SEARCHED_MESSAGE_IS_MMS, message.isMms)
            startActivity(this)
        }
    }

    private companion object {
        // SQLite caps bound parameters at 999.
        const val ROOM_MAX_BIND_ARGS = 900
    }
}
