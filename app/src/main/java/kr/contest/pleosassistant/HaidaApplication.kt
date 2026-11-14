package kr.contest.pleosassistant

import ai.pleos.playground.vehicle.Vehicle
import android.app.Application
import kr.contest.pleosassistant.music.spotify.FileSpotifyTokenStore
import kr.contest.pleosassistant.music.spotify.SpotifyClient
import kr.contest.pleosassistant.music.spotify.SpotifyPlayer
import java.io.File

/**
 * 앱 전체가 함께 쓰는 Pleos Vehicle 연결.
 * Vehicle SDK 가이드대로 앱이 만들어질 때 initialize() 한다. 기능마다 Vehicle 을 새로 만들지 말고 이것을 쓴다.
 * 스포티파이 로그인 상태도 화면(음악) · 로그인 화면이 함께 쓴다(기능 3).
 */
class HaidaApplication : Application() {

    lateinit var vehicle: Vehicle
        private set

    /** 스포티파이 Web API. Client ID 는 secrets.properties 의 SPOTIFY_CLIENT_ID(없으면 비어 있고 스포티파이를 쓰지 않는다) */
    val spotify: SpotifyClient by lazy {
        SpotifyClient(BuildConfig.SPOTIFY_CLIENT_ID, FileSpotifyTokenStore(File(noBackupFilesDir, "spotify.json")))
    }
    val spotifyPlayer: SpotifyPlayer by lazy { SpotifyPlayer(spotify) }

    override fun onCreate() {
        super.onCreate()
        vehicle = Vehicle(this).also { it.initialize() }
    }
}
