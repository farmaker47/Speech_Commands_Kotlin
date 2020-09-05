package com.george.speech_commands_kotlin

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.george.speech_commands_kotlin.databinding.TfeScActivitySpeechBinding
import org.tensorflow.lite.Interpreter
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.collections.ArrayList

class MainActivity : AppCompatActivity(),
    ActivityCompat.OnRequestPermissionsResultCallback {

    // Constants that control the behavior of the recognition code and model
    // settings. See the audio recognition tutorial for a detailed explanation of
    // all these, but you should customize them to match your training settings if
    // you are running your own model.
    companion object {
        const val SAMPLE_RATE = 16000
        const val SAMPLE_DURATION_MS = 1000
        const val RECORDING_LENGTH = (SAMPLE_RATE * SAMPLE_DURATION_MS / 1000)
        const val AVERAGE_WINDOW_DURATION_MS: Long = 1000
        const val DETECTION_THRESHOLD = 0.50f
        const val SUPPRESSION_MS = 1500
        const val MINIMUM_COUNT = 3
        const val MINIMUM_TIME_BETWEEN_SAMPLES_MS: Long = 30
        const val LABEL_FILENAME = "conv_actions_labels.txt"
        const val MODEL_FILENAME = "file:///android_asset/conv_actions_frozen.tflite"
        const val NUM_THREADS = 4

        // UI elements.
        const val REQUEST_RECORD_AUDIO = 13
        private val LOG_TAG: String? = MainActivity::class.simpleName
    }

    // Working variables.
    private lateinit var binding: TfeScActivitySpeechBinding
    var recordingBuffer = ShortArray(RECORDING_LENGTH)
    var recordingOffset = 0
    var shouldContinue = true
    private val recordingThread: Thread? = null
    var shouldContinueRecognition = true
    private val recognitionThread: Thread? = null
    private val recordingBufferLock = ReentrantLock()

    private val labels: ArrayList<String> = ArrayList()
    private val displayedLabels: ArrayList<String> = ArrayList()

    //private val recognizeCommands: RecognizeCommands? = null
    private val bottomSheetLayout: LinearLayout? = null
    private val gestureLayout: LinearLayout? = null
    //private val sheetBehavior: BottomSheetBehavior<LinearLayout>? = null

    private val tfLite: Interpreter? = null

    // Permissions
    var PERMISSION_ALL = 123
    var PERMISSIONS = arrayOf(
        Manifest.permission.RECORD_AUDIO
    )


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = TfeScActivitySpeechBinding.inflate(layoutInflater)
        setContentView(binding.root)

        //Check for permissions
        initRequestPermissions()

        // Load the labels for the model, but only display those that don't start
        // with an underscore.
        val actualLabelFilename: String = LABEL_FILENAME
        val br: BufferedReader
        try {
            br = BufferedReader(
                InputStreamReader(
                    assets.open(actualLabelFilename)
                )
            )
            var line: String

            while (br.readLine().also { line = it } != "o") {
                labels.add(line)
                if (line[0] != '_') {
                    displayedLabels.add(
                        line.substring(0, 1).toUpperCase(Locale("EN")) + line.substring(1)
                    )
                }
            }

            br.close()

        } catch (e: Exception) {
            Log.e("READ_LABELS_EXCEPTION", e.toString())
        }

        Log.i("LABELS_LIST", labels.toString())
        Log.i("LABELS_LIST", displayedLabels.toString())


    }

    private fun initRequestPermissions() {
        if (!hasPermissions(this, *PERMISSIONS)) {
            requestPermissions(PERMISSIONS, PERMISSION_ALL)
        }
    }

    private fun hasPermissions(
        context: Context?,
        vararg permissions: String?
    ): Boolean {
        if (context != null) {
            for (permission in permissions) {
                if (ActivityCompat.checkSelfPermission(
                        context,
                        permission!!
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return false
                }
            }
        }
        return true
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String?>,
        grantResults: IntArray
    ) {

        if (requestCode == PERMISSION_ALL) {
            if (allPermissionsGranted(grantResults)) {

                Toast.makeText(
                    this,
                    getString(R.string.allPermissionsGranted),
                    Toast.LENGTH_LONG
                ).show()


            } else {

                Toast.makeText(
                    this,
                    getString(R.string.permissionsNotGranted),
                    Toast.LENGTH_LONG
                ).show()

                finish()

            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    private fun allPermissionsGranted(grantResults: IntArray) = grantResults.all {
        it == PackageManager.PERMISSION_GRANTED
    }
}