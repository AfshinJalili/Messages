package org.fossify.messages.ui.firstrun

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.fossify.messages.R
import org.fossify.messages.ui.OpenLine
import org.fossify.messages.ui.components.OpenLineButton
import org.fossify.messages.ui.components.PageScaffold

private val MotifIconSize = 46.dp
private val PermissionIconSize = 25.dp

/** 32, then 33: becoming the SMS app, then the optional permissions. */
enum class FirstRunStep { DEFAULT_APP, PERMISSIONS }

sealed interface FirstRunEvent {
    data object BecomeDefault : FirstRunEvent
    data object Continue : FirstRunEvent
    data object Back : FirstRunEvent
}

/** 32 · First run / default SMS and 33 · First run / permissions. Both lead into Android's own prompts. */
@Composable
fun FirstRunScreen(step: FirstRunStep, onEvent: (FirstRunEvent) -> Unit, modifier: Modifier = Modifier) {
    BackHandler { onEvent(FirstRunEvent.Back) }
    when (step) {
        FirstRunStep.DEFAULT_APP -> DefaultAppPage(onEvent, modifier)
        FirstRunStep.PERMISSIONS -> PermissionsPage(onEvent, modifier)
    }
}

@Composable
private fun DefaultAppPage(onEvent: (FirstRunEvent) -> Unit, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    PageScaffold(
        title = stringResource(R.string.welcome_title),
        subtitle = stringResource(R.string.welcome_subtitle),
        modifier = modifier,
    ) {
        PageColumn {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(colors.secondaryContainer)
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(painterResource(R.drawable.ic_lucide_messages_square), contentDescription = null, tint = colors.primary, modifier = Modifier.size(MotifIconSize))
                Text(
                    stringResource(R.string.welcome_statement),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = MaterialTheme.typography.headlineSmall.fontSize,
                        fontWeight = FontWeight.ExtraBold,
                    ),
                    color = colors.onSecondaryContainer,
                    modifier = Modifier.semantics { heading() },
                )
                Text(stringResource(R.string.welcome_text), style = MaterialTheme.typography.bodyMedium, color = OpenLine.colors.onSecondaryContainerVariant)
            }
            OpenLineButton(
                stringResource(R.string.set_as_default_sms_app),
                onClick = { onEvent(FirstRunEvent.BecomeDefault) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.why_default_sms_app), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun PermissionsPage(onEvent: (FirstRunEvent) -> Unit, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    PageScaffold(
        title = stringResource(R.string.make_it_work),
        subtitle = stringResource(R.string.make_it_work_subtitle),
        onBack = { onEvent(FirstRunEvent.Back) },
        modifier = modifier,
    ) {
        PageColumn {
            PermissionCard(R.drawable.ic_lucide_contact, stringResource(R.string.contacts), stringResource(R.string.contacts_permission_text))
            PermissionCard(
                R.drawable.ic_lucide_bell,
                stringResource(org.fossify.commons.R.string.notifications),
                stringResource(R.string.notifications_permission_text),
            )
            OpenLineButton(
                stringResource(R.string.continue_action),
                onClick = { onEvent(FirstRunEvent.Continue) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.permissions_later), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun PermissionCard(icon: Int, title: String, detail: String) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(colors.surfaceContainer)
            .semantics(mergeDescendants = true) {}
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = colors.primary, modifier = Modifier.size(PermissionIconSize))
        Text(title, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold), color = colors.onSurface)
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
    }
}

@Composable
private fun PageColumn(content: @Composable () -> Unit) {
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }
}
