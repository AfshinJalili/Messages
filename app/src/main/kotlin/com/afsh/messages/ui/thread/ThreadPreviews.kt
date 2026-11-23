package com.afsh.messages.ui.thread

import android.content.res.Configuration
import android.provider.Telephony
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.afsh.commons.models.SimpleContact
import com.afsh.messages.models.Message
import com.afsh.messages.models.MessageAttachment
import com.afsh.messages.models.ThreadItem
import com.afsh.messages.ui.AfshTheme

private fun previewMessage(id: Long, body: String, incoming: Boolean, minutesAgo: Int, read: Boolean = true, type: Int? = null) = Message(
    id = id,
    body = body,
    type = type ?: if (incoming) Telephony.Sms.MESSAGE_TYPE_INBOX else Telephony.Sms.MESSAGE_TYPE_SENT,
    status = Telephony.Sms.STATUS_COMPLETE,
    participants = ArrayList<SimpleContact>(),
    date = ((System.currentTimeMillis() / 1000) - minutesAgo * 60).toInt(),
    read = read,
    threadId = 1,
    isMMS = false,
    attachment = null as MessageAttachment?,
    senderPhoneNumber = if (incoming) "+989120000000" else "",
    senderName = if (incoming) "Mina Farahani" else "",
    senderPhotoUri = "",
    subscriptionId = 1,
)

private val previewState = ThreadUiState(
    header = ThreadHeaderState("Mina Farahani", "+98 912 000 0000", canDial = true, actions = ThreadMenuAction.entries),
    items = listOf(
        ThreadItem.ThreadDateTime(((System.currentTimeMillis() / 1000) - 3 * 3600).toInt()),
        previewMessage(1, "Morning! I’m at the station.", incoming = true, minutesAgo = 180),
        previewMessage(2, "I’ll be there in ten minutes.", incoming = false, minutesAgo = 170),
        previewMessage(3, "Running late, sorry", incoming = false, minutesAgo = 100, type = Telephony.Sms.MESSAGE_TYPE_FAILED),
        ThreadItem.ThreadUnreadSeparator,
        previewMessage(4, "Perfect. The train leaves at six. Code 482913, see https://example.com", incoming = true, minutesAgo = 5, read = false),
    ),
)

@Composable
private fun ThreadPreviewContent(state: ThreadUiState, dark: Boolean) {
    AfshTheme(dark = dark) {
        Column(Modifier.fillMaxSize()) {
            ThreadTopBar(state, {})
            ThreadTimeline(state, remember { SnackbarHostState() }, {}, Modifier.weight(1f))
        }
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun ThreadPreview() = ThreadPreviewContent(previewState, dark = false)

@Preview(widthDp = 390, heightDp = 844, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ThreadDarkPreview() = ThreadPreviewContent(previewState, dark = true)

@Preview(widthDp = 390, heightDp = 844, locale = "fa")
@Composable
private fun ThreadRtlPreview() = ThreadPreviewContent(previewState, dark = false)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun ThreadSelectionPreview() = ThreadPreviewContent(previewState.copy(selected = setOf(previewMessage(2, "", false, 0).getStableId())), dark = false)

@Preview(widthDp = 844, heightDp = 390)
@Composable
private fun ThreadLandscapePreview() = ThreadPreviewContent(previewState, dark = false)
