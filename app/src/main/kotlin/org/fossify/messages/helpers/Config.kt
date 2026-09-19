package org.fossify.messages.helpers

import android.content.Context
import androidx.core.content.ContextCompat
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.helpers.ACCENT_COLOR
import org.fossify.commons.helpers.BACKGROUND_COLOR
import org.fossify.commons.helpers.BaseConfig
import org.fossify.commons.helpers.IS_SYSTEM_THEME_ENABLED
import org.fossify.commons.helpers.PRIMARY_COLOR
import org.fossify.commons.helpers.TEXT_COLOR
import org.fossify.messages.R
import org.fossify.messages.extensions.getDefaultKeyboardHeight
import org.fossify.messages.models.Conversation

class Config(context: Context) : BaseConfig(context) {
    companion object {
        fun newInstance(context: Context) = Config(context)
    }

    fun applyCobaltDefaults() {
        if (prefs.getBoolean("cobalt_defaults_applied", false)) return
        val background = context.getProperBackgroundColor()
        val cobalt = context.cobaltColorFor(background)
        val legacyGreen = ContextCompat.getColor(context, org.fossify.commons.R.color.md_green_900)
        if (!prefs.contains(PRIMARY_COLOR) || primaryColor == legacyGreen) primaryColor = cobalt
        if (!prefs.contains(ACCENT_COLOR) || accentColor == legacyGreen) accentColor = cobalt
        // Adopt the brand by default; keep explicit theme and custom-color choices.
        if (!prefs.contains(IS_SYSTEM_THEME_ENABLED)) {
            val light = cobalt == ContextCompat.getColor(context, R.color.brand_cobalt)
            if (!prefs.contains(BACKGROUND_COLOR)) {
                backgroundColor = ContextCompat.getColor(context, if (light) R.color.surface_light else R.color.surface_dark)
            }
            if (!prefs.contains(TEXT_COLOR)) {
                textColor = ContextCompat.getColor(context, if (light) R.color.on_surface_light else R.color.on_surface_dark)
            }
            isSystemThemeEnabled = false
        }
        prefs.edit().putBoolean("cobalt_defaults_applied", true).apply()
    }

    fun saveUseSIMIdAtNumber(number: String, SIMId: Int) {
        prefs.edit().putInt(USE_SIM_ID_PREFIX + number, SIMId).apply()
    }

    fun getUseSIMIdAtNumber(number: String) = prefs.getInt(USE_SIM_ID_PREFIX + number, 0)

    var showCharacterCounter: Boolean
        get() = prefs.getBoolean(SHOW_CHARACTER_COUNTER, false)
        set(showCharacterCounter) = prefs.edit()
            .putBoolean(SHOW_CHARACTER_COUNTER, showCharacterCounter).apply()

    var useSimpleCharacters: Boolean
        get() = prefs.getBoolean(USE_SIMPLE_CHARACTERS, false)
        set(useSimpleCharacters) = prefs.edit()
            .putBoolean(USE_SIMPLE_CHARACTERS, useSimpleCharacters).apply()

    var sendOnEnter: Boolean
        get() = prefs.getBoolean(SEND_ON_ENTER, false)
        set(sendOnEnter) = prefs.edit().putBoolean(SEND_ON_ENTER, sendOnEnter).apply()

    var enableDeliveryReports: Boolean
        get() = prefs.getBoolean(ENABLE_DELIVERY_REPORTS, false)
        set(enableDeliveryReports) = prefs.edit()
            .putBoolean(ENABLE_DELIVERY_REPORTS, enableDeliveryReports).apply()

    var sendLongMessageMMS: Boolean
        get() = prefs.getBoolean(SEND_LONG_MESSAGE_MMS, false)
        set(sendLongMessageMMS) = prefs.edit().putBoolean(SEND_LONG_MESSAGE_MMS, sendLongMessageMMS)
            .apply()

    var sendGroupMessageMMS: Boolean
        get() = prefs.getBoolean(SEND_GROUP_MESSAGE_MMS, false)
        set(sendGroupMessageMMS) = prefs.edit()
            .putBoolean(SEND_GROUP_MESSAGE_MMS, sendGroupMessageMMS).apply()

    var lockScreenVisibilitySetting: Int
        get() = prefs.getInt(LOCK_SCREEN_VISIBILITY, LOCK_SCREEN_SENDER_MESSAGE)
        set(lockScreenVisibilitySetting) = prefs.edit()
            .putInt(LOCK_SCREEN_VISIBILITY, lockScreenVisibilitySetting).apply()

    var mmsFileSizeLimit: Long
        get() = prefs.getLong(MMS_FILE_SIZE_LIMIT, FILE_SIZE_600_KB)
        set(mmsFileSizeLimit) = prefs.edit().putLong(MMS_FILE_SIZE_LIMIT, mmsFileSizeLimit).apply()

