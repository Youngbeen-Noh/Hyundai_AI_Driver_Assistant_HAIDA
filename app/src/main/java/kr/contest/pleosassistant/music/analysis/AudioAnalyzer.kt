package kr.contest.pleosassistant.music.analysis

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.log2
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

// 기능 3 · 곡 분위기 분석. 소리(PCM)만 듣고 분위기를 판단한다.
// 스포티파이가 신규 앱에 Audio Features 를 막아(기능계획서 5장) 곡별 분위기 수치를 밖에서 받을 수 없어서 직접 잰다.
// 안드로이드에 의존하지 않아 단위 테스트로 검증한다. 디코딩은 LocalLibrary(MediaCodec)가 한다.

/**
 * 곡 하나를 듣고 잰 값.
 * @property tempoBpm 박자 빠르기(분당 박 수)
 * @property loudnessDb 평균 소리 크기(RMS, dBFS). 0 에 가까울수록 크다
 * @property brightnessHz 스펙트럼 무게중심. 클수록 밝고 날카로운 소리
 * @property majorness 장조 쪽이면 +, 단조 쪽이면 −(대략 −1 ~ 1)
 * @property pulse 박자가 또렷한 정도 0 ~ 1 (드럼이 뚜렷하면 크다)
 * @property key 으뜸음(0 = C … 11 = B)
 */
data class AudioFeatures(
    val tempoBpm: Double,
    val loudnessDb: Double,
    val brightnessHz: Double,
    val majorness: Double,
    val pulse: Double,
    val key: Int,
) {
    val minor: Boolean get() = majorness < 0

    /** 화면에 보여 줄 한 줄. 예: "114 BPM · 장조 · 밝은 소리 · 박자 또렷". 장 · 단조는 뚜렷할 때만(±0.1) 적는다 */
    fun summary(): String = listOfNotNull(
        "${tempoBpm.roundToInt()} BPM",
        when {
            majorness >= MODE_CLEAR -> "장조"
            majorness <= -MODE_CLEAR -> "단조"
            else -> null
        },
        when {
            brightnessHz >= 1800 -> "밝은 소리"
            brightnessHz >= 1100 -> "부드러운 소리"
            else -> "어두운 소리"
        },
        if (pulse >= 0.35) "박자 또렷" else "박자 잔잔",
    ).joinToString(" · ")

    private companion object {
        /** 장 · 단조 상관 차이가 이보다 작으면 어느 쪽이라고 말하지 않는다 */
        const val MODE_CLEAR = 0.1
    }
}

object AudioAnalyzer {
    private const val FRAME = 2048
    private const val HOP = 512
    private const val MIN_BPM = 55.0
    // 160 위는 찾지 않는다: 8분음표 하이햇 때문에 87 BPM 곡을 174 로 잡는 두 배 오류가 났다(데모 곡 노을 3곡). 주행 음악은 대부분 160 아래다
    private const val MAX_BPM = 160.0

    /** 장 · 단조 음 분포(Krumhansl–Kessler 조성 프로파일) */
    private val MAJOR_PROFILE = doubleArrayOf(6.35, 2.23, 3.48, 2.33, 4.38, 4.09, 2.52, 5.19, 2.39, 3.66, 2.29, 2.88)
    private val MINOR_PROFILE = doubleArrayOf(6.33, 2.68, 3.52, 5.38, 2.60, 3.53, 2.54, 4.75, 3.98, 2.69, 3.34, 3.17)

    /**
     * @param samples 모노 PCM(−1 ~ 1)
     * @param sampleRate 표본화율(Hz)
     */
    fun analyze(samples: FloatArray, sampleRate: Int): AudioFeatures {
        require(samples.size >= FRAME * 4) { "분석하기에 너무 짧은 소리" }
        val window = DoubleArray(FRAME) { 0.5 - 0.5 * cos(2 * PI * it / (FRAME - 1)) }
        val frames = (samples.size - FRAME) / HOP + 1
        val bins = FRAME / 2 + 1
        val binHz = sampleRate.toDouble() / FRAME

        // 반음(피치 클래스)으로 모을 주파수 칸: 60 ~ 2000 Hz
        val pitchClass = IntArray(bins) { b ->
            val f = b * binHz
            if (f < 60 || f > 2000) -1 else ((69 + 12 * log2(f / 440.0)).roundToInt() % 12 + 12) % 12
        }

        val re = DoubleArray(FRAME)
        val im = DoubleArray(FRAME)
        var prevLogMag = DoubleArray(bins)
        val flux = DoubleArray(frames)
        val chroma = DoubleArray(12)
        var centroidSum = 0.0
        var centroidWeight = 0.0

        for (t in 0 until frames) {
            val start = t * HOP
            var energy = 0.0
            for (i in 0 until FRAME) {
                val s = samples[start + i].toDouble()
                energy += s * s
                re[i] = s * window[i]
                im[i] = 0.0
            }
            fft(re, im)
            val frameRms = sqrt(energy / FRAME)
            var magSum = 0.0
            var weighted = 0.0
            var fx = 0.0
            val logMag = DoubleArray(bins)
            for (b in 0 until bins) {
                val mag = sqrt(re[b] * re[b] + im[b] * im[b])
                magSum += mag
                weighted += mag * b * binHz
                logMag[b] = ln(1 + 10 * mag)
                val d = logMag[b] - prevLogMag[b]
                if (d > 0) fx += d
                val pc = pitchClass[b]
                if (pc >= 0) chroma[pc] += mag * mag
            }
            prevLogMag = logMag
            flux[t] = if (t == 0) 0.0 else fx
            if (magSum > 1e-9) {
                centroidSum += weighted / magSum * frameRms
                centroidWeight += frameRms
            }
        }

        val fps = sampleRate.toDouble() / HOP
        val onset = onsetEnvelope(flux)
        val (tempo, pulse) = estimateTempo(onset, fps)
        val (majorness, key) = estimateMode(chroma)

        var sq = 0.0
        for (s in samples) sq += s.toDouble() * s
        val loudness = 20 * log10(sqrt(sq / samples.size) + 1e-9)

        return AudioFeatures(
            tempoBpm = tempo,
            loudnessDb = loudness,
            brightnessHz = if (centroidWeight > 0) centroidSum / centroidWeight else 0.0,
            majorness = majorness,
            pulse = pulse,
            key = key,
        )
    }

