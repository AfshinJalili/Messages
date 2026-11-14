package com.afsh.messages

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText

/** The content of the AfshSheet named [title] (its TalkBack pane title). */
fun isSheet(title: String) = SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, title)

/** Any open AfshSheet. */
fun isAnySheet() = SemanticsMatcher.keyIsDefined(SemanticsProperties.PaneTitle)

/** A tappable sheet row labelled [label], inside the sheet named [title] or, without one, any sheet. */
fun sheetAction(label: String, title: String? = null) =
    hasText(label) and hasClickAction() and hasAnyAncestor(title?.let(::isSheet) ?: isAnySheet())