    /**
     * Kept in pin order so pinned rows never reshuffle by recency. A string set has no order, so the
     * order lives in its own key; the set is still written so a downgrade keeps the pins.
     */
    var pinnedConversations: Set<String>
        get() = prefs.getString(PINNED_CONVERSATIONS_ORDERED, null)
            ?.split(',')
            ?.filterTo(LinkedHashSet()) { it.isNotEmpty() }
            ?: prefs.getStringSet(PINNED_CONVERSATIONS, HashSet<String>())!!.toCollection(LinkedHashSet())
        set(pinnedConversations) = prefs.edit()
            .putString(PINNED_CONVERSATIONS_ORDERED, pinnedConversations.joinToString(","))
            .putStringSet(PINNED_CONVERSATIONS, pinnedConversations).apply()

    fun addPinnedConversationByThreadId(threadId: Long) {
        pinnedConversations = pinnedConversations.plus(threadId.toString())
    }

    fun addPinnedConversations(conversations: List<Conversation>) {
        pinnedConversations = pinnedConversations.plus(conversations.map { it.threadId.toString() })
    }

    fun removePinnedConversationByThreadId(threadId: Long) {
        pinnedConversations = pinnedConversations.minus(threadId.toString())
    }

    fun removePinnedConversations(conversations: List<Conversation>) {
        pinnedConversations =
            pinnedConversations.minus(conversations.map { it.threadId.toString() })
    }

    var blockedKeywords: Set<String>
        get() = prefs.getStringSet(BLOCKED_KEYWORDS, HashSet<String>())!!
        set(blockedKeywords) = prefs.edit().putStringSet(BLOCKED_KEYWORDS, blockedKeywords).apply()

    fun addBlockedKeyword(keyword: String) {
        blockedKeywords = blockedKeywords.plus(keyword)
    }

    fun removeBlockedKeyword(keyword: String) {
        blockedKeywords = blockedKeywords.minus(keyword)
    }

    var exportSms: Boolean
        get() = prefs.getBoolean(EXPORT_SMS, true)
        set(exportSms) = prefs.edit().putBoolean(EXPORT_SMS, exportSms).apply()

    var exportMms: Boolean
        get() = prefs.getBoolean(EXPORT_MMS, true)
        set(exportMms) = prefs.edit().putBoolean(EXPORT_MMS, exportMms).apply()

    var importSms: Boolean
        get() = prefs.getBoolean(IMPORT_SMS, true)
        set(importSms) = prefs.edit().putBoolean(IMPORT_SMS, importSms).apply()

    var importMms: Boolean
        get() = prefs.getBoolean(IMPORT_MMS, true)
        set(importMms) = prefs.edit().putBoolean(IMPORT_MMS, importMms).apply()

    var wasDbCleared: Boolean
        get() = prefs.getBoolean(WAS_DB_CLEARED, false)
        set(wasDbCleared) = prefs.edit().putBoolean(WAS_DB_CLEARED, wasDbCleared).apply()

    var keyboardHeight: Int
        get() = prefs.getInt(SOFT_KEYBOARD_HEIGHT, context.getDefaultKeyboardHeight())
        set(keyboardHeight) = prefs.edit().putInt(SOFT_KEYBOARD_HEIGHT, keyboardHeight).apply()

    var useRecycleBin: Boolean
        get() = prefs.getBoolean(USE_RECYCLE_BIN, false)
        set(useRecycleBin) = prefs.edit().putBoolean(USE_RECYCLE_BIN, useRecycleBin).apply()

    var lastRecycleBinCheck: Long
        get() = prefs.getLong(LAST_RECYCLE_BIN_CHECK, 0L)
        set(lastRecycleBinCheck) = prefs.edit().putLong(LAST_RECYCLE_BIN_CHECK, lastRecycleBinCheck)
            .apply()

    var isArchiveAvailable: Boolean
        get() = prefs.getBoolean(IS_ARCHIVE_AVAILABLE, true)
        set(isArchiveAvailable) = prefs.edit().putBoolean(IS_ARCHIVE_AVAILABLE, isArchiveAvailable)
            .apply()

    var customNotifications: Set<String>
        get() = prefs.getStringSet(CUSTOM_NOTIFICATIONS, HashSet<String>())!!
        set(customNotifications) = prefs.edit()
            .putStringSet(CUSTOM_NOTIFICATIONS, customNotifications).apply()

    fun addCustomNotificationsByThreadId(threadId: Long) {
        customNotifications = customNotifications.plus(threadId.toString())
    }

    fun removeCustomNotificationsByThreadId(threadId: Long) {
        customNotifications = customNotifications.minus(threadId.toString())
    }

