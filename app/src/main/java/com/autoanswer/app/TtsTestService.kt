package com.autoanswer.app

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

class TtsTestService : Service(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var pendingMessage: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        pendingMessage = intent?.getStringExtra("message")
        tts = TextToSpeech(this, this)
        return START_NOT_STICKY
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.setLanguage(Locale.US)
            tts?.setSpeechRate(0.9f)
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) { stopSelf() }
                @Deprecated("Deprecated") override fun onError(utteranceId: String?) { stopSelf() }
            })
            pendingMessage?.let { tts?.speak(it, TextToSpeech.QUEUE_FLUSH, null, "test") }
        } else { stopSelf() }
    }

    override fun onDestroy() { tts?.stop(); tts?.shutdown(); super.onDestroy() }
}
