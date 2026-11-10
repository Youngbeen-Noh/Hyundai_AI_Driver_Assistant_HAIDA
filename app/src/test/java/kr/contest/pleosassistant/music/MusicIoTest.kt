package kr.contest.pleosassistant.music

import kr.contest.pleosassistant.storage.MusicSettings
import kr.contest.pleosassistant.storage.decodeMusicSettings
import kr.contest.pleosassistant.storage.encodeMusicSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MusicIoTest {

    @Test
    fun `WMO 날씨 코드를 맑음 · 구름 · 비 · 눈으로`() {
        assertEquals(Weather.CLEAR, weatherFromWmo(0))
        assertEquals(Weather.CLOUDY, weatherFromWmo(3))
        assertEquals(Weather.CLOUDY, weatherFromWmo(45))
        assertEquals(Weather.RAIN, weatherFromWmo(61))
        assertEquals(Weather.RAIN, weatherFromWmo(81))
        assertEquals(Weather.RAIN, weatherFromWmo(95))
        assertEquals(Weather.SNOW, weatherFromWmo(73))
    }

    @Test
    fun `Open-Meteo 응답을 읽는다`() {
        val json = """{"latitude":37.56,"current":{"time":"2026-11-11T09:00","interval":900,"weather_code":63}}"""
        assertEquals(Weather.RAIN, parseOpenMeteo(json))
        assertNull(parseOpenMeteo("""{"latitude":37.56}"""))
    }

    @Test
    fun `취향과 음악 앱을 저장하고 다시 읽는다`() {
        val track = DemoCatalog.tracks.first()
        val taste = Taste().toggleSaved(track).skipped(DemoCatalog.tracks.last()).played(track)
        val settings = MusicSettings(taste, MusicApp.MELON)
        val back = decodeMusicSettings(encodeMusicSettings(settings))
        assertEquals(settings.app, back.app)
        assertEquals(taste.saved, back.taste.saved)
        assertEquals(taste.recent, back.taste.recent)
        taste.genrePreference.forEach { (g, v) -> assertEquals(v, back.taste.preference(g), 1e-6f) }
    }
}
