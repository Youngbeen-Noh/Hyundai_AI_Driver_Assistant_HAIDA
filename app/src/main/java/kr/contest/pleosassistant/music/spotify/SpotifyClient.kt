package kr.contest.pleosassistant.music.spotify

import android.util.AtomicFile
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.SecureRandom

private const val TAG = "SpotifyClient"

/** 응답 하나. 성공이면 [body], 실패면 [problem] */
data class SpotifyResult(val status: Int, val body: String?, val problem: SpotifyProblem?) {
    val ok: Boolean get() = problem == null
}

/** 토큰을 기기 안 파일 하나에 둔다(noBackupFilesDir). 서버로 보내지 않는다 */
class FileSpotifyTokenStore(file: File) {
    private val file = AtomicFile(file)

    fun load(): SpotifyTokens? = runCatching {
        if (!file.baseFile.exists()) return null
        val o = JSONObject(String(file.readFully(), Charsets.UTF_8))
        SpotifyTokens(o.getString("access"), o.getString("refresh"), o.getLong("expiresAt"))
    }.getOrNull()

    @Synchronized
    fun save(tokens: SpotifyTokens?) {
        if (tokens == null) {
            file.delete()
            return
        }
        val out = file.startWrite()
        try {
            val json = JSONObject().put("access", tokens.accessToken).put("refresh", tokens.refreshToken).put("expiresAt", tokens.expiresAtMs)
            out.write(json.toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(out)
        } catch (e: Exception) {
            file.failWrite(out)
            Log.w(TAG, "토큰 저장 실패: $e")
        }
    }
}

/**
 * 스포티파이 Web API. 네트워크를 쓰니 메인 스레드가 아닌 곳(Dispatchers.IO)에서 부른다.
 * 토큰이 만료되면 새로 고쳐 다시 한 번 보낸다.
 * @param clientId 스포티파이 개발자 앱의 Client ID(비밀 값 아님, PKCE). 비어 있으면 아무것도 하지 않는다
 */
class SpotifyClient(
    val clientId: String,
    private val store: FileSpotifyTokenStore,
    private val now: () -> Long = System::currentTimeMillis,
) {
    @Volatile private var tokens: SpotifyTokens? = store.load()

    val configured: Boolean get() = clientId.isNotBlank()
    val loggedIn: Boolean get() = tokens != null

    // ---- 로그인 ----

    @Volatile private var pendingVerifier: String? = null
    @Volatile private var pendingState: String? = null

    /** 로그인 화면에서 열 주소. 돌아오면 [finishLogin] */
    fun startLogin(): String {
        val random = SecureRandom()
        val verifier = pkceVerifier(ByteArray(64).also(random::nextBytes))
        val state = pkceVerifier(ByteArray(16).also(random::nextBytes))
        pendingVerifier = verifier
        pendingState = state
        return authorizeUrl(clientId, pkceChallenge(verifier), state)
    }

    /** 돌아온 주소의 query 로 토큰을 받는다. 성공하면 true */
    fun finishLogin(query: Map<String, String>): Boolean {
        val verifier = pendingVerifier ?: return false
        val code = parseCallback(query, pendingState ?: return false) ?: return false
        val res = postToken(
            mapOf(
                "grant_type" to "authorization_code",
                "code" to code,
                "redirect_uri" to SPOTIFY_REDIRECT_URI,
                "client_id" to clientId,
                "code_verifier" to verifier,
            ),
        )
        pendingVerifier = null
        pendingState = null
        val body = res.body ?: return false
        if (!res.ok) return false
        tokens = parseTokens(body, now()).also(store::save)
        Log.i(TAG, "로그인됨")
        return true
    }

    fun logout() {
        tokens = null
        store.save(null)
    }

    private fun refresh(): Boolean {
        val t = tokens ?: return false
        val res = postToken(mapOf("grant_type" to "refresh_token", "refresh_token" to t.refreshToken, "client_id" to clientId))
        if (!res.ok || res.body == null) {
            Log.w(TAG, "토큰 새로 고침 실패(${res.status}) → 다시 로그인")
            logout()
            return false
        }
        tokens = parseTokens(res.body, now(), previousRefresh = t.refreshToken).also(store::save)
        return true
    }

    private fun postToken(form: Map<String, String>): SpotifyResult =
        request("POST", "https://accounts.spotify.com/api/token", formEncode(form), "application/x-www-form-urlencoded", token = null)

    // ---- Web API ----

    fun search(query: String): SpotifyResult =
        api("GET", "/v1/search?type=track&limit=10&market=from_token&q=" + URLEncoder.encode(query, "UTF-8"))

    fun devices(): SpotifyResult = api("GET", "/v1/me/player/devices")

    /** 지금 재생 상태. 아무것도 안 틀고 있으면 204(본문 없음) */
    fun playback(): SpotifyResult = api("GET", "/v1/me/player")

    fun play(deviceId: String, uri: String): SpotifyResult =
        api("PUT", "/v1/me/player/play?device_id=$deviceId", JSONObject().put("uris", JSONArray().put(uri)).toString())

    fun resume(): SpotifyResult = api("PUT", "/v1/me/player/play")

    fun pause(): SpotifyResult = api("PUT", "/v1/me/player/pause")

    private fun api(method: String, path: String, json: String? = null): SpotifyResult {
        if (tokens?.expired(now()) == true && !refresh()) return SpotifyResult(401, null, SpotifyProblem.NEEDS_LOGIN)
        val access = tokens?.accessToken ?: return SpotifyResult(401, null, SpotifyProblem.NEEDS_LOGIN)
        val url = "https://api.spotify.com$path"
        val first = request(method, url, json, "application/json", access)
        if (first.status != 401 || !refresh()) return first
        return request(method, url, json, "application/json", tokens?.accessToken)
    }

    private fun request(method: String, url: String, body: String?, contentType: String, token: String?): SpotifyResult =
        runCatching {
            val c = URL(url).openConnection() as HttpURLConnection
            c.requestMethod = method
            c.connectTimeout = 8_000
            c.readTimeout = 8_000
            token?.let { c.setRequestProperty("Authorization", "Bearer $it") }
            if (body != null || method == "PUT") {
                c.doOutput = true
                c.setRequestProperty("Content-Type", contentType)
                c.outputStream.use { it.write((body ?: "").toByteArray(Charsets.UTF_8)) }
            }
            val status = c.responseCode
            val text = (if (status >= 400) c.errorStream else c.inputStream)?.use { it.readBytes().toString(Charsets.UTF_8) }
            c.disconnect()
            if (status in 200..299) SpotifyResult(status, text, null)
            else SpotifyResult(status, text, problemOf(status, text)).also { Log.w(TAG, "$method ${url.substringBefore('?')} → $status ${text?.take(200)}") }
        }.getOrElse { e ->
            Log.w(TAG, "$method ${url.substringBefore('?')} 실패: $e")
            SpotifyResult(0, null, SpotifyProblem.OTHER)
        }
}
