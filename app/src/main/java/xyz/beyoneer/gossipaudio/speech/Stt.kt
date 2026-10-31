package xyz.beyoneer.gossipaudio.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

/**
 * Speech-to-text with partial results. Uses the system recognizer (on-device
 * when the language pack is installed, cloud otherwise) and never crashes if
 * the device has no recognizer at all.
 */
class Stt(private val context: Context) {

    interface Listener {
        fun onListening()
        fun onPartial(text: String)
        fun onResult(text: String)
        fun onError(message: String)
        fun onEnd()
    }

    private var recognizer: SpeechRecognizer? = null
    private var listener: Listener? = null
    private var destroyed = false

    fun isAvailable(): Boolean = try {
        SpeechRecognizer.isRecognitionAvailable(context)
    } catch (_: Throwable) {
        false
    }

    fun start(languageTag: String, preferOffline: Boolean, listener: Listener) {
        this.listener = listener
        if (!isAvailable()) {
            listener.onError("No speech recognizer on this device. Install Google app or use your keyboard's mic.")
            listener.onEnd()
            return
        }
        stop()
        try {
            val rec = SpeechRecognizer.createSpeechRecognizer(context)
            recognizer = rec
            rec.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) = listener.onListening()
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit

                override fun onError(error: Int) {
                    listener.onError(describe(error))
                    listener.onEnd()
                }

                override fun onResults(results: Bundle?) {
                    val text = best(results)
                    if (text.isBlank()) {
                        listener.onError("Heard nothing but vibes. Try again?")
                    } else {
                        listener.onResult(text)
                    }
                    listener.onEnd()
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val text = best(partialResults)
                    if (text.isNotBlank()) listener.onPartial(text)
                }

                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, Locale.getDefault().toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, preferOffline)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1800)
            }
            rec.startListening(intent)
        } catch (t: Throwable) {
            listener.onError("Could not start listening: ${t.message ?: "unknown"}")
            listener.onEnd()
        }
    }

    fun stop() {
        try {
            recognizer?.stopListening()
            recognizer?.cancel()
            recognizer?.destroy()
        } catch (_: Throwable) {
        }
        recognizer = null
    }

    fun destroy() {
        destroyed = true
        stop()
        listener = null
    }

    private fun best(bundle: Bundle?): String {
        val list = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        return list?.firstOrNull()?.trim() ?: ""
    }

    private fun describe(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "Microphone hiccup. Try again."
        SpeechRecognizer.ERROR_CLIENT -> "Recognizer got confused. Tap the mic again."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is needed for dictation."
        SpeechRecognizer.ERROR_NETWORK -> "Network recognizer unavailable — try offline mode."
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "The recognizer timed out. One more try?"
        SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch that. Speak like you mean it."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Already listening…"
        SpeechRecognizer.ERROR_SERVER -> "Speech server said no. Try again later."
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Silence detected. Very mysterious of you."
        else -> "Speech recognition error ($error)"
    }
}