    var lastBlockedKeywordExportPath: String
        get() = prefs.getString(LAST_BLOCKED_KEYWORD_EXPORT_PATH, "")!!
        set(lastBlockedNumbersExportPath) = prefs.edit()
            .putString(LAST_BLOCKED_KEYWORD_EXPORT_PATH, lastBlockedNumbersExportPath).apply()

    var keepConversationsArchived: Boolean
        get() = prefs.getBoolean(KEEP_CONVERSATIONS_ARCHIVED, false)
        set(keepConversationsArchived) = prefs.edit()
            .putBoolean(KEEP_CONVERSATIONS_ARCHIVED, keepConversationsArchived).apply()

    // Off by default: a false positive silently hides a real message.
    var ruleFilterEnabled: Boolean
        get() = prefs.getBoolean(RULE_FILTER_ENABLED, false)
        set(ruleFilterEnabled) = prefs.edit().putBoolean(RULE_FILTER_ENABLED, ruleFilterEnabled).apply()

    var allowedNumbers: Set<String>
        get() = prefs.getStringSet(ALLOWED_NUMBERS, HashSet<String>())!!
        set(allowedNumbers) = prefs.edit().putStringSet(ALLOWED_NUMBERS, allowedNumbers).apply()

    fun addAllowedNumber(number: String) {
        allowedNumbers = allowedNumbers.plus(number)
    }

    var swipeLeftAction: SwipeAction
        get() = SwipeAction.fromId(prefs.getInt(SWIPE_LEFT_ACTION, SwipeAction.ARCHIVE.id))
        set(swipeLeftAction) = prefs.edit().putInt(SWIPE_LEFT_ACTION, swipeLeftAction.id).apply()

    var swipeRightAction: SwipeAction
        get() = SwipeAction.fromId(prefs.getInt(SWIPE_RIGHT_ACTION, SwipeAction.ARCHIVE.id))
        set(swipeRightAction) = prefs.edit().putInt(SWIPE_RIGHT_ACTION, swipeRightAction.id).apply()

    var mutedConversations: Set<String>
        get() = prefs.getStringSet(MUTED_CONVERSATIONS, HashSet<String>())!!
        set(mutedConversations) = prefs.edit().putStringSet(MUTED_CONVERSATIONS, mutedConversations).apply()

    fun isConversationMuted(threadId: Long) = mutedConversations.contains(threadId.toString())

    fun setConversationMuted(threadId: Long, muted: Boolean) {
        val id = threadId.toString()
        mutedConversations = if (muted) mutedConversations.plus(id) else mutedConversations.minus(id)
    }

    // ponytail: entries are not pruned when a message is deleted. Prune on delete if these ever grow large.
    var starredMessages: Set<String>
        get() = prefs.getStringSet(STARRED_MESSAGES, HashSet<String>())!!
        set(starredMessages) = prefs.edit().putStringSet(STARRED_MESSAGES, starredMessages).apply()

    // Legacy keys omitted the provider type. Keep them as SMS; MMS entries need to be starred again.
    private fun messageKey(messageId: Long, isMMS: Boolean) = if (isMMS) "mms:$messageId" else messageId.toString()

    fun isMessageStarred(messageId: Long, isMMS: Boolean) = starredMessages.contains(messageKey(messageId, isMMS))

    fun setMessageStarred(messageId: Long, isMMS: Boolean, starred: Boolean) {
        val id = messageKey(messageId, isMMS)
        starredMessages = if (starred) starredMessages.plus(id) else starredMessages.minus(id)
    }

    /** SMS has no reaction channel, so reactions stay on this device. Keys include the provider type; legacy numeric keys remain SMS keys. */
    private var messageReactions: Set<String>
        get() = prefs.getStringSet(MESSAGE_REACTIONS, HashSet<String>())!!
        set(messageReactions) = prefs.edit().putStringSet(MESSAGE_REACTIONS, messageReactions).apply()

    fun getMessageReaction(messageId: Long, isMMS: Boolean): String? {
        val prefix = "${messageKey(messageId, isMMS)}:"
        return messageReactions.firstOrNull { it.startsWith(prefix) }?.removePrefix(prefix)
    }

    fun setMessageReaction(messageId: Long, isMMS: Boolean, emoji: String?) {
        val prefix = "${messageKey(messageId, isMMS)}:"
        val others = messageReactions.filterNotTo(HashSet()) { it.startsWith(prefix) }
        messageReactions = if (emoji == null) others else others.plus("$prefix$emoji")
    }

    var recentSearches: List<String>
        get() = prefs.getString(RECENT_SEARCHES, "")!!.split('\n').filter { it.isNotBlank() }
        set(recentSearches) = prefs.edit().putString(RECENT_SEARCHES, recentSearches.joinToString("\n")).apply()

    fun addRecentSearch(query: String) {
        recentSearches = (listOf(query) + recentSearches.filterNot { it.equals(query, ignoreCase = true) })
            .take(MAX_RECENT_SEARCHES)
    }
}
