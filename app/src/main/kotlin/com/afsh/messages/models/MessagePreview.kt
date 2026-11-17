package com.afsh.messages.models

/** Latest visible message body and date for denormalized conversation previews. */
data class MessagePreview(
    val body: String,
    val date: Int,
)
