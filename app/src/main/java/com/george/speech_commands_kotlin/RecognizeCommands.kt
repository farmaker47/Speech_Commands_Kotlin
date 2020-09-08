package com.george.speech_commands_kotlin

import android.content.Context
import android.util.Log
import android.util.Pair
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.*
import com.george.speech_commands_kotlin.RecognizeCommands.ScoreForSorting as ScoreForSorting1

class RecognizeCommands(contxt: Context) {

    private var context: Context = contxt
    private val labels: ArrayList<String> = ArrayList()
    private val displayedLabels: ArrayList<String> = ArrayList()

    // Configuration settings.
    private val averageWindowDurationMs: Long = MainActivity.AVERAGE_WINDOW_DURATION_MS
    private val detectionThreshold = MainActivity.DETECTION_THRESHOLD
    private val suppressionMs = MainActivity.SUPPRESSION_MS
    private val minimumCount = MainActivity.MINIMUM_COUNT
    private val minimumTimeBetweenSamplesMs: Long = MainActivity.MINIMUM_TIME_BETWEEN_SAMPLES_MS

    // Working variables.
    private val previousResults: Deque<Pair<Long, FloatArray>> =
        ArrayDeque()
    private val SILENCE_LABEL = "_silence_"
    private var previousTopLabel: String = SILENCE_LABEL
    private var previousTopLabelTime: Long = Long.MIN_VALUE
    private var previousTopLabelScore = 0f

    private val MINIMUM_TIME_FRACTION: Long = 4

    init {

    }

    fun loadLabelsFromAssetsFolder(): ArrayList<String> {
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

        return labels
    }

    /**
     * Holds information about what's been recognized.
     */
    class RecognitionResult(
        val foundCommand: String,
        val score: Float,
        val isNewCommand: Boolean
    )

    private class ScoreForSorting(val score: Float, val index: Int) :
        Comparable<ScoreForSorting?> {

        override fun compareTo(other: ScoreForSorting?): Int {
            return if (score > other!!.score) {
                -1
            } else if (score < other.score) {
                1
            } else {
                0
            }
        }
    }

    fun processLatestResults(
        currentResults: FloatArray,
        currentTimeMS: Long
    ): RecognitionResult? {

        if (currentResults.size != labels.size) {
            throw RuntimeException(
                "The results for recognition should contain "
                        + labels.size
                        + " elements, but there are "
                        + currentResults.size
            )
        }

        if (!previousResults.isEmpty() && currentTimeMS < previousResults.first.first) {
            throw RuntimeException(
                "You must feed results in increasing time order, but received a timestamp of "
                        + currentTimeMS
                        + " that was earlier than the previous one of "
                        + previousResults.first.first
            )
        }
        var howManyResults = previousResults.size
        // Ignore any results that are coming in too frequently.
        if (howManyResults > 1) {
            val timeSinceMostRecent = currentTimeMS - previousResults.last.first
            if (timeSinceMostRecent < minimumTimeBetweenSamplesMs) {
                return RecognitionResult(previousTopLabel, previousTopLabelScore, false)
            }
        }

        // Add the latest results to the head of the queue.
        previousResults.addLast(
            Pair(
                currentTimeMS,
                currentResults
            )
        )

        // Prune any earlier results that are too old for the averaging window.
        val timeLimit = currentTimeMS - averageWindowDurationMs
        while (previousResults.first.first < timeLimit) {
            previousResults.removeFirst()
        }
        howManyResults = previousResults.size

        // If there are too few results, assume the result will be unreliable and
        // bail.
        val earliestTime = previousResults.first.first
        val samplesDuration = currentTimeMS - earliestTime
        Log.v("Number of Results: ", howManyResults.toString())
        Log.v(
            "Duration < WD/FRAC?",
            (samplesDuration < averageWindowDurationMs / MINIMUM_TIME_FRACTION).toString()
        )
        if (howManyResults < minimumCount //        || (samplesDuration < (averageWindowDurationMs / MINIMUM_TIME_FRACTION))
        ) {
            Log.v("RecognizeResult", "Too few results")
            return RecognitionResult(previousTopLabel, 0.0f, false)
        }

        // Calculate the average score across all the results in the window.
        val averageScores = FloatArray(labels.size)
        for (previousResult in previousResults) {
            val scoresTensor = previousResult.second
            var i = 0
            while (i < scoresTensor.size) {
                averageScores[i] += scoresTensor[i] / howManyResults
                ++i
            }
        }

        // Sort the averaged results in descending score order.
        val sortedAverageScores =
            arrayOfNulls<ScoreForSorting1>(labels.size)
        for (i in 0 until labels.size) {
            sortedAverageScores[i] = ScoreForSorting1(averageScores[i], i)
        }
        Arrays.sort(sortedAverageScores)

        // See if the latest top score is enough to trigger a detection.
        val currentTopIndex = sortedAverageScores[0]!!.index
        val currentTopLabel = labels[currentTopIndex]
        val currentTopScore = sortedAverageScores[0]!!.score
        // If we've recently had another label trigger, assume one that occurs too
        // soon afterwards is a bad result.
        val timeSinceLastTop: Long =
            if (previousTopLabel == SILENCE_LABEL || previousTopLabelTime == Long.MIN_VALUE) {
                Long.MAX_VALUE
            } else {
                currentTimeMS - previousTopLabelTime
            }
        val isNewCommand: Boolean
        if (currentTopScore > detectionThreshold && timeSinceLastTop > suppressionMs) {
            previousTopLabel = currentTopLabel
            previousTopLabelTime = currentTimeMS
            previousTopLabelScore = currentTopScore
            isNewCommand = true
        } else {
            isNewCommand = false
        }
        return RecognitionResult(currentTopLabel, currentTopScore, isNewCommand)
    }

}