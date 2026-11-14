package com.afsh.messages

import android.app.Activity
import android.content.res.Configuration
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import org.fossify.commons.FossifyApp
import org.fossify.commons.extensions.hasPermission
import org.fossify.commons.helpers.PERMISSION_READ_CONTACTS
import org.fossify.commons.helpers.ensureBackgroundThread
import com.afsh.messages.extensions.rescheduleAllScheduledMessages
import com.afsh.messages.extensions.config
import com.afsh.messages.extensions.notificationHelper
import com.afsh.messages.helpers.InboxRepository
import com.afsh.messages.helpers.afshXmlTheme
import com.afsh.messages.helpers.ListDetail
import com.afsh.messages.helpers.MessagingCache

class App : FossifyApp() {
    override val isAppLockFeatureAvailable = true

    override fun themeId(activity: Activity) = activity.afshXmlTheme()

    override fun onCreate() {
        super.onCreate()
        InboxRepository.init(this)
        ListDetail.install(this)
        config.applyThemeDefaults()
        watchContacts()

        ensureBackgroundThread {
            rescheduleAllScheduledMessages()
        }
        notificationHelper.createDefaultChannel()
    }

    // Android shows the channel name it was given last; rename it when the app language changes.
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        notificationHelper.createDefaultChannel()
    }

    private var watchingContacts = false

    /**
     * Clears the contact caches when contacts change. The inbox refresh relies on them, so this runs
     * again once contacts permission is granted after launch.
     */
    fun watchContacts() {
        if (watchingContacts || !hasPermission(PERMISSION_READ_CONTACTS)) return
        watchingContacts = true
        listOf(
            ContactsContract.Contacts.CONTENT_URI,
            ContactsContract.Data.CONTENT_URI,
            ContactsContract.DisplayPhoto.CONTENT_URI
        ).forEach {
            try {
                contentResolver.registerContentObserver(it, true, contactsObserver)
            } catch (_: Exception) {
            }
        }
    }

    private val contactsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            MessagingCache.namePhoto.evictAll()
            MessagingCache.participantsCache.evictAll()
        }
    }
}