    /** 스펙트럼 변화량에서 주변 평균을 빼 '소리가 새로 시작되는 순간'만 남긴다 */
    internal fun onsetEnvelope(flux: DoubleArray): DoubleArray {
        val w = 16
        val out = DoubleArray(flux.size)
        var sum = 0.0
        for (i in flux.indices) {
            sum += flux[i]
            if (i >= w) sum -= flux[i - w]
            val mean = sum / minOf(i + 1, w)
            out[i] = (flux[i] - mean).coerceAtLeast(0.0)
        }
        return out
    }

    /**
     * 자기상관으로 박자를 찾는다. 두 배 · 절반 빠르기로 헷갈리지 않게 120 BPM 근처를 조금 더 믿는다.
     * @return 빠르기(BPM)와 박자 또렷함(0~1)
     */
    internal fun estimateTempo(onset: DoubleArray, fps: Double): Pair<Double, Double> {
        val n = onset.size
        val mean = onset.average()
        val x = DoubleArray(n) { onset[it] - mean }
        val maxLag = (60 * fps / MIN_BPM).toInt() * 2 + 2
        val ac = DoubleArray(minOf(maxLag, n - 1))
        for (lag in ac.indices) {
            var s = 0.0
            for (i in 0 until n - lag) s += x[i] * x[i + lag]
            ac[lag] = s / (n - lag)
        }
        if (ac[0] <= 1e-12) return 0.0 to 0.0
        fun at(lag: Double): Double {
            val i = lag.toInt()
            if (i + 1 >= ac.size) return 0.0
            val f = lag - i
            return ac[i] * (1 - f) + ac[i + 1] * f
        }
        var bestBpm = 0.0
        var bestScore = Double.NEGATIVE_INFINITY
        var bestRaw = 0.0
        var bpm = MIN_BPM
        while (bpm <= MAX_BPM) {
            val lag = 60 * fps / bpm
            val raw = at(lag) + 0.5 * at(2 * lag)
            val prior = exp(-0.5 * (log2(bpm / 120.0) / 0.9).let { it * it })
            val score = raw * prior
            if (score > bestScore) {
                bestScore = score
                bestBpm = bpm
                bestRaw = at(lag)
            }
            bpm += 0.5
        }
        return bestBpm to (bestRaw / ac[0]).coerceIn(0.0, 1.0)
    }

    /** 반음별 에너지를 장 · 단조 프로파일 24개(12 으뜸음 × 2)와 비교한다 */
    internal fun estimateMode(chroma: DoubleArray): Pair<Double, Int> {
        var bestMajor = -1.0
        var bestMinor = -1.0
        var majorKey = 0
        var minorKey = 0
        for (k in 0 until 12) {
            val rotated = DoubleArray(12) { chroma[(it + k) % 12] }
            val cMaj = correlation(rotated, MAJOR_PROFILE)
            val cMin = correlation(rotated, MINOR_PROFILE)
            if (cMaj > bestMajor) { bestMajor = cMaj; majorKey = k }
            if (cMin > bestMinor) { bestMinor = cMin; minorKey = k }
        }
        return if (bestMajor >= bestMinor) (bestMajor - bestMinor) to majorKey else (bestMajor - bestMinor) to minorKey
    }

    private fun correlation(a: DoubleArray, b: DoubleArray): Double {
        val ma = a.average()
        val mb = b.average()
        var num = 0.0
        var da = 0.0
        var db = 0.0
        for (i in a.indices) {
            num += (a[i] - ma) * (b[i] - mb)
            da += (a[i] - ma) * (a[i] - ma)
            db += (b[i] - mb) * (b[i] - mb)
        }
        return if (da <= 0 || db <= 0) 0.0 else num / sqrt(da * db)
    }

    /** 제자리 반복 FFT(길이는 2의 거듭제곱) */
    internal fun fft(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                var t = re[i]; re[i] = re[j]; re[j] = t
                t = im[i]; im[i] = im[j]; im[j] = t
            }
        }
        var len = 2
        while (len <= n) {
            val ang = -2 * PI / len
            val wr = cos(ang)
            val wi = sin(ang)
            var i = 0
            while (i < n) {
                var cr = 1.0
                var ci = 0.0
                for (k in 0 until len / 2) {
                    val ur = re[i + k]
                    val ui = im[i + k]
                    val vr = re[i + k + len / 2] * cr - im[i + k + len / 2] * ci
                    val vi = re[i + k + len / 2] * ci + im[i + k + len / 2] * cr
                    re[i + k] = ur + vr
                    im[i + k] = ui + vi
                    re[i + k + len / 2] = ur - vr
                    im[i + k + len / 2] = ui - vi
                    val ncr = cr * wr - ci * wi
                    ci = cr * wi + ci * wr
                    cr = ncr
                }
                i += len
            }
            len = len shl 1
        }
    }
}
