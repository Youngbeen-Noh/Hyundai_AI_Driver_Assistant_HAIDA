package kr.contest.pleosassistant.music.analysis

import kr.contest.pleosassistant.music.Mood
import kotlin.math.exp
import kotlin.math.ln

/**
 * 분위기마다 '이런 소리면 어울린다'는 기준점과, 곡의 잰 값이 그 기준에 얼마나 가까운지로 적합도(0~1)를 낸다.
 *
 * 기준점의 방향(음악적 판단): 빗길 · 밤길은 느리고 어둡고 조용하게, 드라이브는 빠르고 밝게,
 * 크루징 · 노을은 그 사이(노을이 조금 더 느리다), 밤길은 박자가 거의 없게.
 * 숫자는 데모 곡 18개를 [AudioAnalyzer] 로 잰 값(WavDirReport)에 맞췄다. 드럼 · 하이햇이 있으면 밝기가 2,500 Hz 를 넘고,
 * 장 · 단조와 박자 또렷함은 이런 음악에서 흔들림이 커서 허용폭을 넓게(덜 믿게) 두었다.
 * 실제 곡(스포티파이 · 멜론 · CC 음원)을 쓰게 되면 같은 리포트로 다시 맞춘다.
 *
 * 거리 = Σ ((값 − 기준) / 허용폭)². 빠르기는 배수 관계라 로그 눈금(옥타브)으로 잰다.
 * 적합도 = 가장 가까운 분위기를 1 로 두고 나머지를 상대값으로, [FLOOR] 아래는 0(어울리지 않음).
 */
object MoodProfiles {

    private data class Target(val bpm: Double, val db: Double, val hz: Double, val major: Double, val pulse: Double)

    private val TARGETS = mapOf(
        Mood.FRESH to Target(bpm = 114.0, db = -17.0, hz = 3100.0, major = 0.05, pulse = 0.55),
        Mood.DRIVE to Target(bpm = 129.0, db = -16.5, hz = 3250.0, major = 0.0, pulse = 0.45),
        Mood.CRUISE to Target(bpm = 95.0, db = -17.0, hz = 2750.0, major = 0.05, pulse = 0.80),
        Mood.RAIN to Target(bpm = 80.0, db = -20.0, hz = 1550.0, major = -0.15, pulse = 0.50),
        Mood.SUNSET to Target(bpm = 87.0, db = -18.0, hz = 2600.0, major = 0.0, pulse = 0.80),
        Mood.NIGHT to Target(bpm = 66.0, db = -22.0, hz = 800.0, major = -0.10, pulse = 0.10),
    )

    // 허용폭: 이만큼 다르면 거리 1
    private const val SPREAD_OCTAVE = 0.12
    private const val SPREAD_DB = 2.0
    private const val SPREAD_HZ = 400.0
    private const val SPREAD_MAJOR = 0.40
    private const val SPREAD_PULSE = 0.30

    /** 이보다 낮은 상대 적합도는 0(그 분위기에 넣지 않음) */
    const val FLOOR = 0.15

    fun distance(f: AudioFeatures, mood: Mood): Double {
        val t = TARGETS.getValue(mood)
        fun sq(x: Double) = x * x
        return sq(ln(f.tempoBpm.coerceAtLeast(1.0) / t.bpm) / ln(2.0) / SPREAD_OCTAVE) +
            sq((f.loudnessDb - t.db) / SPREAD_DB) +
            sq((f.brightnessHz - t.hz) / SPREAD_HZ) +
            sq((f.majorness - t.major) / SPREAD_MAJOR) +
            sq((f.pulse - t.pulse) / SPREAD_PULSE)
    }

    /** 분위기별 적합도. 가장 가까운 분위기가 1 */
    fun fit(f: AudioFeatures): Map<Mood, Float> {
        val score = Mood.entries.associateWith { exp(-0.5 * distance(f, it)) }
        val best = score.values.max().coerceAtLeast(1e-300)
        return score.mapValues { (_, s) ->
            val rel = s / best
            if (rel < FLOOR) 0f else (Math.round(rel * 100) / 100.0).toFloat()
        }
    }
}
