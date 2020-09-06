package com.george.speech_commands_kotlin

import android.app.Application
import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import org.koin.core.KoinComponent
import org.koin.core.get
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.*

class MainActivityViewModel(application: Application) : AndroidViewModel(application),
    KoinComponent {

    private lateinit var tfLite: Interpreter
    private val context: Context = application
    private val recognizeCommands: RecognizeCommands = get()

    init {
        recognizeCommands.loadLabelsFromAssetsFolder()
    }

    fun loadModelFromAssetsFolder() {
        // Load the model from assets folder
        try {
            val tfliteOptions =
                Interpreter.Options()
            tfliteOptions.setNumThreads(MainActivity.NUM_THREADS)
            tfLite = Interpreter(
                loadModelFile(
                    context.assets
                ), tfliteOptions
            )
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
}