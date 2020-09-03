package com.george.speech_commands_kotlin

import android.os.Bundle
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import org.tensorflow.lite.Interpreter
import java.util.*
import java.util.concurrent.locks.ReentrantLock

class MainActivity : AppCompatActivity() {

    // Constants that control the behavior of the recognition code and model
    // settings. See the audio recognition tutorial for a detailed explanation of
    // all these, but you should customize them to match your training settings if
    // you are running your own model.
    companion object{
        const val SAMPLE_RATE = 16000
        const val SAMPLE_DURATION_MS = 1000
        const val RECORDING_LENGTH = (SAMPLE_RATE * SAMPLE_DURATION_MS / 1000)
        const val AVERAGE_WINDOW_DURATION_MS: Long = 1000
        const val DETECTION_THRESHOLD = 0.50f
        const val SUPPRESSION_MS = 1500
        const val MINIMUM_COUNT = 3
        const val MINIMUM_TIME_BETWEEN_SAMPLES_MS: Long = 30
        const val LABEL_FILENAME = "file:///android_asset/conv_actions_labels.txt"
        const val MODEL_FILENAME = "file:///android_asset/conv_actions_frozen.tflite"
        const val NUM_THREADS = 4

        // UI elements.
        const val REQUEST_RECORD_AUDIO = 13
        private val LOG_TAG: String? = MainActivity::class.simpleName
    }

    // Working variables.
    var recordingBuffer = ShortArray(RECORDING_LENGTH)
    var recordingOffset = 0
    var shouldContinue = true
    private val recordingThread: Thread? = null
    var shouldContinueRecognition = true
    private val recognitionThread: Thread? = null
    private val recordingBufferLock = ReentrantLock()

    private val labels: List<String> = ArrayList()
    private val displayedLabels: List<String> = ArrayList()
    private val recognizeCommands: RecognizeCommands? = null
    private val bottomSheetLayout: LinearLayout? = null
    private val gestureLayout: LinearLayout? = null
    private val sheetBehavior: BottomSheetBehavior<LinearLayout>? = null

    private val tfLite: Interpreter? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }
}