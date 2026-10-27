package org.fossify.messages.ui.library

import org.fossify.messages.models.Conversation
import org.fossify.messages.ui.components.PageNavigation
import org.fossify.messages.ui.components.TopDestination
import org.fossify.messages.ui.inbox.InboxRow
import java.util.concurrent.TimeUnit

/** Sample content for the Library previews, the fictional names the design file uses. */
@Suppress("MagicNumber") // Sample ids and counts are the content here.
internal object LibraryPreviewData {
    private val dayMillis = TimeUnit.DAYS.toMillis(1)
    private const val ARCHIVED_DAYS_AGO_PARCEL = 5
    private const val ARCHIVED_DAYS_AGO_YOGA = 10
    private const val ARCHIVED_DAYS_AGO_NADIA = 24
    private const val STARRED_DAYS_AGO_OLDEST = 8

    val navigation = PageNavigation(TopDestination.LIBRARY, {})

    private fun now() = System.currentTimeMillis()

    private fun conversation(id: Long, title: String, snippet: String = "", daysAgo: Int = 0) = Conversation(
        threadId = id,
        snippet = snippet,
        date = TimeUnit.MILLISECONDS.toSeconds(now() - daysAgo * dayMillis).toInt(),
        read = true,
        title = title,
        photoUri = "",
        isGroupConversation = false,
        phoneNumber = "+1555000$id",
    )

    val starred
        get() = listOf(
            StarredMessage(1, false, 1, "Mina Farahani", "The address is 24 Willow Street.", now()),
            StarredMessage(2, true, 2, "Dad", "Old family album · 2 photos", now() - dayMillis),
            StarredMessage(
                3, false, 3, "مینا", "سلام، ساعت شش ایستگاه باشیم؟", now() - STARRED_DAYS_AGO_OLDEST * dayMillis,
            ),
        )

    val archived
        get() = listOf(
            conversation(1, "Parcel service", "Delivered · Your package…", ARCHIVED_DAYS_AGO_PARCEL),
            conversation(2, "Yoga studio", "See you next month", ARCHIVED_DAYS_AGO_YOGA),
            conversation(3, "Nadia", "Thank you for the photos", ARCHIVED_DAYS_AGO_NADIA),
        ).map { InboxRow(it, draft = null, pinned = false, muted = false) }

    val recycled
        get() = listOf(
            RecycledConversation(conversation(1, "Mina Farahani"), messageCount = 2, deletedMillis = now()),
            RecycledConversation(conversation(2, "Pharmacy"), messageCount = 1, deletedMillis = now() - dayMillis),
        )
}
