package com.afsh.messages.activities

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
import android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
import android.net.Uri
import android.provider.ContactsContract
import android.provider.MediaStore
import android.speech.RecognizerIntent
import android.text.format.DateUtils
import android.text.format.DateUtils.FORMAT_NO_YEAR
import android.text.format.DateUtils.FORMAT_SHOW_DATE
import android.text.format.DateUtils.FORMAT_SHOW_TIME
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.net.toUri
import com.afsh.commons.extensions.formatDate
import com.afsh.commons.extensions.getFilenameFromUri
import com.afsh.commons.extensions.getMyFileUri
import com.afsh.commons.extensions.hideKeyboard
import com.afsh.commons.extensions.showErrorToast
import com.afsh.commons.extensions.toast
import com.afsh.commons.helpers.ContactsHelper
import com.afsh.commons.helpers.ensureBackgroundThread
import com.afsh.messages.R
import com.afsh.messages.extensions.config
import com.afsh.messages.extensions.fileSize
import com.afsh.messages.extensions.isGifMimeType
import com.afsh.messages.extensions.isImageMimeType
import com.afsh.messages.helpers.CAPTURE_AUDIO_INTENT
import com.afsh.messages.helpers.CAPTURE_PHOTO_INTENT
import com.afsh.messages.helpers.CAPTURE_VIDEO_INTENT
import com.afsh.messages.helpers.DraftsRepository
import com.afsh.messages.helpers.FILE_SIZE_NONE
import com.afsh.messages.helpers.ImageCompressor
import com.afsh.messages.helpers.MessageSendCoordinator
import com.afsh.messages.helpers.PICK_CONTACT_INTENT
import com.afsh.messages.helpers.PICK_DOCUMENT_INTENT
import com.afsh.messages.helpers.PICK_PHOTO_INTENT
import com.afsh.messages.helpers.PICK_SAVE_DIR_INTENT
import com.afsh.messages.helpers.PICK_SAVE_FILE_INTENT
import com.afsh.messages.helpers.PICK_VIDEO_INTENT
import com.afsh.messages.helpers.THREAD_ATTACHMENT_URI
import com.afsh.messages.helpers.THREAD_ATTACHMENT_URIS
import com.afsh.messages.helpers.THREAD_TEXT
import com.afsh.messages.helpers.contactAsText
import com.afsh.messages.helpers.insertBlock
import com.afsh.messages.models.Attachment
import com.afsh.messages.models.AttachmentSelection
import com.afsh.messages.models.Message
import com.afsh.messages.ui.schedule.ScheduleTarget
import com.afsh.messages.ui.thread.AttachOption
import org.joda.time.DateTime
import java.io.File

/**
 * The composer's platform side: its draft, attachments from pickers and the camera (compressed for MMS),
 * contacts as text, dictation, the send time, sending, scheduled messages, and saving attachments to files.
 * The composer's state lives in [ThreadVisit.holder]; sending goes through [ThreadVisit.sender].
 */
internal class ThreadComposerActions(private val visit: ThreadVisit) {
    private val activity get() = visit.activity
    private val holder get() = visit.holder
    private val sender get() = visit.sender
    private val imageCompressor by lazy { ImageCompressor(activity) }
    private val drafts by lazy { DraftsRepository.from(activity) }
    private var capturedImageUri: Uri? = null

    // region Draft

    /** Text and attachments from before a recreation win over the intent's. */
    fun restore(text: String?, attachments: List<Uri>) {
        if (text != null) {
            holder.setText(text)
            attachments.forEach(::addAttachment)
            return
        }
        val intent = activity.intent
        intent.getStringExtra(THREAD_TEXT)?.let { holder.setText(it) }
        if (intent.extras?.containsKey(THREAD_ATTACHMENT_URI) == true) {
            addAttachment(intent.getStringExtra(THREAD_ATTACHMENT_URI)!!.toUri())
        } else if (intent.extras?.containsKey(THREAD_ATTACHMENT_URIS) == true) {
            @Suppress("UNCHECKED_CAST", "DEPRECATION")
            (intent.getSerializableExtra(THREAD_ATTACHMENT_URIS) as? ArrayList<Uri>)?.forEach(::addAttachment)
        }
    }

    fun loadDraft() {
        ensureBackgroundThread {
            val draft = drafts.get(visit.threadId)
            if (draft.isNotEmpty()) activity.runOnUiThread { holder.setText(draft) }
        }
    }

    fun saveDraft() {
        val text = holder.text
        val hasAttachments = holder.attachments.isNotEmpty()
        ensureBackgroundThread { drafts.store(visit.threadId, text, hasAttachments) }
    }

    // endregion

    // region Attachments

