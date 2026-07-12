package com.carai.maintenance.audio

import android.content.Context
import android.content.res.AssetFileDescriptor
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.exp
import kotlin.math.min

private const val MODEL_FILE_NAME = "sound_model.tflite"
private const val LABEL_FILE_NAME = "labels.txt"

/**
 * assets/sound_model.tflite + assets/labels.txt 를 정확한 이름으로 로드해서
 * 추론하는 분류기입니다. (다른 .tflite 파일이 assets에 섞여 있어도 이 두 이름만 봅니다)
 */
class TFLiteSoundClassifier(context: Context) : SoundClassifier {

    private val interpreter: Interpreter
    private val labels: List<String>
    private val inputShape: IntArray
    private val inputSize: Int
    private val outputSize: Int

    /** 입력이 단순 1차원 파형이 아니라 스펙트로그램 등 특수 형태로 의심되면 true */
    val looksLikeNonRawWaveformInput: Boolean

    init {
        interpreter = Interpreter(loadModelFile(context, MODEL_FILE_NAME))
        labels = context.assets.open(LABEL_FILE_NAME).bufferedReader().readLines()
            .map { it.trim() }
            .filter { it.isNotBlank() }

        inputShape = interpreter.getInputTensor(0).shape()
        inputSize = inputShape.fold(1) { acc, v -> acc * v } / inputShape[0].coerceAtLeast(1)
        outputSize = interpreter.getOutputTensor(0).shape().last()

        // 입력 텐서가 3차원 이상이거나, 마지막 차원이 원시 파형이라기엔 너무 작으면
        // (예: 스펙트로그램 프레임 형태) 특수 전처리가 필요한 모델일 가능성이 큽니다.
        looksLikeNonRawWaveformInput = inputShape.size > 2 || inputSize < 1000
    }

    fun debugInfo(): String =
        "입력 shape=${inputShape.joinToString(",", "[", "]")}, 출력 클래스 수=$outputSize, 라벨 수=${labels.size}"

    override fun classify(samples: DoubleArray, sampleRate: Int): SoundResult {
        val input = FloatArray(inputSize) { i ->
            if (i < samples.size) samples[i].toFloat() else 0f
        }
        val inputBuffer = arrayOf(input)
        val output = arrayOf(FloatArray(outputSize))

        interpreter.run(inputBuffer, output)

        val probs = softmaxIfNeeded(output[0])

        var bestIdx = 0
        var bestVal = probs[0]
        for (i in probs.indices) {
            if (probs[i] > bestVal) {
                bestVal = probs[i]
                bestIdx = i
            }
        }

        val label = if (bestIdx < labels.size) labels[bestIdx] else "클래스 #$bestIdx"
        val mismatchNote = if (labels.size != outputSize) {
            " (주의: 라벨 ${labels.size}개 vs 모델 출력 ${outputSize}개, 개수가 달라요)"
        } else ""

        val note = if (looksLikeNonRawWaveformInput) {
            " 이 모델은 원시 음성 파형이 아닌 특수 입력(예: 스펙트로그램) 형태로 보여 결과가 부정확할 수 있습니다."
        } else ""

        return SoundResult(
            label = label,
            confidence = bestVal.coerceIn(0f, 1f),
            dominantFrequencyHz = 0.0,
            loudnessDb = 0.0,
            detail = "학습된 AI 모델의 분류 결과입니다.$mismatchNote$note"
        )
    }

    /** 출력값이 이미 0~1 확률(합이 약 1)이면 그대로, 아니면 소프트맥스로 정규화 */
    private fun softmaxIfNeeded(raw: FloatArray): FloatArray {
        val sum = raw.sum()
        val allInRange = raw.all { it in -0.0001f..1.0001f }
        if (allInRange && sum in 0.98f..1.02f) {
            return raw
        }
        val max = raw.maxOrNull() ?: 0f
        val exps = FloatArray(raw.size) { i -> exp((raw[i] - max).toDouble()).toFloat() }
        val expSum = exps.sum().coerceAtLeast(1e-9f)
        return FloatArray(raw.size) { i -> exps[i] / expSum }
    }

    private fun loadModelFile(context: Context, assetName: String): MappedByteBuffer {
        val afd: AssetFileDescriptor = context.assets.openFd(assetName)
        FileInputStream(afd.fileDescriptor).use { input ->
            val channel = input.channel
            return channel.map(FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.declaredLength)
        }
    }
}

/** assets에 정확한 이름의 모델/라벨 파일이 있는지, 로드가 실제로 되는지 상태 */
sealed class ModelStatus {
    data class Found(val modelFile: String, val labelFile: String, val debugInfo: String, val warning: String?) : ModelStatus()
    data class LoadFailed(val modelFile: String, val reason: String) : ModelStatus()
    object NotFound : ModelStatus()
}

object SoundClassifierProvider {

    private fun filesExist(context: Context): Boolean {
        val files = context.assets.list("") ?: emptyArray()
        return MODEL_FILE_NAME in files && LABEL_FILE_NAME in files
    }

    fun checkStatus(context: Context): ModelStatus {
        if (!filesExist(context)) return ModelStatus.NotFound
        return try {
            val classifier = TFLiteSoundClassifier(context)
            val warning = if (classifier.looksLikeNonRawWaveformInput) {
                "입력 형태가 단순 파형이 아닌 것 같습니다. 결과가 이상하면 이 때문일 수 있어요."
            } else null
            ModelStatus.Found(MODEL_FILE_NAME, LABEL_FILE_NAME, classifier.debugInfo(), warning)
        } catch (e: Exception) {
            ModelStatus.LoadFailed(MODEL_FILE_NAME, e.message ?: e.toString())
        }
    }

    fun createAiClassifier(context: Context): SoundClassifier? {
        if (!filesExist(context)) return null
        return try {
            TFLiteSoundClassifier(context)
        } catch (e: Exception) {
            null
        }
    }

    fun createHeuristicClassifier(): SoundClassifier = HeuristicSoundClassifier()
}
