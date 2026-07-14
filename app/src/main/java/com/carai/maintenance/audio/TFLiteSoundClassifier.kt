package com.carai.maintenance.audio

import android.content.Context
import android.content.res.AssetFileDescriptor
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.exp

private const val MODEL_FILE_NAME = "sound_model.tflite"
private const val LABEL_FILE_NAME = "labels.txt"

/**
 * assets/sound_model.tflite + assets/labels.txt 를 정확한 이름으로 로드해서
 * 추론하는 분류기입니다.
 *
 * 입력 텐서 shape([1, N])의 N을 [recommendedSampleCount]로 노출해서,
 * 녹음 단계에서 정확히 그 길이만큼만 오디오를 모으도록 합니다.
 */
class TFLiteSoundClassifier(context: Context) : SoundClassifier {

    private val interpreter: Interpreter
    private val labels: List<String>
    private val inputShape: IntArray
    private val inputSize: Int
    private val outputSize: Int

    /** 입력이 단순 1차원 파형이 아니라 스펙트로그램 등 특수 형태로 의심되면 true */
    val looksLikeNonRawWaveformInput: Boolean

    override val recommendedSampleCount: Int
        get() = inputSize

    /** Flex(select TF ops) 델리게이트가 실제로 붙었는지 여부 (진단용) */
    val flexDelegateAttached: Boolean

    init {
        val options = Interpreter.Options()
        // Flex 커널이 멀티스레드에서 NaN을 내는 사례가 보고되어 있어 1개로 고정
        options.setNumThreads(1)

        var flexAttached = false
        try {
            // select-tf-ops 아티팩트가 있으면 이 클래스가 존재합니다.
            // 리플렉션으로 로드해서, 없는 환경에서도 컴파일/실행이 깨지지 않게 합니다.
            val flexDelegateClass = Class.forName("org.tensorflow.lite.flex.FlexDelegate")
            val flexDelegate = flexDelegateClass.getDeclaredConstructor().newInstance() as org.tensorflow.lite.Delegate
            options.addDelegate(flexDelegate)
            flexAttached = true
        } catch (e: Throwable) {
            // FlexDelegate 클래스를 못 찾으면 select-tf-ops가 제대로 안 붙은 것.
            // 그래도 기본 Interpreter로는 로드를 시도합니다 (Flex 없이 되는 모델일 수도 있으니).
            flexAttached = false
        }
        flexDelegateAttached = flexAttached

        interpreter = Interpreter(loadModelFile(context, MODEL_FILE_NAME), options)
        labels = context.assets.open(LABEL_FILE_NAME).bufferedReader().readLines()
            .map { it.trim() }
            .filter { it.isNotBlank() }

        inputShape = interpreter.getInputTensor(0).shape()
        inputSize = inputShape.fold(1) { acc, v -> acc * v } / inputShape[0].coerceAtLeast(1)
        outputSize = interpreter.getOutputTensor(0).shape().last()

        looksLikeNonRawWaveformInput = inputShape.size > 2
    }

    fun debugInfo(): String =
        "입력 shape=${inputShape.joinToString(",", "[", "]")}, 출력 클래스 수=$outputSize, " +
            "라벨 수=${labels.size}, Flex 델리게이트=${if (flexDelegateAttached) "연결됨" else "못 찾음"}"

    override fun classify(samples: DoubleArray, sampleRate: Int): SoundResult {
        // 모델이 요구하는 길이에 정확히 맞춤: 부족하면 0으로 채우고, 넘치면 앞부분만 사용
        val input = FloatArray(inputSize) { i ->
            if (i < samples.size) samples[i].toFloat().coerceIn(-1f, 1f) else 0f
        }
        val inputBuffer = arrayOf(input)
        val output = arrayOf(FloatArray(outputSize))

        try {
            interpreter.run(inputBuffer, output)
        } catch (e: Exception) {
            return SoundResult(
                label = "AI 추론 오류",
                confidence = 0f,
                dominantFrequencyHz = 0.0,
                loudnessDb = 0.0,
                detail = "모델 실행 중 예외 발생: ${e.message ?: e.toString()}"
            )
        }

        val rawOutput = output[0]

        val hasNaN = rawOutput.any { it.isNaN() || it.isInfinite() }
        val isAllZero = rawOutput.all { it == 0f }

        if (hasNaN || isAllZero) {
            val dump = rawOutput.joinToString(", ") { "%.4f".format(it) }
            val flexNote = if (flexDelegateAttached) {
                "Flex 델리게이트는 연결됐지만 그래도 NaN이 나왔습니다. 스레드/연산 자체의 수치 불안정 문제일 수 있습니다."
            } else {
                "Flex 델리게이트를 찾지 못했습니다 (select-tf-ops 의존성이 최종 APK에 제대로 포함 안 됐을 가능성)."
            }
            val inputStats = inputStatsSummary(input)
            return SoundResult(
                label = "출력값 이상",
                confidence = 0f,
                dominantFrequencyHz = 0.0,
                loudnessDb = 0.0,
                detail = "모델이 유효한 값을 내놓지 않았습니다 (raw=[$dump]). $flexNote " +
                    "입력 데이터: $inputStats"
            )
        }

        val probs = softmaxIfNeeded(rawOutput)

        // 상위 3개 클래스를 뽑아서 진단용으로 같이 보여줌 (한글 이름으로 표시)
        val ranked = probs.indices.sortedByDescending { probs[it] }.take(3)
        val topText = ranked.joinToString(" / ") { idx ->
            val raw = if (idx < labels.size) labels[idx] else "클래스#$idx"
            val name = LabelTranslations.localize(raw).displayName
            "$name ${(probs[idx] * 100).toInt()}%"
        }

        val bestIdx = ranked.first()
        val bestVal = probs[bestIdx]
        val rawLabel = if (bestIdx < labels.size) labels[bestIdx] else "클래스 #$bestIdx"
        val localized = LabelTranslations.localize(rawLabel)

        val mismatchNote = if (labels.size != outputSize) {
            " (주의: 라벨 ${labels.size}개 vs 모델 출력 ${outputSize}개, 개수가 달라요)"
        } else ""

        return SoundResult(
            label = localized.displayName,
            confidence = bestVal.coerceIn(0f, 1f),
            dominantFrequencyHz = 0.0,
            loudnessDb = 0.0,
            detail = "${localized.description}\n\n상위 3개: $topText$mismatchNote"
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

    private fun inputStatsSummary(input: FloatArray): String {
        val nanCount = input.count { it.isNaN() || it.isInfinite() }
        val min = input.minOrNull() ?: 0f
        val max = input.maxOrNull() ?: 0f
        val mean = if (input.isNotEmpty()) input.sum() / input.size else 0f
        val allZero = input.all { it == 0f }
        return "길이=${input.size}, min=%.3f, max=%.3f, mean=%.4f, NaN개수=$nanCount, 전부0=$allZero"
            .format(min, max, mean)
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
