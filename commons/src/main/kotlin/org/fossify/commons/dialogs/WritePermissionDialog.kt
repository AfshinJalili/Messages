package org.fossify.commons.dialogs

import android.app.Activity
import android.text.Html
import androidx.appcompat.app.AlertDialog
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import org.fossify.commons.R
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.databinding.DialogWritePermissionBinding
import org.fossify.commons.databinding.DialogWritePermissionOtgBinding
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.humanizePath
import org.fossify.commons.extensions.setupDialogStuff

class WritePermissionDialog(activity: Activity, val writePermissionDialogMode: WritePermissionDialogMode, val callback: () -> Unit) {

    sealed class WritePermissionDialogMode {
        data object Otg : WritePermissionDialogMode()

        data object SdCard : WritePermissionDialogMode()

        data class OpenDocumentTreeSDK30(val path: String) : WritePermissionDialogMode()

        data object CreateDocumentSDK30 : WritePermissionDialogMode()
    }

    private var dialog: AlertDialog? = null

    init {
        val sdCardView = DialogWritePermissionBinding.inflate(activity.layoutInflater, null, false)
        val otgView = DialogWritePermissionOtgBinding.inflate(
            activity.layoutInflater,
            null,
            false
        )

        var dialogTitle = R.string.confirm_storage_access_title

        val glide = Glide.with(activity)
        val crossFade = DrawableTransitionOptions.withCrossFade()
        when (writePermissionDialogMode) {
            WritePermissionDialogMode.Otg -> {
                otgView.writePermissionsDialogOtgText.setText(R.string.confirm_usb_storage_access_text)
                glide.load(R.drawable.img_write_storage_otg).transition(crossFade).into(otgView.writePermissionsDialogOtgImage)
            }

            WritePermissionDialogMode.SdCard -> {
                glide.load(R.drawable.img_write_storage).transition(crossFade).into(sdCardView.writePermissionsDialogImage)
                glide.load(R.drawable.img_write_storage_sd).transition(crossFade).into(sdCardView.writePermissionsDialogImageSd)
            }

            is WritePermissionDialogMode.OpenDocumentTreeSDK30 -> {
                dialogTitle = R.string.confirm_folder_access_title
                val humanizedPath = activity.humanizePath(writePermissionDialogMode.path)
                otgView.writePermissionsDialogOtgText.text =
                    Html.fromHtml(activity.getString(R.string.confirm_storage_access_android_text_specific, humanizedPath))
                glide.load(R.drawable.img_write_storage_sdk_30).transition(crossFade).into(otgView.writePermissionsDialogOtgImage)

                otgView.writePermissionsDialogOtgImage.setOnClickListener {
                    dialogConfirmed()
                }
            }

            WritePermissionDialogMode.CreateDocumentSDK30 -> {
                dialogTitle = R.string.confirm_folder_access_title
                otgView.writePermissionsDialogOtgText.text = Html.fromHtml(activity.getString(R.string.confirm_create_doc_for_new_folder_text))
                glide.load(R.drawable.img_write_storage_create_doc_sdk_30).transition(crossFade).into(otgView.writePermissionsDialogOtgImage)

                otgView.writePermissionsDialogOtgImage.setOnClickListener {
                    dialogConfirmed()
                }
            }
        }

        activity.getAlertDialogBuilder()
            .setPositiveButton(R.string.ok) { _, _ -> dialogConfirmed() }
            .setOnCancelListener {
                BaseSimpleActivity.funAfterSAFPermission?.invoke(false)
                BaseSimpleActivity.funAfterSAFPermission = null
            }
            .apply {
                activity.setupDialogStuff(
                    if (writePermissionDialogMode == WritePermissionDialogMode.SdCard) sdCardView.root else otgView.root,
                    this,
                    dialogTitle
                ) { alertDialog ->
                    dialog = alertDialog
                }
            }
    }

    private fun dialogConfirmed() {
        dialog?.dismiss()
        callback()
    }
}