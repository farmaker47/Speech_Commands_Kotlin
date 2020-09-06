package com.george.speech_commands_kotlin

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.*

class RecognizeCommands(contxt: Context) {

    private var context: Context = contxt
    private val labels: ArrayList<String> = ArrayList()
    private val displayedLabels: ArrayList<String> = ArrayList()

    init {

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

}