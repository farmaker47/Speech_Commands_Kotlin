package com.george.speech_commands_kotlin

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.george.speech_commands_kotlin.databinding.TfeScActivitySpeechBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.BottomSheetCallback
import org.tensorflow.lite.Interpreter
import java.util.concurrent.locks.ReentrantLock
import org.koin.android.viewmodel.ext.android.viewModel

class MainActivity : AppCompatActivity(),
    ActivityCompat.OnRequestPermissionsResultCallback,
    View.OnClickListener, CompoundButton.OnCheckedChangeListener {

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
        const val MODEL_FILENAME = "conv_actions_frozen.tflite"
        const val NUM_THREADS = 4

        // UI elements.
        const val REQUEST_RECORD_AUDIO = 13
        private val LOG_TAG: String? = MainActivity::class.simpleName
    }

    // Working variables.
    private lateinit var bindingActivitySpeechBinding: TfeScActivitySpeechBinding
    var recordingBuffer = ShortArray(RECORDING_LENGTH)
    var recordingOffset = 0
    var shouldContinue = true
    private val recordingThread: Thread? = null
    var shouldContinueRecognition = true
    private val recognitionThread: Thread? = null
    private val recordingBufferLock = ReentrantLock()

    //private val recognizeCommands: RecognizeCommands? = null
    private lateinit var bottomSheet: LinearLayout
    private var sheetBehavior: BottomSheetBehavior<LinearLayout?>? = null

    private var tfLite: Interpreter? = null

    // Permissions
    var PERMISSION_ALL = 123
    var PERMISSIONS = arrayOf(
        Manifest.permission.RECORD_AUDIO
    )

    // Koin DI
    private val viewModel: MainActivityViewModel by viewModel()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bindingActivitySpeechBinding = TfeScActivitySpeechBinding.inflate(layoutInflater)
        setContentView(bindingActivitySpeechBinding.root)
        bindingActivitySpeechBinding.lifecycleOwner = this

        //Check for permissions
        initRequestPermissions()

        // Load Labels
        viewModel.loadLabelsFromAssetsFolder()

        // Load Model
        viewModel.loadModelFromAssetsFolder()

        bindingActivitySpeechBinding.bottomSheetLayout.apiInfoSwitch.setOnCheckedChangeListener(this)

        // BottomSheetBehavior
        bottomSheet = findViewById(R.id.bottom_sheet_layout)
        sheetBehavior = BottomSheetBehavior.from<LinearLayout>(bottomSheet)

        val vto = bindingActivitySpeechBinding.bottomSheetLayout.gestureLayout.viewTreeObserver
        vto.addOnGlobalLayoutListener(
            object : OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    bindingActivitySpeechBinding.bottomSheetLayout.gestureLayout.viewTreeObserver.removeOnGlobalLayoutListener(
                        this
                    )
                    val height =
                        bindingActivitySpeechBinding.bottomSheetLayout.gestureLayout.measuredHeight
                    sheetBehavior?.peekHeight = height
                }
            })
        sheetBehavior?.isHideable = false

        sheetBehavior?.setBottomSheetCallback(
            object : BottomSheetCallback() {
                override fun onStateChanged(
                    bottomSheet: View,
                    newState: Int
                ) {
                    when (newState) {
                        BottomSheetBehavior.STATE_HIDDEN -> {
                        }
                        BottomSheetBehavior.STATE_EXPANDED -> {
                            bindingActivitySpeechBinding.bottomSheetLayout.bottomSheetArrow
                                .setImageResource(R.drawable.icn_chevron_down)
                        }
                        BottomSheetBehavior.STATE_COLLAPSED -> {
                            bindingActivitySpeechBinding.bottomSheetLayout.bottomSheetArrow
                                .setImageResource(R.drawable.icn_chevron_up)
                        }
                        BottomSheetBehavior.STATE_DRAGGING -> {
                        }
                        BottomSheetBehavior.STATE_SETTLING -> bindingActivitySpeechBinding.bottomSheetLayout.bottomSheetArrow
                            .setImageResource(
                                R.drawable.icn_chevron_up
                            )
                    }
                }

                override fun onSlide(
                    bottomSheet: View,
                    slideOffset: Float
                ) {
                }
            })

        bindingActivitySpeechBinding.bottomSheetLayout.plus.setOnClickListener(this)
        bindingActivitySpeechBinding.bottomSheetLayout.minus.setOnClickListener(this)

        bindingActivitySpeechBinding.bottomSheetLayout.sampleRate.text = "$SAMPLE_RATE Hz"

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

    override fun onClick(p0: View?) {
        TODO("Not yet implemented")
    }

    override fun onCheckedChanged(p0: CompoundButton?, p1: Boolean) {
        TODO("Not yet implemented")
        /*backgroundHandler.post(Runnable { tfLite!!.setUseNNAPI(isChecked) })
        if (isChecked) apiSwitchCompat.setText("NNAPI") else apiSwitchCompat.setText("TFLITE")*/
    }
}