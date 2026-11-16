package com.afsh.messages.ui.applock

import android.app.Activity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.afsh.messages.R
import com.afsh.messages.ui.Afsh
import com.afsh.messages.ui.components.AfshDialog
import kotlinx.coroutines.delay
import org.fossify.commons.extensions.isRPlus
import org.fossify.commons.helpers.MINIMUM_PIN_LENGTH
import org.fossify.commons.helpers.PROTECTION_FINGERPRINT
import org.fossify.commons.helpers.PROTECTION_PATTERN
import org.fossify.commons.helpers.PROTECTION_PIN
import java.math.BigInteger
import java.security.MessageDigest

/**
 * Dialog to set up or verify app lock (PIN, pattern, or biometric). When [requiredHash] is empty,
 * sets up a new lock; otherwise verifies against it. [showTabIndex] picks the initial tab (or shows
 * all when -1). [onResult] receives (hash, type, success).
 */
@Composable
fun SecuritySetupDialog(
    requiredHash: String,
    showTabIndex: Int,
    onDismiss: () -> Unit,
    onResult: (hash: String, type: Int, success: Boolean) -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val biometricAvailable = remember {
        val manager = BiometricManager.from(context)
        manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
    }

    val tabs = buildList {
        add(PROTECTION_PATTERN to stringResource(R.string.pattern))
        add(PROTECTION_PIN to stringResource(R.string.pin))
        if (biometricAvailable) {
            add(PROTECTION_FINGERPRINT to if (context.isRPlus()) stringResource(R.string.biometrics) else stringResource(R.string.fingerprint))
        }
    }

    val initialTab = if (showTabIndex >= 0 && showTabIndex < tabs.size) showTabIndex else 0
    var selectedTab by remember { mutableIntStateOf(initialTab) }
    val (selectedType, _) = tabs[selectedTab]

    val showAllTabs = showTabIndex == -1

    AfshDialog(
        title = stringResource(if (requiredHash.isEmpty()) R.string.setup_app_lock else R.string.app_lock),
        onDismiss = { onResult("", 0, false); onDismiss() },
        dismissLabel = stringResource(android.R.string.cancel),
    ) {
        Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Afsh.spacing.lg)) {
            if (showAllTabs) {
                TabRow(selectedTab) {
                    tabs.forEachIndexed { index, (_, label) ->
                        Tab(selectedTab == index, onClick = { selectedTab = index }, text = { Text(label) })
                    }
                }
            }

            when (selectedType) {
                PROTECTION_PIN -> PinSecurityTab(requiredHash, onResult, onDismiss)
                PROTECTION_PATTERN -> PatternSecurityTab(requiredHash, onResult, onDismiss)
                PROTECTION_FINGERPRINT -> BiometricSecurityTab(activity, requiredHash, onResult, onDismiss)
            }
        }
    }
}

@Composable
private fun PinSecurityTab(
    requiredHash: String,
    onResult: (String, Int, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var computedHash by remember { mutableStateOf(requiredHash) }
    var title by remember { mutableStateOf(context.getString(R.string.enter_pin)) }
    var wrongAttempt by remember { mutableStateOf(false) }
    val lockout = rememberLockout()

    fun hashPin(value: String): String {
        val md = MessageDigest.getInstance("SHA-1")
        md.update(value.toByteArray())
        val digest = md.digest()
        val bigInt = BigInteger(1, digest)
        return String.format("%0${digest.size * 2}x", bigInt).lowercase()
    }

    fun onConfirm() {
        if (lockout.isLockedOut) return
        val newHash = hashPin(pin)
        when {
            pin.isEmpty() -> return
            computedHash.isEmpty() && pin.length < MINIMUM_PIN_LENGTH -> {
                pin = ""
                title = context.getString(R.string.pin_must_be_4_digits_long)
            }
            computedHash.isEmpty() -> {
                computedHash = newHash
                pin = ""
                title = context.getString(R.string.repeat_pin)
            }
            computedHash == newHash -> {
                lockout.onCorrect()
                onResult(computedHash, PROTECTION_PIN, true)
                onDismiss()
            }
            else -> {
                pin = ""
                wrongAttempt = true
                title = context.getString(R.string.wrong_pin)
                lockout.onIncorrect()
                if (requiredHash.isEmpty()) computedHash = ""
            }
        }
    }

    LaunchedEffect(wrongAttempt) {
        if (wrongAttempt) {
            delay(1000)
            wrongAttempt = false
            title = context.getString(R.string.enter_pin)
        }
    }

    LockoutTitle(title, lockout.countdown, lockout.isLockedOut)
    PinInput(pin, { if (it.length <= 10) pin = it }, ::onConfirm, !lockout.isLockedOut)
}

@Composable
private fun PatternSecurityTab(
    requiredHash: String,
    onResult: (String, Int, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var computedHash by remember { mutableStateOf(requiredHash) }
    var title by remember { mutableStateOf(context.getString(R.string.insert_pattern)) }
    var patternState by remember { mutableStateOf(PatternState.Default) }
    val lockout = rememberLockout()

    fun onPatternComplete(newHash: String) {
        if (lockout.isLockedOut) return
        when {
            computedHash.isEmpty() -> {
                computedHash = newHash
                title = context.getString(R.string.repeat_pattern)
                patternState = PatternState.Default
            }
            computedHash == newHash -> {
                patternState = PatternState.Correct
                lockout.onCorrect()
                onResult(computedHash, PROTECTION_PATTERN, true)
                onDismiss()
            }
            else -> {
                patternState = PatternState.Wrong
                lockout.onIncorrect()
                title = context.getString(R.string.wrong_pattern)
                if (requiredHash.isEmpty()) computedHash = ""
            }
        }
    }

    LaunchedEffect(patternState) {
        if (patternState == PatternState.Wrong) {
            delay(300)
            patternState = PatternState.Default
            title = context.getString(if (requiredHash.isEmpty()) R.string.insert_pattern else R.string.insert_pattern)
        }
    }

    LockoutTitle(title, lockout.countdown, lockout.isLockedOut)
    PatternInput(::onPatternComplete, patternState, !lockout.isLockedOut)
}

@Composable
private fun BiometricSecurityTab(
    activity: FragmentActivity?,
    requiredHash: String,
    onResult: (String, Int, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current

    DisposableEffect(Unit) {
        if (activity != null) {
            val executor = ContextCompat.getMainExecutor(context)
            val callback = object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onResult(requiredHash.ifEmpty { "biometric" }, PROTECTION_FINGERPRINT, true)
                    onDismiss()
                }
                override fun onAuthenticationFailed() {}
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED && errorCode != BiometricPrompt.ERROR_CANCELED) {
                        onResult("", 0, false)
                    }
                }
            }
            val prompt = BiometricPrompt(activity, executor, callback)
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle(context.getString(R.string.app_lock))
                .setNegativeButtonText(context.getString(android.R.string.cancel))
                .setAllowedAuthenticators(BiometricPrompt.AUTHENTICATOR_BIOMETRIC_STRONG)
                .build()
            prompt.authenticate(info)
        }
        onDispose {}
    }

    Text(stringResource(if (context.isRPlus()) R.string.biometric_setup else R.string.fingerprint_setup))
}

@Composable
private fun LockoutTitle(title: String, countdown: Int, isLockedOut: Boolean) {
    val context = LocalContext.current
    val displayTitle = if (isLockedOut && countdown > 0) {
        context.getString(R.string.too_many_incorrect_attempts, countdown)
    } else {
        title
    }
    Text(displayTitle, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth())
}
