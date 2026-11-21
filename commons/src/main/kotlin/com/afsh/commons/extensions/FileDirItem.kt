package com.afsh.commons.extensions

import android.content.Context
import com.afsh.commons.models.FileDirItem

fun FileDirItem.isRecycleBinPath(context: Context): Boolean {
    return path.startsWith(context.recycleBinPath)
}
