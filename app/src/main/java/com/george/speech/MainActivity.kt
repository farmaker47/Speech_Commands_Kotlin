package com.george.speech

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.view.View
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Observer
import com.george.speech.databinding.TfeScActivitySpeechBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.BottomSheetCallback
import com.scottyab.rootbeer.RootBeer
import org.koin.android.viewmodel.ext.android.viewModel
import java.util.*


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

        // UI elements.
        val LOG_TAG: String? = MainActivity::class.simpleName
    }

    // Load native library
    init {
        System.loadLibrary("native-lib")
    }

    // Working variables.
    private lateinit var bindingActivitySpeechBinding: TfeScActivitySpeechBinding
    private lateinit var bottomSheet: LinearLayout
    private var sheetBehavior: BottomSheetBehavior<LinearLayout?>? = null
    private val handler = Handler()
    private var selectedTextView: TextView? = null
    var labels: ArrayList<String> = ArrayList()

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

        // Check offline for rooted device
        // If device is rooted then show a Toast and finish activity
        // More info at https://github.com/scottyab/rootbeer
        // and for devices with busybox use complete root detection method
        // rootBeer.isRootedWithBusyBoxCheck();
        val rootBeer = RootBeer(this)
        if (rootBeer.isRooted) {
            //we found indication of root
            Toast.makeText(this, "Rooted device!! Closing application", Toast.LENGTH_LONG).show()
            finish()
        } else {
            //we didn't find indication of root
            //Toast.makeText(this, "Device not rooted", Toast.LENGTH_LONG).show()
        }

        //Check for permissions
        initRequestPermissions()

        // Observe number of threads
        viewModel.numThreads.observe(
            this,
            Observer { number ->
                if (number != null) {
                    bindingActivitySpeechBinding.bottomSheetLayout.threads.text = number.toString()
                }
            }
        )

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

        sheetBehavior?.addBottomSheetCallback(
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

        viewModel.labels.observe(
            this,
            Observer { labelsCommands ->
                if (labelsCommands != null) {
                    labels=labelsCommands
                }
            }
        )

        // Observe viewmodel object
        viewModel.result.observe(
            this,
            Observer { result ->
                if (result != null) {

                    runOnUiThread(
                        Runnable {

                            // If we do have a new command, highlight the right list entry.
                            if (!result.foundCommand.startsWith("_") && result.isNewCommand) {
                                var labelIndex = -1
                                for (i in labels.indices) {
                                    if (labels.get(i) == result.foundCommand) {
                                        labelIndex = i
                                    }
                                }
                                when (labelIndex - 2) {
                                    0 -> selectedTextView = bindingActivitySpeechBinding.yes
                                    1 -> selectedTextView = bindingActivitySpeechBinding.no
                                    2 -> selectedTextView = bindingActivitySpeechBinding.up
                                    3 -> selectedTextView = bindingActivitySpeechBinding.down
                                    4 -> selectedTextView = bindingActivitySpeechBinding.left
                                    5 -> selectedTextView = bindingActivitySpeechBinding.right
                                    6 -> selectedTextView = bindingActivitySpeechBinding.on
                                    7 -> selectedTextView = bindingActivitySpeechBinding.off
                                    8 -> selectedTextView = bindingActivitySpeechBinding.stop
                                    9 -> selectedTextView = bindingActivitySpeechBinding.go
                                }
                                if (selectedTextView != null) {
                                    selectedTextView?.setBackgroundResource(R.drawable.round_corner_text_bg_selected)
                                    val score =
                                        Math.round(result.score * 100).toString() + "%"
                                    selectedTextView?.setText(
                                        selectedTextView?.text.toString() + "\n" + score
                                    )
                                    selectedTextView?.setTextColor(
                                        resources.getColor(android.R.color.holo_orange_light)
                                    )
                                    handler.postDelayed(
                                        Runnable {
                                            val origionalString: String =
                                                selectedTextView?.getText().toString()
                                                    .replace(score, "").trim({ it <= ' ' })
                                            selectedTextView?.text = origionalString
                                            selectedTextView?.setBackgroundResource(
                                                R.drawable.round_corner_text_bg_unselected
                                            )
                                            selectedTextView?.setTextColor(
                                                resources.getColor(android.R.color.black)
                                            )
                                        },
                                        750
                                    )
                                }
                            }
                        })
                    try {
                        // We don't need to run too frequently, so snooze for a bit.
                        Thread.sleep(MINIMUM_TIME_BETWEEN_SAMPLES_MS)
                    } catch (e: InterruptedException) {
                        // Ignore
                    }

                }
            }
        )

        viewModel.lastProcessingTimeMs.observe(
            this,
            Observer { time ->
                if (time != null) {
                    bindingActivitySpeechBinding.bottomSheetLayout.inferenceInfo.text = "$time ms"
                }
            }
        )

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

    override fun onResume() {
        super.onResume()
        viewModel.startRecording()
        viewModel.startRecognition()
    }

    override fun onPause() {
        super.onPause()
        viewModel.stopRecording()
        viewModel.stopRecognition()
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

    override fun onClick(v: View?) {
        if (v?.id == R.id.plus) {
            val threads: String = bindingActivitySpeechBinding.bottomSheetLayout.threads.text.toString().trim { it <= ' ' }
            var numThreads = threads.toInt()
            numThreads++
            bindingActivitySpeechBinding.bottomSheetLayout.threads.text = numThreads.toString()

            // Procedure when plus button is pressed
            threadButtonProcedure(numThreads)
        } else if (v?.id == R.id.minus) {
            val threads: String = bindingActivitySpeechBinding.bottomSheetLayout.threads.text.toString().trim { it <= ' ' }
            var numThreads = threads.toInt()
            if (numThreads == 1) {
                return
            }
            numThreads--
            bindingActivitySpeechBinding.bottomSheetLayout.threads.text = numThreads.toString()

            //Procedure when minus button is pressed
            threadButtonProcedure(numThreads)
        }
    }

    private fun threadButtonProcedure(numThreads: Int) {
        viewModel.loadModelFromAssetsFolder(numThreads)
    }

    override fun onCheckedChanged(p0: CompoundButton?, p1: Boolean) {
        /*backgroundHandler.post(Runnable { tfLite!!.setUseNNAPI(isChecked) })
        if (isChecked) apiSwitchCompat.setText("NNAPI") else apiSwitchCompat.setText("TFLITE")*/
    }
}

external fun getAPIKey(): String
external fun getIVKey(): String