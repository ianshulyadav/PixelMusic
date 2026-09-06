package com.unshoo.pixelmusic

import android.util.Log
import timber.log.Timber

/**
 * A release-optimized Timber Tree that:
 * - Only logs WARN, ERROR, and WTF (suppresses VERBOSE, DEBUG, and INFO)
 * - Strips method/line information for performance
 */
class ReleaseTree : Timber.Tree() {
    
    override fun isLoggable(tag: String?, priority: Int): Boolean {
        return priority >= Log.WARN
    }
    
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (priority < Log.WARN) return
        
        when (priority) {
            Log.WARN -> Log.w(tag, message, t)
            Log.ERROR -> Log.e(tag, message, t)
            Log.ASSERT -> Log.wtf(tag, message, t)
        }
        
    }
}
