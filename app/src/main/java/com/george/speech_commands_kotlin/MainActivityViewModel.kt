package com.george.speech_commands_kotlin

import android.app.Application
import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.*

class MainActivityViewModel(application: Application) : AndroidViewModel(application) {

    private lateinit var tfLite: Interpreter
    private val context: Context = application
    private val labels: ArrayList<String> = ArrayList()
    private val displayedLabels: ArrayList<String> = ArrayList()

    init {

    }

    fun loadModelFromAssetsFolder() {
        // Load the model from assets folder
        val actualModelFilename: String = MainActivity.MODEL_FILENAME
        try {
            val tfliteOptions =
                Interpreter.Options()
            tfliteOptions.setNumThreads(MainActivity.NUM_THREADS)
            tfLite = Interpreter(
                loadModelFile(
                    context.assets,
                    actualModelFilename
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

    fun loadLabelsFromAssetsFolder() {
        // Load the labels for the model, but only display those that don't start
        // with an underscore.
        val actualLabelFilename: String = MainActivity.LABEL_FILENAME
        val br: BufferedReader
        try {
            br = BufferedReader(
                InputStreamReader(
                    context.assets.open(actualLabelFilename)
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

    /**
     * Memory-map the model file in Assets.
     */
    @Throws(IOException::class)
    private fun loadModelFile(
        assets: AssetManager,
        modelFilename: String
    ): ByteBuffer {
        val fileDescriptor = assets.openFd(modelFilename)
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