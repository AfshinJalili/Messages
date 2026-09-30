package org.fossify.messages.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.fossify.messages.R
import org.fossify.messages.ui.OpenLine
import org.fossify.messages.ui.rememberBitmap

@Composable
fun Avatar(title: String, photoUri: String, isGroup: Boolean, size: Dp = 50.dp) {
    val extras = OpenLine.colors
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    val photo = rememberBitmap(photoUri, sizePx, sizePx, circle = true)
    if (photo != null) {
        Image(
            photo,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size)
                .clip(CircleShape),
        )
        return
    }
    val initials = remember(title) { initials(title) }
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(extras.avatars[Math.floorMod(title.hashCode(), extras.avatars.size)]),
        contentAlignment = Alignment.Center,
    ) {
        if (initials != null && !isGroup) {
            Text(initials, style = MaterialTheme.typography.labelLarge.copy(fontSize = (size.value * 0.34f).sp), color = extras.onAvatar)
        } else {
            val icon = if (isGroup) {
                R.drawable.ic_ol_users
            } else {
                R.drawable.ic_ol_user
            }
            Icon(painterResource(icon), contentDescription = null, tint = extras.onAvatar)
        }
    }
}

internal fun initials(title: String): String? = title.split(' ')
    .filter { it.firstOrNull()?.isLetter() == true }
    .take(2)
    .joinToString("") { it.first().uppercase() }
    .ifEmpty { null }
