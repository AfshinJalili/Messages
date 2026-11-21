package com.afsh.messages.ui.components

import androidx.annotation.AnyRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource

/**
 * Resolves an action label that may be either a plain string or a `<plurals>` resource.
 * Count-sensitive labels (sender, number, message, conversation) must use plurals and pass
 * the selection size so English and Persian agree with the count.
 */
@Composable
fun actionLabel(@AnyRes id: Int, count: Int, quantity: Boolean): String =
    if (quantity) pluralStringResource(id, count) else stringResource(id)
