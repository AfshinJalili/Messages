package org.fossify.messages.dialogs

import android.view.ViewGroup
import org.fossify.commons.fragments.BaseBottomSheetDialogFragment
import org.fossify.messages.activities.ThreadActivity
import org.fossify.messages.databinding.LayoutAttachmentPickerBinding

class AttachmentPickerDialog : BaseBottomSheetDialogFragment() {
    override fun setupContentView(parent: ViewGroup) {
        val picker = LayoutAttachmentPickerBinding.inflate(layoutInflater, parent, false)
        parent.addView(picker.root)
        // looked up, not passed in: a sheet restored after recreation must reach the new activity
        (activity as? ThreadActivity)?.setupAttachmentPickerView(picker) { dismiss() }
    }

    companion object {
        const val TAG = "AttachmentPickerDialog"
    }
}
