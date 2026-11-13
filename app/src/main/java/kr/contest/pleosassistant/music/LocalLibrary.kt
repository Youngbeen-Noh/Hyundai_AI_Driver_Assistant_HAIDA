package kr.contest.pleosassistant.music

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kr.contest.pleosassistant.music.analysis.AudioAnalyzer
import kr.contest.pleosassistant.music.analysis.AudioFeatures
import kr.contest.pleosassistant.music.analysis.MoodProfiles
import org.json.JSONObject
import java.io.File
import java.nio.ByteOrder

/**
 * 앱에 든 데모 곡(assets/music, tools/music-gen 으로 직접 만든 곡이라 저작권 문제 없음)을 읽고,
 * 곡마다 앞 [ANALYZE_SECONDS]초를 디코딩해 [AudioAnalyzer] 로 분위기를 판단한다.
 * 결과는 [cacheFile] 에 저장해 다음 실행부터는 다시 분석하지 않는다(곡 파일이나 분석기 버전이 바뀌면 다시 한다).
 * tracks.json 의 intent(작곡 의도)는 판단에 쓰지 않고 로그로 비교만 한다.
 */
class LocalLibrary(private val context: Context, private val cacheFile: File) {

    /** @param onProgress (분석한 곡 수, 전체 곡 수) */
    suspend fun load(onProgress: (Int, Int) -> Unit = { _, _ -> }): List<Track> = withContext(Dispatchers.Default) {
        val entries = runCatching { readCatalog() }.getOrElse {
            Log.w(TAG, "곡 목록을 읽지 못함: $it")
            return@withContext emptyList()
        }
        val cache = readCache()
        val updated = mutableMapOf<String, JSONObject>()
        val tracks = entries.mapIndexedNotNull { i, e ->
            val size = runCatching { context.assets.openFd(e.file).use { it.length } }.getOrDefault(-1L)
            val cacheKey = "$ANALYZER_VERSION:$size"
            val cached = cache.optJSONObject(e.id)?.takeIf { it.optString("key") == cacheKey }
            val features = cached?.let(::featuresFrom) ?: runCatching {
                val (pcm, rate) = decodeMono(e.file, ANALYZE_SECONDS)
                AudioAnalyzer.analyze(pcm, rate)
            }.onFailure { Log.w(TAG, "${e.id} 분석 실패: $it") }.getOrNull()
            onProgress(i + 1, entries.size)
            if (features == null) return@mapIndexedNotNull null
            updated[e.id] = featuresJson(features).put("key", cacheKey)
            val fit = MoodProfiles.fit(features)
            val heard = fit.maxBy { it.value }.key
            Log.i(TAG, "${e.id} '${e.title}' → ${heard.name} (의도 ${e.intent}) · ${features.summary()} · $fit")
            Track(
                id = e.id,
                title = e.title,
                artist = e.artist,
                genre = e.genre,
                moodFit = fit.filterValues { it > 0f },
                durationSec = e.durationSec,
                assetPath = e.file,
                features = features,
            )
        }
        writeCache(updated)
        tracks
    }

    private data class Entry(
        val id: String, val file: String, val title: String, val artist: String, val genre: String,
        val durationSec: Int, val intent: String,
    )

