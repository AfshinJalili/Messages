package com.afsh.messages

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp

/** A node with the TalkBack [role]: button, switch, tab and so on. */
fun hasRole(role: Role): SemanticsMatcher = SemanticsMatcher.expectValue(SemanticsProperties.Role, role)

/** A primary navigation item (bar or rail) by its role, never by its position on screen. */
fun isNavigationItem(): SemanticsMatcher = hasRole(Role.Tab)

/** The navigation item for [label], found by its tab role and its text. */
fun isNavigationItem(label: String): SemanticsMatcher = isNavigationItem() and hasText(label)

/** The laid-out text of a text node, for line counts, ellipsis and glyph positions. */
fun SemanticsNodeInteraction.textLayout(): TextLayoutResult {
    val layouts = mutableListOf<TextLayoutResult>()
    checkNotNull(fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action) { "Not a text node" }.invoke(layouts)
    return layouts.single()
}

/** Whether this rectangle lies within [outer], with half a dp of slack for rounding. */
infix fun DpRect.inside(outer: DpRect): Boolean {
    val slack = 0.5.dp
    return left >= outer.left - slack && top >= outer.top - slack && right <= outer.right + slack && bottom <= outer.bottom + slack
}
