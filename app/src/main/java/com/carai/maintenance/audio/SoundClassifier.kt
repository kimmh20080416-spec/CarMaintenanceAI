package com.carai.maintenance.audio

import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.sqrt

data class SoundResult(
    val label: String,
    val confidence: Float,
    val dominantFrequencyHz: Double,
    val loudnessDb: Double,
    val detail: String
)

/**
 * 소리 분류기 인터페이스.
 *
 * 지금은 [HeuristicSoundClassifier] (주파수 대역 기반 규칙)를 사용합니다.
 * 나중에 실제 학습된 모델이 준비되면, 이 인터페이스를 구현하는
 * TFLiteSoundClassifier 같은 클래스를 새로 만들어서 교체하기만 하면 됩니다.
 * (예: assets/engine_sound_model.tflite 를 로드해서 classify()에서 추론)
 */
interface SoundClassifier {
    fun classify(samples: DoubleArray, sampleRate: Int): SoundResult
}

/**
 * 지배 주파수와 스펙트럼 특성을 기반으로 한 규칙 기반(휴리스틱) 분류기.
 *
 * 주의: 이것은 실제 차량 고장을 진단하는 검증된 AI가 아니라,
 * "이런 주파수대는 보통 이런 부위에서 난다"는 일반적인 경험칙을 코드로
 * 옮긴 것입니다. 실제 정밀 진단에는 정비소 점검이 필요합니다.
 */
class HeuristicSoundClassifier : SoundClassifier {

    override fun classify(samples: DoubleArray, sampleRate: Int): SoundResult {
        val n = nextPowerOfTwo(samples.size)
        val real = DoubleArray(n)
        val imag = DoubleArray(n)
        for (i in samples.indices) {
            // 해닝 윈도우 적용 (스펙트럼 누설 감소)
            val window = 0.5 - 0.5 * Math.cos(2.0 * Math.PI * i / (samples.size - 1))
            real[i] = samples[i] * window
        }

        FFT.transform(real, imag)

        val magnitudes = DoubleArray(n / 2)
        for (i in 0 until n / 2) {
            magnitudes[i] = hypot(real[i], imag[i])
        }

        // RMS 기반 음량(dB)
        var sumSq = 0.0
        for (s in samples) sumSq += s * s
        val rms = sqrt(sumSq / samples.size)
        val loudnessDb = 20 * log10(rms.coerceAtLeast(1e-9)) + 100 // 대략적인 상대 dB

        // 지배 주파수 찾기 (DC 성분 제외)
        var maxMag = 0.0
        var maxIdx = 1
        for (i in 1 until magnitudes.size) {
            if (magnitudes[i] > maxMag) {
                maxMag = magnitudes[i]
                maxIdx = i
            }
        }
        val freqResolution = sampleRate.toDouble() / n
        val dominantFreq = maxIdx * freqResolution

        // 대역별 에너지 비율 계산 (저/중/고)
        val lowEnergy = bandEnergy(magnitudes, freqResolution, 20.0, 300.0)
        val midEnergy = bandEnergy(magnitudes, freqResolution, 300.0, 2000.0)
        val highEnergy = bandEnergy(magnitudes, freqResolution, 2000.0, 8000.0)
        val total = (lowEnergy + midEnergy + highEnergy).coerceAtLeast(1e-9)

        val lowRatio = lowEnergy / total
        val midRatio = midEnergy / total
        val highRatio = highEnergy / total

        val (label, detail, confidence) = when {
            loudnessDb < 40 -> Triple(
                "소음 미검출",
                "주변 소음 대비 유의미한 신호가 약합니다. 마이크를 소음원에 더 가까이 대고 다시 시도해 보세요.",
                0.3f
            )
            dominantFreq in 20.0..80.0 && lowRatio > 0.55 -> Triple(
                "엔진 저속 진동 / 마운트 의심",
                "저주파(20~80Hz) 에너지가 우세합니다. 공회전 시 심한 진동이라면 엔진 마운트 또는 아이들 관련 부위를 점검해 보세요.",
                0.55f
            )
            dominantFreq in 80.0..300.0 && lowRatio > 0.45 -> Triple(
                "엔진/구동계 저음",
                "저~중저역대 소리가 두드러집니다. 엔진 내부 또는 구동계(하체) 쪽 소음일 가능성이 있습니다.",
                0.5f
            )
            dominantFreq in 300.0..1000.0 && midRatio > 0.5 -> Triple(
                "벨트/풀리 마찰음 의심",
                "중저역 반복음이 우세합니다. 팬벨트, 텐셔너, 풀리류의 마찰/베어링 소음일 가능성이 있습니다.",
                0.5f
            )
            dominantFreq in 1000.0..3000.0 && midRatio > 0.4 -> Triple(
                "배기/흡기 계통 소음 의심",
                "중역대 소리입니다. 배기 파이프 누기, 흡기 계통 소음일 가능성이 있습니다.",
                0.45f
            )
            dominantFreq in 3000.0..8000.0 && highRatio > 0.4 -> Triple(
                "브레이크 마찰음(스퀼) 의심",
                "고주파 마찰음 패턴입니다. 제동 시 발생한다면 브레이크 패드 마모 경고음일 가능성이 높습니다.",
                0.6f
            )
            highRatio > 0.5 -> Triple(
                "고주파 이상음 (베어링/휠 계통 등)",
                "고주파 성분이 두드러집니다. 휠베어링, 알터네이터, 워터펌프 등 회전체 베어링 소음 가능성이 있습니다.",
                0.4f
            )
            else -> Triple(
                "복합음 / 특정 어려움",
                "여러 대역이 섞여 있어 하나로 특정하기 어렵습니다. 소음이 나는 순간(가속/제동/공회전) 다시 녹음해 보세요.",
                0.3f
            )
        }

        return SoundResult(
            label = label,
            confidence = confidence,
            dominantFrequencyHz = dominantFreq,
            loudnessDb = loudnessDb,
            detail = detail
        )
    }

    private fun bandEnergy(mag: DoubleArray, freqRes: Double, lowHz: Double, highHz: Double): Double {
        val lowIdx = (lowHz / freqRes).toInt().coerceIn(0, mag.size - 1)
        val highIdx = (highHz / freqRes).toInt().coerceIn(0, mag.size - 1)
        var sum = 0.0
        for (i in lowIdx..highIdx) sum += mag[i] * mag[i]
        return sum
    }

    private fun nextPowerOfTwo(x: Int): Int {
        var v = 1
        while (v < x) v = v shl 1
        return v
    }
}
