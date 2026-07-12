package com.carai.maintenance.audio

/**
 * 아주 단순한 Radix-2 FFT 구현체.
 * 외부 라이브러리 없이 주파수 분석을 하기 위한 최소 구현입니다.
 * size는 2의 거듭제곱이어야 합니다.
 */
object FFT {

    fun transform(real: DoubleArray, imag: DoubleArray) {
        val n = real.size
        if (n == 0 || (n and (n - 1)) != 0) {
            throw IllegalArgumentException("FFT 크기는 2의 거듭제곱이어야 합니다. size=$n")
        }

        // 비트 반전 정렬 (표준 알고리즘)
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tr = real[i]; real[i] = real[j]; real[j] = tr
                val ti = imag[i]; imag[i] = imag[j]; imag[j] = ti
            }
        }

        var len = 2
        while (len <= n) {
            val ang = -2.0 * Math.PI / len
            val wr = Math.cos(ang)
            val wi = Math.sin(ang)
            var i = 0
            while (i < n) {
                var curWr = 1.0
                var curWi = 0.0
                for (k in 0 until len / 2) {
                    val ur = real[i + k]
                    val ui = imag[i + k]
                    val vr = real[i + k + len / 2] * curWr - imag[i + k + len / 2] * curWi
                    val vi = real[i + k + len / 2] * curWi + imag[i + k + len / 2] * curWr
                    real[i + k] = ur + vr
                    imag[i + k] = ui + vi
                    real[i + k + len / 2] = ur - vr
                    imag[i + k + len / 2] = ui - vi
                    val nextWr = curWr * wr - curWi * wi
                    val nextWi = curWr * wi + curWi * wr
                    curWr = nextWr
                    curWi = nextWi
                }
                i += len
            }
            len = len shl 1
        }
    }
}
