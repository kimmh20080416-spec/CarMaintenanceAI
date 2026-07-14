package com.carai.maintenance.audio

import android.Manifest
import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission

/**
 * 마이크에서 원시 PCM 오디오를 캡처합니다.
 * 4096 샘플(2의 거듭제곱) 단위로 분석용 청크를 반환합니다.
 */
class AudioCapture {

    companion object {
        // YAMNet 기반 모델(TFLite Model Maker)은 16kHz를 기준으로 학습됩니다.
        // 기존 Teachable Machine 모델을 계속 쓰신다면 44100으로 되돌려도 됩니다.
        const val SAMPLE_RATE = 16000
        const val CHUNK_SIZE = 4096
    }

    private var audioRecord: AudioRecord? = null

    @SuppressLint("MissingPermission")
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start(): Boolean {
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = maxOf(minBuf, CHUNK_SIZE * 2)

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )
        return try {
            audioRecord?.startRecording()
            true
        } catch (e: Exception) {
            false
        }
    }

    /** CHUNK_SIZE개의 샘플을 -1.0~1.0 범위의 Double 배열로 반환. 실패 시 null. */
    fun readChunk(): DoubleArray? {
        val record = audioRecord ?: return null
        val shortBuf = ShortArray(CHUNK_SIZE)
        val read = record.read(shortBuf, 0, CHUNK_SIZE)
        if (read <= 0) return null
        return DoubleArray(CHUNK_SIZE) { i ->
            if (i < read) shortBuf[i] / 32768.0 else 0.0
        }
    }

    fun stop() {
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {
        }
        audioRecord = null
    }
}
