package com.afsh.messages.activities

import androidx.compose.runtime.Composable
import com.afsh.messages.extensions.config
import com.afsh.messages.ui.spam.WhatFilterLooksForScreen

/** Plain-language list of Persian marketing words and promo emojis the local filter checks. */
class WhatFilterLooksForActivity : ComposePageActivity() {
    @Composable
    override fun Page() {
        WhatFilterLooksForScreen(
            navigation = settingsNavigation(),
            onBack = ::finish,
            patterns = config.spamPatterns,
        )
    }
}
