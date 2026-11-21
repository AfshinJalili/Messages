package com.afsh.messages.helpers

import android.util.LruCache
import com.afsh.commons.models.SimpleContact
import com.afsh.messages.models.NamePhoto

private const val CACHE_SIZE = 512

object MessagingCache {
    val namePhoto = LruCache<String, NamePhoto>(CACHE_SIZE)
    val participantsCache = LruCache<Long, ArrayList<SimpleContact>>(CACHE_SIZE)
}
