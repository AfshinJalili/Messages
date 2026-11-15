package com.afsh.messages.ui.thread

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.afsh.messages.ui.Afsh
import com.afsh.messages.ui.components.SELECTION_ENTER_MS
import com.afsh.messages.ui.components.SELECTION_EXIT_MS

/** How far, as a share of its height, the composer sinks while the selection bar rises over it. */
private const val COMPOSER_SINK = 0.5f

/** The whole conversation screen: top bar, timeline (with Manage people over it) and the composer frame. */
@Composable
fun ThreadRoute(model: ThreadScreenModel, snackbarHost: SnackbarHostState, sendOnEnter: Boolean) {
    val ui = model.ui
    // The timeline's lift scrim also dims the header above it.
    val scrim = Afsh.colors.liftScrim
    Column(Modifier.fillMaxSize()) {
        ThreadTopBar(ui, model::onEvent, Modifier.drawWithContent {
            drawContent()
            drawRect(scrim, alpha = model.headerDim)
        })
        Box(Modifier.weight(1f)) {
            ThreadTimeline(ui, snackbarHost, model::onEvent, onDim = model::onDim)
            val people = model.people
            if (people.open) {
                ManagePeoplePanel(
                    people,
                    onAdd = { model.onPeopleEvent(PeopleEvent.Add(it)) },
                    onRemove = { model.onPeopleEvent(PeopleEvent.Remove(it)) },
                    onDone = { model.onPeopleEvent(PeopleEvent.Done) },
                    onAllowContacts = { model.onPeopleEvent(PeopleEvent.AllowContacts) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        ThreadBottom(model, sendOnEnter)
    }
}

/**
 * The selection bar takes the composer's place in the same frame (design view 10): the frame is the composer's
 * height, so the timeline above never moves. The composer stays laid out while it sinks and fades under the rising
 * bar; covered, it says nothing to TalkBack and the bar takes the touches. With no composer at all (Recycle Bin)
 * the bar grows from the bottom edge on its own.
 */
@Composable
private fun ThreadBottom(model: ThreadScreenModel, sendOnEnter: Boolean) {
    val ui = model.ui
    val composer = model.composer
    if (!composer.visible) {
        ThreadSelectionBar(ui, overComposer = false, model::onEvent)
        return
    }
    val sink by animateFloatAsState(
        if (ui.selecting) 1f else 0f,
        tween(if (ui.selecting) SELECTION_ENTER_MS else SELECTION_EXIT_MS),
        label = "composer sink",
    )
    Box(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = 1f - sink
                    translationY = size.height * COMPOSER_SINK * sink
                }
                .then(if (ui.selecting) Modifier.clearAndSetSemantics {} else Modifier)
                // The padding under the composer is its surface too.
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime)),
        ) {
            ThreadComposer(composer, ui.firstName, model.composerText, sendOnEnter, model::onComposerTextChange, model::onComposerEvent)
        }
        ThreadSelectionBar(ui, overComposer = true, model::onEvent, Modifier.matchParentSize())
    }
}
