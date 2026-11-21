package com.afsh.commons.views

import android.content.Context
import android.util.AttributeSet
import android.widget.AutoCompleteTextView
import com.afsh.commons.extensions.adjustAlpha
import com.afsh.commons.extensions.applyColorFilter
import com.afsh.commons.extensions.applyFontToTextView

open class MyAutoCompleteTextView : AutoCompleteTextView {
    constructor(context: Context) : super(context) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle) {
        init()
    }

    private fun init() {
        if (!isInEditMode) context.applyFontToTextView(this)
    }

    fun setColors(textColor: Int, accentColor: Int, backgroundColor: Int) {
        background?.mutate()?.applyColorFilter(accentColor)

        // requires android:textCursorDrawable="@null" in xml to color the cursor too
        setTextColor(textColor)
        setHintTextColor(textColor.adjustAlpha(0.5f))
        setLinkTextColor(accentColor)
    }
}