    fun attach(option: AttachOption) = when (option) {
        AttachOption.PHOTO -> launchGetContent(arrayOf("image/*"), PICK_PHOTO_INTENT)
        AttachOption.CAMERA -> launchCapturePhoto()
        AttachOption.VIDEO -> launchGetContent(arrayOf("video/*"), PICK_VIDEO_INTENT)
        AttachOption.RECORD_VIDEO -> launchForResult(Intent(MediaStore.ACTION_VIDEO_CAPTURE), CAPTURE_VIDEO_INTENT)
        AttachOption.AUDIO -> launchForResult(Intent(MediaStore.Audio.Media.RECORD_SOUND_ACTION), CAPTURE_AUDIO_INTENT)
        AttachOption.FILE -> launchGetContent(arrayOf("*/*"), PICK_DOCUMENT_INTENT)
        AttachOption.CONTACT ->
            launchForResult(Intent(Intent.ACTION_PICK).setType(ContactsContract.Contacts.CONTENT_TYPE), PICK_CONTACT_INTENT)
        AttachOption.SEND_LATER -> launchScheduleSendDialog(holder.scheduledMillis)
    }

    /** A result from a picker, the camera or a save location. */
    fun onResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        if (resultCode != Activity.RESULT_OK) return
        val data = resultData?.data
        if (requestCode == CAPTURE_PHOTO_INTENT && capturedImageUri != null) {
            addAttachment(capturedImageUri!!)
        } else if (data != null) {
            when (requestCode) {
                CAPTURE_VIDEO_INTENT, PICK_DOCUMENT_INTENT, CAPTURE_AUDIO_INTENT, PICK_PHOTO_INTENT, PICK_VIDEO_INTENT -> addAttachment(data)
                PICK_CONTACT_INTENT -> addContactAsText(data)
                PICK_SAVE_FILE_INTENT, PICK_SAVE_DIR_INTENT -> saveAttachmentsTo(data)
            }
        }
    }

    private fun launchForResult(intent: Intent, requestCode: Int, @StringRes error: Int = com.afsh.commons.R.string.no_app_found) {
        activity.hideKeyboard()
        try {
            @Suppress("DEPRECATION")
            activity.startActivityForResult(intent, requestCode)
        } catch (_: ActivityNotFoundException) {
            activity.showErrorToast(activity.getString(error))
        } catch (e: Exception) {
            activity.showErrorToast(e)
        }
    }

    private fun launchCapturePhoto() {
        val dir = File(activity.cacheDir, "attachments").apply { if (!exists()) mkdirs() }
        val imageFile = File.createTempFile("attachment_", ".jpg", dir)
        capturedImageUri = activity.getMyFileUri(imageFile)
        launchForResult(Intent(MediaStore.ACTION_IMAGE_CAPTURE).putExtra(MediaStore.EXTRA_OUTPUT, capturedImageUri), CAPTURE_PHOTO_INTENT)
    }

    private fun launchGetContent(mimeTypes: Array<String>, requestCode: Int) {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
        }
        launchForResult(intent, requestCode)
    }

    /** A picked contact goes into the composer as plain text (name, then numbers), so it sends as SMS. */
    private fun addContactAsText(contactUri: Uri) {
        ensureBackgroundThread {
            val contact = ContactsHelper(activity).getContactFromUri(contactUri)
            val text = contact?.let { contactAsText(it.getNameToDisplay(), it.phoneNumbers) }
            activity.runOnUiThread {
                when {
                    contact == null -> activity.toast(com.afsh.commons.R.string.unknown_error_occurred)
                    text == null -> activity.toast(com.afsh.commons.R.string.no_phone_number_found)
                    else -> {
                        val at = holder.composerText.selection.min.coerceIn(0, holder.text.length)
                        val (value, cursor) = insertBlock(holder.text, at, text)
                        holder.replaceText(value, cursor)
                    }
                }
            }
        }
    }

    private fun addAttachment(uri: Uri) {
        val id = uri.toString()
        if (holder.hasAttachment(id)) {
            activity.toast(R.string.duplicate_item_warning)
            return
        }

        val mimeType = activity.contentResolver.getType(uri)
        if (mimeType == null) {
            activity.toast(com.afsh.commons.R.string.unknown_error_occurred)
            return
        }
        val isImage = mimeType.isImageMimeType()
        val isGif = mimeType.isGifMimeType()
        val mmsFileSizeLimit = activity.config.mmsFileSizeLimit
        if (isGif || !isImage) {
            // is it assumed that images will always be compressed below the max MMS size limit
            val fileSize = activity.contentResolver.fileSize(uri)
            if (mmsFileSizeLimit != FILE_SIZE_NONE && fileSize > mmsFileSizeLimit) {
                activity.toast(R.string.attachment_sized_exceeds_max_limit, length = Toast.LENGTH_LONG)
                return
            }
        }

        val compress = isImage && !isGif && mmsFileSizeLimit != FILE_SIZE_NONE
        val selection = AttachmentSelection(id, uri, mimeType, activity.getFilenameFromUri(uri), isPending = compress)
        holder.addAttachment(selection)
        if (!compress) return
        imageCompressor.compressImage(uri, mmsFileSizeLimit) { compressedUri ->
            activity.runOnUiThread {
                if (activity.isDestroyed) return@runOnUiThread
                if (holder.finishCompression(selection, compressedUri) && compressedUri == null) activity.toast(R.string.compress_error)
            }
        }
    }

    /** Save as: the person picks a file for one attachment, a folder for several. */
    fun saveAttachments(attachments: List<Attachment>) {
        val (intent, requestCode) = when (val request = sender.saveAttachments(attachments) ?: return) {
            is MessageSendCoordinator.SaveRequest.File -> Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                type = request.mimetype
                addCategory(Intent.CATEGORY_OPENABLE)
                putExtra(Intent.EXTRA_TITLE, request.title)
            } to PICK_SAVE_FILE_INTENT
            MessageSendCoordinator.SaveRequest.Folder ->
                Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addCategory(Intent.CATEGORY_DEFAULT) to PICK_SAVE_DIR_INTENT
        }
        launchForResult(intent, requestCode, error = com.afsh.commons.R.string.system_service_disabled)
    }

    private fun saveAttachmentsTo(destination: Uri) {
        activity.applicationContext.contentResolver.takePersistableUriPermission(
            destination, FLAG_GRANT_READ_URI_PERMISSION or FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        sender.saveTo(destination)
    }

    // endregion

    // region Dictation

    fun launchDictation() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        try {
            activity.dictate.launch(intent)
        } catch (_: ActivityNotFoundException) {
            activity.toast(com.afsh.commons.R.string.no_app_found)
        }
    }

    fun dictated(result: Intent?) {
        val spoken = result?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull() ?: return
        // Insert, never auto-send: dictation is frequently wrong.
        holder.insertAtCursor(spoken)
    }

    // endregion

    // region Sending and scheduling

    fun send() {
        // Also while an image is still compressing: the original may be over the MMS limit.
        if (!holder.composer.hasContent(holder.text)) return
        holder.scrollToBottom()
        sender.send(holder.text, holder.attachments, holder.sendSubscriptionId, holder.scheduledMillis)
    }

    fun launchScheduleSendDialog(originalMillis: Long? = null) {
        sender.whenExactAlarmsAllowed {
            val subscriptionId = holder.sendSubscriptionId ?: -1
            val target = ScheduleTarget(holder.text.trim(), scheduleRecipient(subscriptionId))
            activity.sendLater.launch(SendLaterActivity.Request(target, originalMillis))
        }
    }

    /** 12 returned a time: with something written it is scheduled at once; otherwise the composer waits for it. */
    fun onSendTimeChosen(millis: Long) {
        val label = if (DateTime(millis).yearOfCentury().get() > DateTime.now().yearOfCentury().get()) {
            millis.formatDate(activity)
        } else {
            DateUtils.formatDateTime(activity, millis, FORMAT_SHOW_TIME or FORMAT_SHOW_DATE or FORMAT_NO_YEAR)
        }
        holder.setSchedule(millis, label)
        if (holder.composer.hasContent(holder.text)) send()
    }

    fun editScheduled(message: Message) = activity.editScheduled.launch(
        ScheduledMessageActivity.Request(message.id, ScheduleTarget(message.body, scheduleRecipient(message.subscriptionId)), message.millis()),
    )

    /** 47 decided: save the new text and time, send now, or cancel. */
    fun onScheduledDecision(messageId: Long, decision: ScheduledMessageActivity.Decision) {
        val message = visit.data.list.firstOrNull { it.id == messageId && it.isScheduled } ?: return
        when (decision) {
            is ScheduledMessageActivity.Decision.Save -> sender.updateScheduled(message, decision.text, decision.time)
            ScheduledMessageActivity.Decision.SendNow -> sendScheduledNow(message)
            ScheduledMessageActivity.Decision.Cancel -> visit.deletion.deleteMessages(listOf(message), toRecycleBin = false, fromRecycleBin = false)
        }
    }

    /** 84 · Send now: the scheduled message goes as a normal one, and its schedule is dropped. */
    fun sendScheduledNow(message: Message) {
        visit.data.list.removeAll { message.id == it.id }
        message.attachment?.attachments?.forEach { addAttachment(it.getUri()) }
        sender.sendScheduledNow(message, holder.attachments)
    }

    /** "To Mina Farahani", with "· SIM 1" when the phone has two SIMs. */
    private fun scheduleRecipient(subscriptionId: Int): String {
        val people = visit.people
        val to = activity.getString(R.string.scheduled_to, people.scheduleName(visit.conversation?.title))
        return people.simLabels[subscriptionId]
            ?.let { activity.getString(R.string.detail_pair, to, activity.getString(R.string.message_sim_label, it)) }
            ?: to
    }

    // endregion
}
