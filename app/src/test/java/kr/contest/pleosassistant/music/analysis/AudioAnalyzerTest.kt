package kr.contest.pleosassistant.music.analysis

import kr.contest.pleosassistant.music.Mood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/** 정답을 아는 소리를 만들어 분석기가 맞게 재는지 본다 */
class AudioAnalyzerTest {

    private val sr = 22050

    private fun silence(seconds: Double) = FloatArray((seconds * sr).toInt())

    /** 박마다 짧은 '틱'(감쇠하는 1 kHz) */
    private fun clicks(bpm: Double, seconds: Double, offbeat: Boolean = false): FloatArray {
        val out = silence(seconds)
        val beat = 60.0 / bpm
        val tick = (0.03 * sr).toInt()
        var t = 0.0
        while (t < seconds) {
            listOfNotNull(t to 1.0, if (offbeat) (t + beat / 2) to 0.6 else null).forEach { (at, gain) ->
                val start = (at * sr).toInt()
                for (i in 0 until tick) if (start + i < out.size) {
                    out[start + i] += (gain * sin(2 * PI * 1000 * i / sr) * exp(-i / (0.006 * sr))).toFloat() * 0.5f
                }
            }
            t += beat
        }
        return out
    }

    /** 음 여러 개를 계속 울리는 화음(배음 3개) */
    private fun chord(midis: List<Int>, seconds: Double, amp: Double = 0.1): FloatArray {
        val out = silence(seconds)
        for (m in midis) {
            val f = 440.0 * Math.pow(2.0, (m - 69) / 12.0)
            for (i in out.indices) for (k in 1..3) out[i] += (amp / k * sin(2 * PI * f * k * i / sr)).toFloat()
        }
        return out
    }

    @Test
    fun `FFT 는 사인파의 주파수 칸에서 가장 크다`() {
        val n = 1024
        val re = DoubleArray(n) { sin(2 * PI * 64 * it / n) }
        val im = DoubleArray(n)
        AudioAnalyzer.fft(re, im)
        val mags = (0 until n / 2).map { re[it] * re[it] + im[it] * im[it] }
        assertEquals(64, mags.indices.maxBy { mags[it] })
    }

    @Test
    fun `박자를 맞게 잰다`() {
        assertEquals(120.0, AudioAnalyzer.analyze(clicks(120.0, 20.0), sr).tempoBpm, 2.0)
        assertEquals(80.0, AudioAnalyzer.analyze(clicks(80.0, 20.0), sr).tempoBpm, 2.0)
        assertEquals(135.0, AudioAnalyzer.analyze(clicks(135.0, 20.0), sr).tempoBpm, 2.0)
    }

    @Test
    fun `8분음표가 섞여도 박자를 두 배로 잡지 않는다`() {
        val f = AudioAnalyzer.analyze(clicks(87.0, 20.0, offbeat = true), sr)
        assertEquals(87.0, f.tempoBpm, 2.0)
    }

    @Test
    fun `박자가 뚜렷하면 pulse 가 크고 없으면 작다`() {
        val beat = AudioAnalyzer.analyze(clicks(100.0, 20.0), sr)
        val pad = AudioAnalyzer.analyze(chord(listOf(60, 64, 67), 20.0), sr)
        assertTrue(beat.pulse > 0.5)
        assertTrue(pad.pulse < beat.pulse)
    }

    @Test
    fun `장화음은 장조, 단화음은 단조로 듣는다`() {
        val cMajor = AudioAnalyzer.analyze(chord(listOf(60, 64, 67, 72), 10.0), sr)
        val aMinor = AudioAnalyzer.analyze(chord(listOf(57, 60, 64, 69), 10.0), sr)
        assertTrue("C 장조 majorness=${cMajor.majorness}", cMajor.majorness > 0)
        assertEquals(0, cMajor.key)
        assertTrue("A 단조 majorness=${aMinor.majorness}", aMinor.majorness < 0)
        assertEquals(9, aMinor.key)
    }

    @Test
    fun `높은 소리 · 잡음이 많을수록 밝다`() {
        val dark = AudioAnalyzer.analyze(chord(listOf(48), 5.0), sr)
        val rnd = Random(1)
        val noise = FloatArray(5 * sr) { (rnd.nextDouble(-0.2, 0.2)).toFloat() }
        val bright = AudioAnalyzer.analyze(noise, sr)
        assertTrue(dark.brightnessHz < 600)
        assertTrue(bright.brightnessHz > 3000)
    }

    @Test
    fun `소리 크기는 RMS dBFS`() {
        val sine = FloatArray(5 * sr) { (0.1 * sin(2 * PI * 440 * it / sr)).toFloat() }
        // 진폭 0.1 사인파의 RMS = 0.0707 → −23 dB
        assertEquals(-23.0, AudioAnalyzer.analyze(sine, sr).loudnessDb, 0.2)
    }

    @Test
    fun `요약 한 줄`() {
        val f = AudioFeatures(tempoBpm = 114.2, loudnessDb = -17.0, brightnessHz = 3000.0, majorness = 0.2, pulse = 0.5, key = 0)
        assertEquals("114 BPM · 장조 · 밝은 소리 · 박자 또렷", f.summary())
        // 장 · 단조가 뚜렷하지 않으면 적지 않는다
        assertEquals("66 BPM · 어두운 소리 · 박자 잔잔", f.copy(tempoBpm = 66.0, majorness = -0.05, brightnessHz = 800.0, pulse = 0.0).summary())
    }
}

class MoodProfilesTest {

    private fun f(bpm: Double, db: Double, hz: Double, major: Double = 0.0, pulse: Double = 0.5) =
        AudioFeatures(bpm, db, hz, major, pulse, key = 0)

    @Test
    fun `빠르고 밝으면 드라이브, 느리고 어두우면 밤길`() {
        assertEquals(Mood.DRIVE, best(f(130.0, -16.0, 3300.0, pulse = 0.45)))
        assertEquals(Mood.NIGHT, best(f(64.0, -23.0, 700.0, major = -0.2, pulse = 0.05)))
    }

    @Test
    fun `중간 빠르기에서는 빠르기로 크루징과 노을을 가른다`() {
        assertEquals(Mood.CRUISE, best(f(96.0, -17.0, 2700.0, pulse = 0.8)))
        assertEquals(Mood.SUNSET, best(f(86.0, -18.0, 2600.0, pulse = 0.8)))
    }

    @Test
    fun `느리고 조용하고 단조이면 빗길`() {
        assertEquals(Mood.RAIN, best(f(80.0, -20.0, 1500.0, major = -0.2, pulse = 0.45)))
    }

    @Test
    fun `가장 가까운 분위기가 1 이고 아주 먼 분위기는 0`() {
        val fit = MoodProfiles.fit(f(130.0, -16.0, 3300.0, pulse = 0.45))
        assertEquals(1f, fit.getValue(Mood.DRIVE))
        assertEquals(0f, fit.getValue(Mood.NIGHT))
        assertTrue(fit.values.all { it == 0f || it >= MoodProfiles.FLOOR })
    }

    private fun best(features: AudioFeatures) = MoodProfiles.fit(features).maxBy { it.value }.key
}