    private fun readCatalog(): List<Entry> {
        val json = JSONObject(context.assets.open(CATALOG).bufferedReader().use { it.readText() })
        val arr = json.getJSONArray("tracks")
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Entry(
                id = o.getString("id"),
                file = o.getString("file"),
                title = o.getString("title"),
                artist = o.getString("artist"),
                genre = o.getString("genre"),
                durationSec = o.optInt("durationSec", 60),
                intent = o.optString("intent"),
            )
        }
    }

    /** 앞 [maxSeconds]초를 모노 PCM 으로 디코딩한다(MediaExtractor + MediaCodec) */
    private fun decodeMono(asset: String, maxSeconds: Int): Pair<FloatArray, Int> {
        val extractor = MediaExtractor()
        context.assets.openFd(asset).use { afd -> extractor.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length) }
        try {
            val trackIndex = (0 until extractor.trackCount).first {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            }
            extractor.selectTrack(trackIndex)
            val inFormat = extractor.getTrackFormat(trackIndex)
            val codec = MediaCodec.createDecoderByType(inFormat.getString(MediaFormat.KEY_MIME)!!)
            codec.configure(inFormat, null, null, 0)
            codec.start()
            var rate = inFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = inFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var floatPcm = false
            var out = FloatArray(rate * maxSeconds)
            var n = 0
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            try {
                while (n < out.size) {
                    if (!inputDone) {
                        val inIdx = codec.dequeueInputBuffer(10_000)
                        if (inIdx >= 0) {
                            val buf = codec.getInputBuffer(inIdx)!!
                            val read = extractor.readSampleData(buf, 0)
                            if (read < 0) {
                                codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                codec.queueInputBuffer(inIdx, 0, read, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                    val outIdx = codec.dequeueOutputBuffer(info, 10_000)
                    when {
                        outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val f = codec.outputFormat
                            rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            floatPcm = f.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                                f.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                            if (out.size != rate * maxSeconds) out = out.copyOf(rate * maxSeconds)
                        }
                        outIdx >= 0 -> {
                            val buf = codec.getOutputBuffer(outIdx)!!.order(ByteOrder.nativeOrder())
                            buf.position(info.offset)
                            buf.limit(info.offset + info.size)
                            if (floatPcm) {
                                val fb = buf.asFloatBuffer()
                                while (fb.remaining() >= channels && n < out.size) {
                                    var s = 0f
                                    repeat(channels) { s += fb.get() }
                                    out[n++] = s / channels
                                }
                            } else {
                                val sb = buf.asShortBuffer()
                                while (sb.remaining() >= channels && n < out.size) {
                                    var s = 0f
                                    repeat(channels) { s += sb.get() / 32768f }
                                    out[n++] = s / channels
                                }
                            }
                            codec.releaseOutputBuffer(outIdx, false)
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                        }
                    }
                }
            } finally {
                runCatching { codec.stop() }
                codec.release()
            }
            return out.copyOf(n) to rate
        } finally {
            extractor.release()
        }
    }

    private fun readCache(): JSONObject =
        runCatching { if (cacheFile.exists()) JSONObject(cacheFile.readText()) else JSONObject() }.getOrDefault(JSONObject())

    private fun writeCache(items: Map<String, JSONObject>) {
        runCatching {
            cacheFile.parentFile?.mkdirs()
            val tmp = File(cacheFile.parentFile, cacheFile.name + ".tmp")
            tmp.writeText(JSONObject(items as Map<*, *>).toString())
            if (!tmp.renameTo(cacheFile)) {
                cacheFile.delete()
                tmp.renameTo(cacheFile)
            }
        }.onFailure { Log.w(TAG, "분석 결과 저장 실패: $it") }
    }

    private fun featuresJson(f: AudioFeatures) = JSONObject()
        .put("tempo", f.tempoBpm).put("db", f.loudnessDb).put("hz", f.brightnessHz)
        .put("major", f.majorness).put("pulse", f.pulse).put("tonic", f.key)

    private fun featuresFrom(o: JSONObject): AudioFeatures? = runCatching {
        AudioFeatures(
            tempoBpm = o.getDouble("tempo"),
            loudnessDb = o.getDouble("db"),
            brightnessHz = o.getDouble("hz"),
            majorness = o.getDouble("major"),
            pulse = o.getDouble("pulse"),
            key = o.getInt("tonic"),
        )
    }.getOrNull()

    companion object {
        const val CATALOG = "music/tracks.json"

        /** 앞 45초만 듣는다(곡 하나 분석 1초 안팎) */
        const val ANALYZE_SECONDS = 45

        /** 분석 방법이 바뀌면 올린다 → 저장해 둔 결과를 버리고 다시 분석 */
        const val ANALYZER_VERSION = 1
        private const val TAG = "LocalLibrary"
    }
}
