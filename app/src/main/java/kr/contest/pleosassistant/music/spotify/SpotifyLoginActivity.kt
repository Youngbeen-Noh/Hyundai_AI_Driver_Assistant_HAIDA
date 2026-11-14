package kr.contest.pleosassistant.music.spotify

import android.annotation.SuppressLint
import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kr.contest.pleosassistant.HaidaApplication

/**
 * 스포티파이 로그인(정차 중에만 연다). 차에는 브라우저 앱이 없을 수 있어 앱 안 WebView 로 스포티파이 로그인 화면을 연다.
 * 아이디 · 비밀번호는 사용자가 스포티파이 화면에 직접 넣고, 우리 앱은 돌아오는 주소([SPOTIFY_REDIRECT_URI])의 code 만 받는다.
 */
class SpotifyLoginActivity : Activity() {

    private val scope: CoroutineScope = MainScope()
    private var handled = false

    @SuppressLint("SetJavaScriptEnabled") // 스포티파이 로그인 화면이 JavaScript 를 쓴다
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val client = (application as HaidaApplication).spotify
        val web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url
                if (!url.toString().startsWith(SPOTIFY_REDIRECT_URI)) return false
                finishWith(client, url)
                return true
            }
        }
        setContentView(web)
        web.loadUrl(client.startLogin())
    }

    private fun finishWith(client: SpotifyClient, url: Uri) {
        if (handled) return
        handled = true
        val query = url.queryParameterNames.associateWith { url.getQueryParameter(it).orEmpty() }
        scope.launch {
            val ok = withContext(Dispatchers.IO) { client.finishLogin(query) }
            Log.i(TAG, "로그인 ${if (ok) "성공" else "실패"}")
            (application as HaidaApplication).spotifyPlayer.onLoginChanged()
            finish()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "SpotifyLogin"
    }
}
