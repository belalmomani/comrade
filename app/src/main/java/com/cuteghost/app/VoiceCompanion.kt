package com.cuteghost.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Voice conversation loop: tap -> listen (speech to text) -> AI answer -> speak (text to speech).
 * Everything stays in memory only; nothing is recorded or saved. Must be used on the main thread.
 */
class VoiceCompanion(context: Context, private val ui: Ui) {

    interface Ui {
        fun onState(state: GhostView.VoiceState)
        fun onLevel(level: Float)
        fun onSad()
    }

    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()

    private var state = GhostView.VoiceState.IDLE
    private var token = 0
    private var recognizer: SpeechRecognizer? = null

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var ttsFailed = false
    private var pendingSpeech: String? = null
    private var currentUtterance = ""
    private var utteranceCounter = 0

    private val history = ArrayList<Pair<String, String>>()
    private var lastActivity = 0L

    private val listenTimeout = Runnable {
        if (state == GhostView.VoiceState.LISTENING) {
            token++
            recognizer?.cancel()
            speakLocal(R.string.say_not_heard)
        }
    }

    init {
        tts = TextToSpeech(appContext) { status ->
            handler.post {
                if (status == TextToSpeech.SUCCESS) onTtsReady() else ttsFailed = true
            }
        }
    }

    // =====================================================================
    // Public
    // =====================================================================

    fun onTap() {
        when (state) {
            GhostView.VoiceState.IDLE -> startListening()
            GhostView.VoiceState.LISTENING -> recognizer?.stopListening() // finish early
            GhostView.VoiceState.THINKING -> {
                token++
                setState(GhostView.VoiceState.IDLE)
            }
            GhostView.VoiceState.SPEAKING -> {
                currentUtterance = ""
                tts?.stop()
                setState(GhostView.VoiceState.IDLE)
            }
        }
    }

    fun release() {
        token++
        handler.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        recognizer = null
        tts?.stop()
        tts?.shutdown()
        tts = null
        executor.shutdownNow()
        history.clear()
        pendingSpeech = null
    }

    // =====================================================================
    // Text to speech
    // =====================================================================

