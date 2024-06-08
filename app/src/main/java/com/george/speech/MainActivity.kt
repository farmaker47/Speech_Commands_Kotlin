/* Copyright 2021 George Soloupis. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
==============================================================================*/

package com.george.speech

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import com.george.speech.databinding.TfeScActivitySpeechBinding
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.safetynet.SafetyNet
import com.google.android.gms.safetynet.SafetyNetApi.AttestationResponse
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.BottomSheetCallback
import com.scottyab.rootbeer.RootBeer
import com.scottyab.safetynet.SafetyNetHelper
import com.scottyab.safetynet.SafetyNetHelper.SafetyNetWrapperCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.android.viewmodel.ext.android.getViewModel
import org.koin.android.viewmodel.ext.android.viewModel
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.security.SecureRandom
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

        // for logs
        val LOG_TAG: String? = MainActivity::class.simpleName

        // Load native library
        init {
            System.loadLibrary("speech_commands_kotlin")
        }
    }

    // Working variables.
    private lateinit var bindingActivitySpeechBinding: TfeScActivitySpeechBinding
    private lateinit var bottomSheet: LinearLayout
    private var sheetBehavior: BottomSheetBehavior<LinearLayout?>? = null
    private val handler = Handler()
    private var selectedTextView: TextView? = null
    var labels: ArrayList<String> = ArrayList()
    private val mRandom: Random = SecureRandom()
    private var mResult: String? = null

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

        /////////////////////////////////////////////////////////////////////////////////////
        /// START OF TESTS
        // Check offline for rooted device
        // If device is rooted then show a Toast and finish activity
        // More info at https://github.com/scottyab/rootbeer
        // and for devices with busybox use complete root detection method
        // rootBeer.isRootedWithBusyBoxCheck();
        lifecycleScope.launch(Dispatchers.Default) {
            val rootBeer = RootBeer(this@MainActivity)
            if (rootBeer.isRooted && !BuildConfig.DEBUG) {
                //we found indication of root
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Rooted device!! Closing application", Toast.LENGTH_LONG).show()
                        finish()
                    }
            } else {
                //we didn't find indication of root
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Device not rooted", Toast.LENGTH_LONG).show()
                    }

            }
        }

        // Check online for integrity of the device with SafetyNet API
        // https://developer.android.com/training/safetynet/attestation
        sendSafetyNetRequest()

        // Helper function to get a check for a Safety Net Wrapper
        // https://github.com/scottyab/safetynethelper
        getTestResultFromSafetyNetHelper()

        /// END OF TESTS
        /////////////////////////////////////////////////////////////////////////////////////////

        //Check for permissions
        initRequestPermissions()

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

        // Observe ViewModel
        observeViewModel()
    }

    private fun observeViewModel() {

        // Observe number of threads
        viewModel.numThreads.observe(
            this,
            Observer { number ->
                if (number != null) {
                    bindingActivitySpeechBinding.bottomSheetLayout.threads.text = number.toString()
                }
            }
        )

        viewModel.labels.observe(
            this,
            Observer { labelsCommands ->
                if (labelsCommands != null) {
                    labels = labelsCommands
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

        viewModel.lastProcessingTimeMs.observe(this, { time ->
            if (time != null) {
                bindingActivitySpeechBinding.bottomSheetLayout.inferenceInfo.text = "$time ms"
            }
        }
        )
    }

    private fun sendSafetyNetRequest() {
        Log.i(
            LOG_TAG,
            "Sending SafetyNet API request."
        )

        /*
        Create a nonce for this request.
        The nonce is returned as part of the response from the
        SafetyNet API. Here we append the string to a number of random bytes to ensure it larger
        than the minimum 16 bytes required.
        Read out this value and verify it against the original request to ensure the
        response is correct and genuine.
        NOTE: A nonce must only be used once and a different nonce should be used for each request.
        As a more secure option, you can obtain a nonce from your own server using a secure
        connection. Here in this sample, we generate a String and append random bytes, which is not
        very secure. Follow the tips on the Security Tips page for more information:
        https://developer.android.com/training/articles/security-tips.html#Crypto
         */
        // Change the nonce generation to include your own, used once value,
        // ideally from your remote server.
        val nonceData = "Safety Net Sample: " + System.currentTimeMillis()
        val nonce: ByteArray = getRequestNonce(nonceData) ?: byteArrayOf()

        /*
         Call the SafetyNet API asynchronously.
         The result is returned through the success or failure listeners.
         First, get a SafetyNetClient for the foreground Activity.
         Next, make the call to the attestation API. The API key is specified in the gradle build
         configuration and read from the gradle.properties file.
         */
        val client = SafetyNet.getClient(this)
        val task = client.attest(nonce, getSafetyAPIKey())

        task.addOnSuccessListener(this, mSuccessListener)
            .addOnFailureListener(this, mFailureListener)
    }

    /**
     * Generates a 16-byte nonce with additional data.
     * The nonce should also include additional information, such as a user id or any other details
     * you wish to bind to this attestation. Here you can provide a String that is included in the
     * nonce after 24 random bytes. During verification, extract this data again and check it
     * against the request that was made with this nonce.
     */
    private fun getRequestNonce(data: String): ByteArray? {
        val byteStream = ByteArrayOutputStream()
        val bytes = ByteArray(24)
        mRandom.nextBytes(bytes)
        try {
            byteStream.write(bytes)
            byteStream.write(data.toByteArray())
        } catch (e: IOException) {
            return null
        }
        return byteStream.toByteArray()
    }

    /**
     * Called after successfully communicating with the SafetyNet API.
     * The #onSuccess callback receives an
     * [com.google.android.gms.safetynet.SafetyNetApi.AttestationResponse] that contains a
     * JwsResult with the attestation result.
     */
    private val mSuccessListener =
        OnSuccessListener<AttestationResponse> { attestationResponse -> /*
                          Successfully communicated with SafetyNet API.
                          Use result.getJwsResult() to get the signed result data. See the server
                          component of this sample for details on how to verify and parse this result.
                          */
            mResult = attestationResponse.jwsResult
            Log.d(
                "API_RESULT",
                "Success! SafetyNet result:\n$mResult\n"
            )

            /*
                              TODO(developer): Forward this result to your server together with
                              the nonce for verification.
                              You can also parse the JwsResult locally to confirm that the API
                              returned a response by checking for an 'error' field first and before
                              retrying the request with an exponential backoff.

                              NOTE: Do NOT rely on a local, client-side only check for security, you
                              must verify the response on a remote server!
                             */
        }

    /**
     * Called when an error occurred when communicating with the SafetyNet API.
     */
    private val mFailureListener = OnFailureListener { e ->
        // An error occurred while communicating with the service.
        mResult = null
        if (e is ApiException) {
            // An error with the Google Play Services API contains some additional details.
            val apiException = e
            Log.d(
                LOG_TAG, "Error: " +
                        CommonStatusCodes.getStatusCodeString(apiException.statusCode) + ": " +
                        apiException.statusMessage
            )
        } else {
            // A different, unknown type of error occurred.
            Log.d(
                LOG_TAG,
                "ERROR! " + e.message
            )
        }
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
        lifecycleScope.launch {
            delay(2000)
            viewModel.startRecording()
            viewModel.startRecognition()
        }

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
            val threads: String =
                bindingActivitySpeechBinding.bottomSheetLayout.threads.text.toString()
                    .trim { it <= ' ' }
            var numThreads = threads.toInt()
            numThreads++
            bindingActivitySpeechBinding.bottomSheetLayout.threads.text = numThreads.toString()

            // Procedure when plus button is pressed
            threadButtonProcedure(numThreads)
        } else if (v?.id == R.id.minus) {
            val threads: String =
                bindingActivitySpeechBinding.bottomSheetLayout.threads.text.toString()
                    .trim { it <= ' ' }
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

    // SafetyNet Wrapper implementation with result
    // https://github.com/scottyab/safetynethelper
    private fun getTestResultFromSafetyNetHelper() {

        val safetyNetHelper = SafetyNetHelper(getSafetyAPIKey())

        Log.d("SAFETY_HELPER", "SafetyNetHelper start request")
        safetyNetHelper.requestTest(this, object : SafetyNetWrapperCallback {
            override fun error(errorCode: Int, errorMessage: String) {
                //handle and retry depending on errorCode
                Log.d("SAFETY_HELPER", errorMessage)

            }

            override fun success(ctsProfileMatch: Boolean, basicIntegrity: Boolean) {
                Log.d("SAFETY_HELPER",
                    "SafetyNet req success: ctsProfileMatch:$ctsProfileMatch and basicIntegrity, $basicIntegrity"
                )
                when {
                    ctsProfileMatch -> {
                        //profile of the device running your app matches the profile of a device that has passed Android compatibility testing.
                    }
                    basicIntegrity -> {
                        //then the device running your app likely wasn't tampered with, but the device has not necessarily passed Android compatibility testing.
                    }
                    else -> {
                        //handle fail, maybe warn user device is unsupported or in compromised state? (this is up to you!)
                    }
                }
            }
        })

    }

}

external fun getAPIKey(): String
external fun getIVKey(): String
external fun getSafetyAPIKey(): String