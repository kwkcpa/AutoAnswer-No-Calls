package com.autoanswer.app

import android.telecom.Call
import android.util.Log

/**
 * Singleton to share the current call state between
 * AutoAnswerInCallService and InCallActivity.
 */
object CallManager {

    private const val TAG = "CallManager"

    var currentCall: Call? = null
        private set

    var onCallChanged: ((Call?) -> Unit)? = null

    fun updateCall(call: Call?) {
        Log.d(TAG, "Call updated: ${call?.state}")
        currentCall = call
        onCallChanged?.invoke(call)
    }

    fun answer() {
        currentCall?.answer(android.telecom.VideoProfile.STATE_AUDIO_ONLY)
    }

    fun hangup() {
        currentCall?.disconnect()
    }

    fun reject() {
        currentCall?.reject(false, null)
    }
}