    private fun onTtsReady() {
        val engine = tts ?: return
        val result = engine.setLanguage(Locale("ar"))
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            ttsFailed = true
            return
        }
        engine.setSpeechRate(0.85f)
        engine.setPitch(1.15f)
        engine.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                handler.post {
                    if (utteranceId == currentUtterance) setState(GhostView.VoiceState.SPEAKING)
                }
            }

            override fun onDone(utteranceId: String?) {
                handler.post {
                    if (utteranceId == currentUtterance) setState(GhostView.VoiceState.IDLE)
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                handler.post {
                    if (utteranceId == currentUtterance) {
                        setState(GhostView.VoiceState.IDLE)
                        ui.onSad()
                    }
                }
            }
        })
        ttsReady = true
        pendingSpeech?.let {
            pendingSpeech = null
            speak(it)
        }
    }

    private fun speak(text: String) {
        if (ttsFailed) {
            setState(GhostView.VoiceState.IDLE)
            ui.onSad()
            toast(R.string.tts_missing)
            return
        }
        val engine = tts
        if (engine == null || !ttsReady) {
            pendingSpeech = text
            return
        }
        currentUtterance = "u" + (++utteranceCounter)
        val r = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, currentUtterance)
        if (r == TextToSpeech.ERROR) {
            setState(GhostView.VoiceState.IDLE)
            ui.onSad()
        }
    }

    private fun speakLocal(resId: Int) {
        setState(GhostView.VoiceState.THINKING)
        speak(appContext.getString(resId))
    }

    // =====================================================================
    // Speech to text
    // =====================================================================

    private fun recognizerIntent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-JO")
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ar-JO")
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, appContext.packageName)
        // Small children pause a lot: wait a little longer before cutting them off
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2200L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1800L)
    }

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            ui.onSad()
            toast(R.string.stt_missing)
            return
        }
        if (appContext.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ui.onSad()
            toast(R.string.mic_denied)
            return
        }
        if (!AiConfig.hasKeys()) {
            ui.onSad()
            speakLocal(R.string.say_not_ready)
            return
        }

        // Forget old context after a long pause
        if (System.currentTimeMillis() - lastActivity > 5 * 60 * 1000L) history.clear()

        token++
        val myToken = token
        val sr = recognizer ?: SpeechRecognizer.createSpeechRecognizer(appContext).also { recognizer = it }
        sr.setRecognitionListener(listenerFor(myToken))
        sr.startListening(recognizerIntent())
        setState(GhostView.VoiceState.LISTENING)
        handler.postDelayed(listenTimeout, 15000L)
    }

    private fun listenerFor(myToken: Int) = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onRmsChanged(rmsdB: Float) {
            if (myToken == token && state == GhostView.VoiceState.LISTENING) {
                ui.onLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
            }
        }

        override fun onError(error: Int) {
            if (myToken != token) return
            handler.removeCallbacks(listenTimeout)
            ui.onLevel(0f)
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> speakLocal(R.string.say_not_heard)
                SpeechRecognizer.ERROR_NETWORK,
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                SpeechRecognizer.ERROR_SERVER -> speakLocal(R.string.say_no_internet)
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                    recognizer?.destroy()
                    recognizer = null
                    setState(GhostView.VoiceState.IDLE)
                }
                SpeechRecognizer.ERROR_CLIENT -> setState(GhostView.VoiceState.IDLE)
                else -> {
                    setState(GhostView.VoiceState.IDLE)
                    ui.onSad()
                }
            }
        }

        override fun onResults(results: Bundle?) {
            if (myToken != token) return
            handler.removeCallbacks(listenTimeout)
            ui.onLevel(0f)
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()
            if (text.isEmpty()) speakLocal(R.string.say_not_heard) else ask(text.take(300))
        }
    }

    // =====================================================================
    // AI answer
    // =====================================================================

    private fun ask(userText: String) {
        setState(GhostView.VoiceState.THINKING)
        val myToken = token
        val keys = AiConfig.keys()
        val messages = ArrayList(history)
        messages.add("user" to userText)

        try {
            executor.execute {
                val result = completeWithFallback(keys, messages)
                handler.post { if (myToken == token) onAnswer(userText, result) }
            }
        } catch (e: java.util.concurrent.RejectedExecutionException) {
            setState(GhostView.VoiceState.IDLE)
        }
    }

    /** Tries each key in turn; moves on to the next one when a key is rate-limited or rejected. */
    private fun completeWithFallback(
        keys: List<String>,
        messages: List<Pair<String, String>>
    ): ChatClient.Result {
        var last: ChatClient.Result = ChatClient.Result.Err(ChatClient.ErrorKind.AUTH)
        for (key in keys) {
            val result = ChatClient.complete(key, AiConfig.MODEL, messages)
            if (result is ChatClient.Result.Ok) return result
            last = result
            val kind = (result as ChatClient.Result.Err).kind
            if (kind != ChatClient.ErrorKind.RATE && kind != ChatClient.ErrorKind.AUTH) return result
        }
        return last
    }

    private fun onAnswer(userText: String, result: ChatClient.Result) {
        when (result) {
            is ChatClient.Result.Ok -> {
                val clean = SafetyFilter.clean(result.text)
                if (clean.isBlank()) {
                    speakLocal(R.string.say_try_later)
                } else {
                    history.add("user" to userText)
                    history.add("assistant" to clean)
                    while (history.size > 6) {
                        history.removeAt(0)
                        history.removeAt(0)
                    }
                    lastActivity = System.currentTimeMillis()
                    speak(clean)
                }
            }
            is ChatClient.Result.Err -> when (result.kind) {
                ChatClient.ErrorKind.NETWORK -> speakLocal(R.string.say_no_internet)
                ChatClient.ErrorKind.AUTH -> {
                    setState(GhostView.VoiceState.IDLE)
                    ui.onSad()
                    toast(R.string.key_invalid)
                }
                else -> speakLocal(R.string.say_try_later)
            }
        }
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private fun setState(newState: GhostView.VoiceState) {
        state = newState
        if (newState != GhostView.VoiceState.LISTENING) {
            handler.removeCallbacks(listenTimeout)
            ui.onLevel(0f)
        }
        ui.onState(newState)
    }

    private fun toast(resId: Int) {
        Toast.makeText(appContext, resId, Toast.LENGTH_LONG).show()
    }
}
