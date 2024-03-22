package org.eclipse.uprotocol.common.util.log

import android.util.Log

fun logD(isLoggable: Boolean, tag: String, message: String) {
    if (isLoggable) {
        Log.d(tag, message)
    }
}

fun logV(isLoggable: Boolean, tag: String, message: String) {
    if (isLoggable) {
        Log.v(tag, message)
    }
}
