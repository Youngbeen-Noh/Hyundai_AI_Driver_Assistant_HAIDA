package kr.contest.pleosassistant.music.analysis

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 개발용: 폴더의 WAV(16bit) 를 분석해 값과 분위기 판단을 출력한다. 환경 변수 HAIDA_WAV_DIR 가 없으면 건너뛴다(CI).
 * 새 곡을 넣을 때 분위기 기준(MoodProfiles)을 맞춰 보는 데 쓴다.
 *
 *   ffmpeg -i 곡.m4a -ac 1 -ar 22050 곡.wav
 *   HAIDA_WAV_DIR=폴더 ./gradlew testDebugUnitTest --tests '*WavDirReport*' -i
 */
class WavDirReport {

    @Test
    fun report() {
        val dir = System.getenv("HAIDA_WAV_DIR")
        assumeTrue("HAIDA_WAV_DIR 없음", dir != null)
        File(dir!!).listFiles { f -> f.extension == "wav" }!!.sortedBy { it.name }.forEach { f ->
            val (pcm, rate) = readWav(f)
            val features = AudioAnalyzer.analyze(pcm, rate)
            val fit = MoodProfiles.fit(features)
            val best = fit.maxByOrNull { it.value }!!.key
            println(
                "WAV ${f.nameWithoutExtension.padEnd(10)} → ${best.name.padEnd(6)} | " +
                    "%.1f BPM · %.1f dB · %.0f Hz · maj %.2f · pulse %.2f | %s".format(
                        features.tempoBpm, features.loudnessDb, features.brightnessHz, features.majorness, features.pulse,
                        fit.entries.sortedByDescending { it.value }.joinToString { "${it.key.name}=${"%.2f".format(it.value)}" },
                    ),
            )
        }
    }

    private fun readWav(f: File): Pair<FloatArray, Int> {
        val bytes = f.readBytes()
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val rate = bb.getInt(24)
        val channels = bb.getShort(22).toInt()
        var pos = 12
        while (pos < bytes.size - 8) {
            val id = String(bytes, pos, 4)
            val size = bb.getInt(pos + 4)
            if (id == "data") {
                val n = size / 2 / channels
                val out = FloatArray(n) { i -> bb.getShort(pos + 8 + i * 2 * channels).toFloat() / 32768f }
                return out to rate
            }
            pos += 8 + size
        }
        error("data 청크 없음: $f")
    }
}
