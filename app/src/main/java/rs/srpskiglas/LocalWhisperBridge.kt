package rs.srpskiglas

import android.content.Context
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig

object LocalWhisperBridge {
    private const val MODEL_DIR = "sherpa-onnx-whisper-tiny"
    private var recognizer: OfflineRecognizer? = null

    @JvmStatic
    @Synchronized
    fun ensureLoaded(context: Context) {
        if (recognizer != null) return
        val model = OfflineModelConfig(
            whisper = OfflineWhisperModelConfig(
                encoder = "$MODEL_DIR/tiny-encoder.int8.onnx",
                decoder = "$MODEL_DIR/tiny-decoder.int8.onnx",
                language = "sr",
                task = "transcribe"
            ),
            tokens = "$MODEL_DIR/tiny-tokens.txt",
            numThreads = 4,
            provider = "cpu",
            modelType = "whisper"
        )
        recognizer = OfflineRecognizer(
            assetManager = context.assets,
            config = OfflineRecognizerConfig(modelConfig = model)
        )
    }

    @JvmStatic
    fun transcribe(context: Context, samples: FloatArray, sampleRate: Int): String {
        ensureLoaded(context)
        val r = recognizer ?: return ""
        val stream = r.createStream()
        return try {
            stream.setOption("language", "sr")
            stream.setOption("task", "transcribe")
            stream.acceptWaveform(samples, sampleRate)
            r.decode(stream)
            r.getResult(stream).text.trim()
        } finally {
            stream.release()
        }
    }

    @JvmStatic
    @Synchronized
    fun release() {
        recognizer?.release()
        recognizer = null
    }
}
