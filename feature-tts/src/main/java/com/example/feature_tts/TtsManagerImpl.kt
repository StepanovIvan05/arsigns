package com.example.feature_tts

import android.content.Context
import android.speech.tts.TextToSpeech
import com.example.domain.repository.ITtsManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TtsManagerImpl @Inject constructor(
    @ApplicationContext context: Context
) : ITtsManager, TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isReady = false
    private var utteranceId = 0

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        isReady = status == TextToSpeech.SUCCESS
    }

    override fun speak(text: String) {
        if (!isReady) return
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "arsigns-${utteranceId++}")
    }

    override fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.shutdown()
    }
}
