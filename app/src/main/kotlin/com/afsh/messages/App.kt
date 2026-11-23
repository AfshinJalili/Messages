package com.afsh.messages

import android.app.Activity
import android.app.Application
import android.content.res.Configuration
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import com.afsh.commons.AfshApp
import com.afsh.commons.extensions.appLockManager
import com.afsh.commons.extensions.hasPermission
import com.afsh.commons.helpers.PERMISSION_READ_CONTACTS
import com.afsh.commons.helpers.REQUEST_APP_UNLOCK
import com.afsh.commons.helpers.ensureBackgroundThread
import com.afsh.messages.activities.AppLockActivity
import com.afsh.messages.extensions.rescheduleAllScheduledMessages
import com.afsh.messages.extensions.config
import com.afsh.messages.extensions.notificationHelper
import com.afsh.messages.helpers.InboxRepository
import com.afsh.messages.helpers.MessagingEvents
import com.afsh.messages.helpers.afshXmlTheme
import com.afsh.messages.helpers.ListDetail
import com.afsh.messages.helpers.MessagingCache

class App : AfshApp() {
    override val isAppLockFeatureAvailable = true

    override fun themeId(activity: Activity) = activity.afshXmlTheme()

    companion object {
        private const val TAG = "AfshApp"
        private const val CONTACTS_CACHE_DEBOUNCE_MS = 750L
    }

    override fun onCreate() {
        super.onCreate()
        InboxRepository.init(this)
        MessagingEvents.init(this)
        ListDetail.install(this)
        config.applyThemeDefaults()
        watchContacts()

        ensureBackgroundThread {
            rescheduleAllScheduledMessages()
        }
        notificationHelper.createDefaultChannel()

        // Register app lock lifecycle handler
        registerActivityLifecycleCallbacks(appLockCallbacks)
    }

    private val appLockCallbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityResumed(activity: Activity) {
            // Launch lock screen if locked, but not if we're already on the lock screen
            if (activity !is AppLockActivity && appLockManager.isLocked()) {
                AppLockActivity.launch(activity, REQUEST_APP_UNLOCK)
            }
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
        override fun onActivityStarted(activity: Activity) {}
        override fun onActivityPaused(activity: Activity) {}
        override fun onActivityStopped(activity: Activity) {}
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        override fun onActivityDestroyed(activity: Activity) {}
    }

    // Android shows the channel name it was given last; rename it when the app language changes.
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        notificationHelper.createDefaultChannel()
    }

    private var watchingContacts = false
    private val contactsHandler = Handler(Looper.getMainLooper())
    private val clearContactCaches = Runnable {
        MessagingCache.namePhoto.evictAll()
        MessagingCache.participantsCache.evictAll()
    }

    /**
     * Clears the contact caches when contacts change. The inbox refresh relies on them, so this runs
     * again once contacts permission is granted after launch. Contact sync fires many rapid
     * onChange callbacks; debounce so a sync storm does not thrash the caches while the process
     * is warm as the default SMS app.
     */
    fun watchContacts() {
        if (watchingContacts || !hasPermission(PERMISSION_READ_CONTACTS)) return
        watchingContacts = true
        // Contacts.CONTENT_URI with descendants already covers Data and photos; observing all
        // three multiplied every sync callback.
        try {
            contentResolver.registerContentObserver(
                ContactsContract.Contacts.CONTENT_URI,
                true,
                contactsObserver,
            )
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Failed to register content observer for contacts", e)
        }
    }

    private val contactsObserver = object : ContentObserver(contactsHandler) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            contactsHandler.removeCallbacks(clearContactCaches)
            contactsHandler.postDelayed(clearContactCaches, CONTACTS_CACHE_DEBOUNCE_MS)
        }
    }
}
