package com.afsh.messages

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText

/** A primary navigation item (bar or rail) by its role, never by its position on screen. */
fun isNavigationItem(): SemanticsMatcher = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

/** The navigation item for [label], found by its tab role and its text. */
fun isNavigationItem(label: String): SemanticsMatcher = isNavigationItem() and hasText(label)

