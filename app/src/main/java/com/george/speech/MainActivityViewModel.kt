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

import android.app.Application
import android.content.Context
import android.content.res.AssetManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.george.speech.MainActivity.Companion.LOG_TAG
import com.george.speech.MainActivity.Companion.RECORDING_LENGTH
import com.george.speech.MainActivity.Companion.SAMPLE_RATE
import com.google.common.io.ByteStreams
import kotlinx.coroutines.*
import org.koin.core.KoinComponent
import org.koin.core.get
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.security.SecureRandom
import java.security.spec.AlgorithmParameterSpec
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec


class MainActivityViewModel(application: Application) : AndroidViewModel(application),
    KoinComponent {

    private lateinit var tfLite: Interpreter
    private val context: Context = application
    private val recognizeCommands: RecognizeCommands = get()
    private val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"

    // Working variables.
    var recordingBuffer =
        ShortArray(RECORDING_LENGTH)
    var recordingOffset = 0
    var shouldContinue = true
    var shouldContinueRecognition = true
    private val recordingBufferLock = ReentrantLock()
    //private lateinit var nnApiDelegate: NnApiDelegate

    private val _lastProcessingTimeMs = MutableLiveData<Long>()
    val lastProcessingTimeMs: LiveData<Long>
        get() = _lastProcessingTimeMs

    private val _result = MutableLiveData<RecognizeCommands.RecognitionResult>()
    val result: LiveData<RecognizeCommands.RecognitionResult>
        get() = _result

    private val _labels = MutableLiveData<ArrayList<String>>()
    val labels: LiveData<ArrayList<String>>
        get() = _labels

    private val _numThreads = MutableLiveData<Int>()
    val numThreads: LiveData<Int>
        get() = _numThreads


    init {
        loadModelFromAssetsFolder(1)
        _labels.value = recognizeCommands.loadLabelsFromAssetsFolder()

        //loadTfliteToByteArray()
        //loadAndEncryptTFLiteFile()
        //val (secretKey, iv) = generateAESKeyAndIV()
        //println("AES Key (Base64): ${Base64.getEncoder().encodeToString(secretKey.encoded)}")
        //println("IV Key (Base64): ${Base64.getEncoder().encodeToString(iv)}")

    }

    private fun generateAESKeyAndIV(): Pair<SecretKey, ByteArray> {
        // Generate a 32-byte AES key
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256) // for AES-256
        val secretKey = keyGen.generateKey()

        // Generate a 16-byte IV key
        val iv = ByteArray(16)
        val secureRandom = SecureRandom()
        secureRandom.nextBytes(iv)

        return Pair(secretKey, iv)
    }

    private fun String.fromBase64(): ByteArray = Base64.getDecoder().decode(this)

    // This function helps encrypt and save the TFLite file to output directory
    // e.g com.george.speech_commands_kotlin/Speech Commands Kotlin/.._encrypted_file.tflite
    // https://developer.android.com/reference/javax/crypto/Cipher
    // https://developer.android.com/guide/topics/security/cryptography#encrypt-message
    private fun loadAndEncryptTFLiteFile() {
        val inputStream = context.assets.open(MainActivity.MODEL_FILENAME)
        val byteArray: ByteArray = ByteStreams.toByteArray(inputStream)

        val decodedKey = getAPIKey().fromBase64()
        val ivByteArray = getIVKey().fromBase64()

        // Rebuild key using SecretKeySpec
        val originalKey: SecretKey =
            SecretKeySpec(decodedKey, "AES")//(decodedKey, 0, decodedKey.size, "AES")
        val ivSpec: AlgorithmParameterSpec = IvParameterSpec(ivByteArray)
        // Encrypt the bytearray
        val cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING")
        cipher.init(Cipher.ENCRYPT_MODE, originalKey, ivSpec)
        // Encrypted ByteArray
        val cipherByteArray: ByteArray = cipher.doFinal(byteArray)

        // Generate the File
        val file = File(
            getOutputDirectory(context),
            SimpleDateFormat(
                FILENAME_FORMAT, Locale.US
            ).format(1) + "_encrypted_file.tflite"
        )

        // Write to FileOutputStream
        try {
            if (!file.exists()) {
                file.createNewFile()
            }
            val fos = FileOutputStream(file, false)
            fos.write(cipherByteArray)
            fos.close()
        } catch (e: Exception) {
            Log.e("Error_of_byte_array", e.message ?: "Error writing to output directory")
        }

    }

    private fun loadTfliteToByteArray() {
        val inputStream = context.assets.open(MainActivity.MODEL_FILENAME)
        val byteArray: ByteArray = ByteStreams.toByteArray(inputStream)

        // Generate the File
        val file = File(
            getOutputDirectory(context),
            SimpleDateFormat(
                FILENAME_FORMAT, Locale.US
            ).format(1) + "_dummy_file.tflite"
        )

        // Write to FileOutputStream
        try {
            if (!file.exists()) {
                file.createNewFile()
            }
            val fos = FileOutputStream(file, false)
            fos.write(byteArray)
            fos.close()
        } catch (e: Exception) {
            Log.e("Error_of_byte_array", e.message ?: "Error writing to output directory")
        }


    }

    private fun getOutputDirectory(context: Context): File {
        val appContext = context.applicationContext
        val mediaDir = context.externalMediaDirs.firstOrNull()?.let {
            File(it, appContext.resources.getString(R.string.app_name)).apply { mkdirs() }
        }
        return if (mediaDir != null && mediaDir.exists())
            mediaDir else appContext.filesDir
    }

    fun loadModelFromAssetsFolder(number: Int) {

        // Set number of threads to live data for configuration changes
        _numThreads.value = number

        // Load the model from assets folder
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val tfliteOptions =
                    Interpreter.Options()
                tfliteOptions.setNumThreads(number)

                //nnApiDelegate = NnApiDelegate()
                //tfliteOptions.addDelegate(nnApiDelegate)

                // Load encrypted or non encrypted model file
                tfLite = Interpreter(loadByteBufferFromEncryptedTFLiteFile(), tfliteOptions)
                //tfLite = Interpreter(loadModelFile(context.assets), tfliteOptions)
            } catch (e: java.lang.Exception) {
                throw RuntimeException(e)
            }

            // Resize input of model
            tfLite.resizeInput(
                0,
                intArrayOf(MainActivity.RECORDING_LENGTH, 1)
            )
            tfLite.resizeInput(1, intArrayOf(1))

            // Reads type and shape of input and output tensors, respectively.
            val imageTensorIndex = 0
            val imageShape: IntArray =
                tfLite.getInputTensor(imageTensorIndex).shape()
            Log.i("INPUT_TENSOR_SHAPE", imageShape.contentToString())
            val imageDataType: DataType =
                tfLite.getInputTensor(imageTensorIndex).dataType()
            Log.i("IMAGE_TYPE", imageDataType.toString())

            val probabilityTensorIndex = 0
            val probabilityShape =
                tfLite.getOutputTensor(probabilityTensorIndex).shape()// {1, NUM_CLASSES}

            Log.i("OUTPUT_TENSOR_SHAPE", Arrays.toString(probabilityShape))

            val probabilityDataType: DataType =
                tfLite.getOutputTensor(probabilityTensorIndex).dataType()
            Log.i("OUTPUT_DATA_TYPE", probabilityDataType.toString())

        }

    }

    /**
     * Memory-map the model file in Assets.
     */
    @Throws(IOException::class)
    private fun loadModelFile(
        assets: AssetManager
    ): ByteBuffer {
        val fileDescriptor = assets.openFd(MainActivity.MODEL_FILENAME)
        val inputStream =
            FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(
            FileChannel.MapMode.READ_ONLY,
            startOffset,
            declaredLength
        )
    }

    private fun loadByteBufferFromTextFile(
    ): ByteBuffer {
        val fileInputStream = context.assets.open("dummy_text_file.txt")
        val byteArray: ByteArray = ByteStreams.toByteArray(fileInputStream)
        val byteBuffer = ByteBuffer.allocateDirect(byteArray.size)
        byteBuffer.order(ByteOrder.nativeOrder())
        byteBuffer.rewind()
        byteBuffer.put(byteArray)
        return byteBuffer
    }

    private fun loadByteBufferFromEncryptedTFLiteFile(
    ): ByteBuffer {
        val fileInputStream = context.assets.open("encrypted_file.tflite")
        val byteArray: ByteArray = ByteStreams.toByteArray(fileInputStream)

        // String to ByteArray
        val decodedKey: ByteArray = getAPIKey().toByteArray(Charsets.UTF_8)
        // Rebuild key using SecretKeySpec
        val originalKey: SecretKey =
            SecretKeySpec(decodedKey, "AES")//(decodedKey, 0, decodedKey.size, "AES")
        // IV Key to ByteArray
        val ivByteArray = getIVKey().toByteArray()
        val ivSpec: AlgorithmParameterSpec = IvParameterSpec(ivByteArray)
        // Decrypt the bytearray
        val cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING")
        cipher.init(Cipher.DECRYPT_MODE, originalKey, ivSpec)
        // Decrypted ByteArray
        val cipherByteArray: ByteArray = cipher.doFinal(byteArray)

        val byteBuffer = ByteBuffer.allocateDirect(cipherByteArray.size)
        byteBuffer.order(ByteOrder.nativeOrder())
        byteBuffer.rewind()
        byteBuffer.put(cipherByteArray)
        return byteBuffer
    }

    // Start recording
    fun startRecording() {
        viewModelScope.launch {
            record()
        }
    }

    fun stopRecording() {
        shouldContinue = false
    }

    suspend fun record() = withContext(Dispatchers.Default) {
        // Heavy work
        shouldContinue = true
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)

        // Estimate the buffer size we'll need for this device.
        var bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
            bufferSize = SAMPLE_RATE * 2
        }
        val audioBuffer = ShortArray(bufferSize / 2)

        val record = AudioRecord(
            MediaRecorder.AudioSource.DEFAULT,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(
                LOG_TAG,
                "Audio Record can't initialize!"
            )
        }

        record.startRecording()

        Log.v(
            LOG_TAG,
            "Start recording"
        )

        // Loop, gathering audio data and copying it to a round-robin buffer.
        while (shouldContinue) {
            val numberRead = record.read(audioBuffer, 0, audioBuffer.size)
            val maxLength: Int = recordingBuffer.size
            val newRecordingOffset: Int = recordingOffset + numberRead
            val secondCopyLength = Math.max(0, newRecordingOffset - maxLength)
            val firstCopyLength = numberRead - secondCopyLength
            // We store off all the data for the recognition thread to access. The ML
            // thread will copy out of this buffer into its own, while holding the
            // lock, so this should be thread safe.
            recordingBufferLock.lock()
            try {
                System.arraycopy(
                    audioBuffer,
                    0,
                    recordingBuffer,
                    recordingOffset,
                    firstCopyLength
                )
                System.arraycopy(
                    audioBuffer,
                    firstCopyLength,
                    recordingBuffer,
                    0,
                    secondCopyLength
                )
                recordingOffset = newRecordingOffset % maxLength
            } finally {
                recordingBufferLock.unlock()
            }
        }

        record.stop()
        record.release()
    }

    // Start Recognition
    fun startRecognition() {
        viewModelScope.launch {
            recognize()
        }
    }

    fun stopRecognition() {
        shouldContinueRecognition = false
    }

    private suspend fun recognize() = withContext(Dispatchers.Default) {
        shouldContinueRecognition = true
        // Heavy work
        Log.v(
            LOG_TAG,
            "Start recognition"
        )

        val inputBuffer = ShortArray(RECORDING_LENGTH)
        val floatInputBuffer = Array(RECORDING_LENGTH) { FloatArray(1) }
        val outputScores = Array(1) { FloatArray(_labels.value!!.size) }
        val sampleRateList = intArrayOf(SAMPLE_RATE)

        // Loop, grabbing recorded data and running the recognition model on it.
        while (shouldContinueRecognition) {
            val startTime = Date().time
            // The recording thread places data in this round-robin buffer, so lock to
            // make sure there's no writing happening and then copy it to our own
            // local version.
            recordingBufferLock.lock()
            try {
                val maxLength = recordingBuffer.size
                val firstCopyLength = maxLength - recordingOffset
                val secondCopyLength = recordingOffset
                System.arraycopy(
                    recordingBuffer,
                    recordingOffset,
                    inputBuffer,
                    0,
                    firstCopyLength
                )
                System.arraycopy(
                    recordingBuffer,
                    0,
                    inputBuffer,
                    firstCopyLength,
                    secondCopyLength
                )
            } finally {
                recordingBufferLock.unlock()
            }

            // We need to feed in float values between -1.0f and 1.0f, so divide the
            // signed 16-bit inputs.
            //Log.e("INPUT_BUFFER", inputBuffer.contentToString())
            for (i in 0 until RECORDING_LENGTH) {
                floatInputBuffer[i][0] = inputBuffer[i] / 32767.0f
            }
            //Log.e("INPUT_BUFFER", floatInputBuffer[1].contentToString())
            val inputArray = arrayOf(floatInputBuffer, sampleRateList)

            //val outputMap: MutableMap<Int, Any> = HashMap()
            val outputMap = HashMap<Int, Any>()

            outputMap[0] = outputScores

            // Run the model.
            tfLite.runForMultipleInputsOutputs(inputArray, outputMap)

            // Use the smoother to figure out if we've had a real recognition event.
            val currentTime = System.currentTimeMillis()
            Log.i("INPUT_BUFFER", outputScores[0].contentToString())
            _result.postValue(recognizeCommands.processLatestResults(outputScores[0], currentTime))
            _lastProcessingTimeMs.postValue(Date().time - startTime)
        }

        Log.v(
            LOG_TAG,
            "End recognition"
        )
    }

    override fun onCleared() {
        super.onCleared()
        tfLite.close()
    }
}