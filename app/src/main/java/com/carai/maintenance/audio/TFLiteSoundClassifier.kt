package com.carai.maintenance.audio

import android.content.Context
import android.content.res.AssetFileDescriptor
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Teachable Machine(오디오 프로젝트)이나 직접 학습한 TFLite 모델을 로드해서
 * 실제 추론을 수행하는 분류기입니다.
 *
 * 파일 이름을 정확히 강제하지 않고, assets 안에서 .tflite 파일과 라벨(.txt) 파일을
 * 자동으로 찾아서 사용합니다.
 */
class TFLiteSoundClassifier(
    context: Context,
    private val modelFileName: String,
    private val labelFileName: String?
) : SoundClassifier {

    private val interpreter: Interpreter
    private val labels: List<String>
    private val inputSize: Int

    init {
        interpreter = Interpreter(loadModelFile(context, modelFileName))
        labels = labelFileName?.let {
            context.assets.open(it).bufferedReader().readLines().filter { l -> l.isNotBlank() }
        } ?: emptyList()
        val inputShape = interpreter.getInputTensor(0).shape()
        inputSize = inputShape.last()
    }

    override fun classify(samples: DoubleArray, sampleRate: Int): SoundResult {
        val input = FloatArray(inputSize) { i ->
            if (i < samples.size) samples[i].toFloat() else 0f
        }
        val inputBuffer = arrayOf(input)

        val outputSize = interpreter.getOutputTensor(0).shape().last()
        val output = arrayOf(FloatArray(outputSize))

        interpreter.run(inputBuffer, output)

        val probs = output[0]
        var bestIdx = 0
        var bestVal = probs[0]
        for (i in probs.indices) {
            if (probs[i] > bestVal) {
                bestVal = probs[i]
                bestIdx = i
            }
        }

        val label = labels.getOrElse(bestIdx) { "클래스 #$bestIdx" }

        return SoundResult(
            label = label,
            confidence = bestVal,
            dominantFrequencyHz = 0.0,
            loudnessDb = 0.0,
            detail = "학습된 AI 모델($modelFileName)의 분류 결과입니다."
        )
    }

    private fun loadModelFile(context: Context, assetName: String): MappedByteBuffer {
        val afd: AssetFileDescriptor = context.assets.openFd(assetName)
        FileInputStream(afd.fileDescriptor).use { input ->
            val channel = input.channel
            return channel.map(FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.declaredLength)
        }
    }
}

/** assets 안에서 실제로 찾은 모델/라벨 파일 정보, 혹은 로드 실패 이유 */
sealed class ModelStatus {
    data class Found(val modelFile: String, val labelFile: String?) : ModelStatus()
    data class LoadFailed(val modelFile: String, val reason: String) : ModelStatus()
    object NotFound : ModelStatus()
}

object SoundClassifierProvider {

    /** assets 폴더를 스캔해서 .tflite 파일과 라벨(.txt) 파일을 이름 상관없이 찾음 */
    private fun scan(context: Context): Pair<String?, String?> {
        val files = context.assets.list("") ?: emptyArray()
        val modelFile = files.firstOrNull { it.endsWith(".tflite") }
        val labelFile = files.firstOrNull { it.equals("labels.txt", ignoreCase = true) }
            ?: files.firstOrNull { it.endsWith(".txt") && !it.equals("PUT_MODEL_HERE.txt", true) }
        return modelFile to labelFile
    }

    fun checkStatus(context: Context): ModelStatus {
        val (modelFile, labelFile) = scan(context)
        if (modelFile == null) return ModelStatus.NotFound
        return try {
            // 실제로 로드까지 시도해봐서 진짜 되는지 확인
            TFLiteSoundClassifier(context, modelFile, labelFile)
            ModelStatus.Found(modelFile, labelFile)
        } catch (e: Exception) {
            ModelStatus.LoadFailed(modelFile, e.message ?: e.toString())
        }
    }

    fun createAiClassifier(context: Context): SoundClassifier? {
        val (modelFile, labelFile) = scan(context)
        if (modelFile == null) return null
        return try {
            TFLiteSoundClassifier(context, modelFile, labelFile)
        } catch (e: Exception) {
            null
        }
    }

    fun createHeuristicClassifier(): SoundClassifier = HeuristicSoundClassifier()
}
