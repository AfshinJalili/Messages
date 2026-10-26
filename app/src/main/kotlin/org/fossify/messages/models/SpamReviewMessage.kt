package org.fossify.messages.models

import androidx.compose.runtime.Immutable

/** One spam SMS as the Spam review lists it. [date] is in milliseconds. */
@Immutable
data class SpamReviewMessage(
    val id: Long,
    val threadId: Long,
    val address: String,
    val sender: String,
    val photoUri: String,
    val body: String,
    val date: Long,
    val read: Boolean,
    val reason: Int,
)
