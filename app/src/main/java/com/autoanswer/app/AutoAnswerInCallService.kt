package com.autoanswer.app

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.telecom.Call
import android.telecom.InCallService
import android.util.Log
import java.util.Locale

class AutoAnswerInCallService : InCallService() {

    companion object {
        private const val TAG = "AutoAnswerInCall"
    }

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private val handler = Handler(Looper.getMainLooper())

    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            Log.d(TAG, "Call state changed: $state")
            CallManager.updateCall(call)

            when (state) {
                Call.STATE_ACTIVE -> {
                    onCallActive()
                }
                Call.STATE_DISCONNECTED -> {
                    stopTts()
                    CallManager.updateCall(null)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "InCallService created")

        // Initialize TTS early
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.US)
                ttsReady = result != TextToSpeech.LANG_MISSING_DATA &&
                           result != TextToSpeech.LANG_NOT_SUPPORTED
                tts?.setSpeechRate(0.9f)
                Log.d(TAG, "TTS ready: $ttsReady")
            } else {
                Log.e(TAG, "TTS init failed: $status")
            }
        }
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        Log.d(TAG, "Call added, state: ${call.state}")

        call.registerCallback(callCallback)
        CallManager.updateCall(call)

        // Launch the in-call UI
        val uiIntent = Intent(this, InCallActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(uiIntent)

        // Check if auto-answer is enabled
        val prefs = getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean(MainActivity.KEY_ENABLED, false)

        if (isEnabled && call.state == Call.STATE_RINGING) {
            val delaySec = prefs.getInt(MainActivity.KEY_DELAY_SECONDS, 0)
            val delayMs = delaySec * 1000L

            Log.d(TAG, "Auto-answering in ${delaySec}s")
            handler.postDelayed({
                if (CallManager.currentCall?.state == Call.STATE_RINGING) {
                    CallManager.answer()
                }
            }, delayMs)
        }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        Log.d(TAG, "Call removed")
        call.unregisterCallback(callCallback)
        stopTts()
        CallManager.updateCall(null)
    }

    private fun onCallActive() {
        val prefs = getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean(MainActivity.KEY_ENABLED, false)

        if (!isEnabled) return

        val message = prefs.getString(
            MainActivity.KEY_MESSAGE,
            MainActivity.DEFAULT_MESSAGE
        ) ?: MainActivity.DEFAULT_MESSAGE

        // Delay to let call audio stabilize
        handler.postDelayed({
            playAnnouncement(message)
        }, 1500)
    }

    private fun playAnnouncement(message: String) {
        Log.d(TAG, "Playing announcement: $message")

        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.mode = AudioManager.MODE_IN_CALL

        // Max voice call volume
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
        audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, maxVol, 0)

        if (!ttsReady) {
            Log.e(TAG, "TTS not ready, reinitializing")
            tts = TextToSpeech(this) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.setLanguage(Locale.US)
                    tts?.setSpeechRate(0.9f)
                    ttsReady = true
                    speakMessage(message)
                }
            }
            return
        }

        speakMessage(message)
    }

    private fun speakMessage(message: String) {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                Log.d(TAG, "TTS started speaking")
            }

            override fun onDone(utteranceId: String?) {
                Log.d(TAG, "TTS finished speaking")
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                Log.e(TAG, "TTS error")
            }
        })

        // As default dialer, STREAM_VOICE_CALL should work properly
        val params = android.os.Bundle().apply {
            putInt(
                TextToSpeech.Engine.KEY_PARAM_STREAM,
                AudioManager.STREAM_VOICE_CALL
            )
        }
        tts?.speak(message, TextToSpeech.QUEUE_FLUSH, params, "announcement")
    }

    private fun stopTts() {
        tts?.stop()
    }

    override fun onDestroy() {
        Log.d(TAG, "InCallService destroyed")
        tts?.shutdown()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
