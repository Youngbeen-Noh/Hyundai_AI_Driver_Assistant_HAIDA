package kr.contest.pleosassistant.vehicle

import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import ai.pleos.playground.vehicle.constant.ErrorCode
import android.os.Handler
import android.os.Looper
import android.util.Log
import kr.contest.pleosassistant.status.Reading

/**
 * Pleos Vehicle SDK 항목 하나를 지원 확인(checkXxxCapabilities) → 첫 값 읽기(getXxx) → 변화 구독(registerXxx) 순서로 처리한다.
 * 지원하지 않거나 권한이 없으면 Unsupported, 그 밖의 실패는 Unavailable 을 알린다.
 * 차량 서비스 연결이 아직 안 됐으면 잠시 뒤 다시 시도한다. 기능 1(차량 상태)과 기능 2(좌석)가 함께 쓴다.
 */
internal class VehicleWatcher(private val tag: String) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val unregisters = mutableListOf<() -> Unit>()

    @Volatile
    var active = false
        private set

    fun start() {
        active = true
    }

    fun stop() {
        active = false
        mainHandler.removeCallbacksAndMessages(null)
        synchronized(unregisters) {
            unregisters.forEach { runCatching(it) }
            unregisters.clear()
        }
    }

    /**
     * @param check 이 항목을 읽을 수 있으면 ok(true). 차량이 지원하지 않거나 읽기 권한이 없으면 ok(false)
     * @param register 구독하고, 구독을 끊는 함수를 돌려준다
     */
    fun <T> watch(
        name: String,
        publish: (Reading<T>) -> Unit,
        check: (ok: (Boolean) -> Unit, fail: (Exception) -> Unit) -> Unit,
        get: (ok: (T?) -> Unit, fail: (Exception) -> Unit) -> Unit,
        register: (emit: (T?) -> Unit) -> () -> Unit,
        attempt: Int = 1,
    ) {
        fun publishValue(value: T?) = publish(if (value == null) Reading.Unavailable else Reading.Value(value))
        fun fail(step: String, e: Exception) {
            Log.w(tag, "$name $step 실패: ${e.describe()}")
            if (e.isConnectionProblem() && attempt < MAX_ATTEMPTS && active) {
                mainHandler.postDelayed({ watch(name, publish, check, get, register, attempt + 1) }, RETRY_DELAY_MS)
            } else {
                publish(if (e.isUnsupported()) Reading.Unsupported else Reading.Unavailable)
            }
        }

        guarded("check", ::fail) {
            check({ readable ->
                if (readable) {
                    guarded("get", ::fail) { get(::publishValue) { e -> fail("get", e) } }
                    guarded("register", ::fail) {
                        val unregister = register(::publishValue)
                        synchronized(unregisters) { unregisters += unregister }
                    }
                } else {
                    publish(Reading.Unsupported)
                }
            }) { e -> fail("check", e) }
        }
    }

    /** 구독 중 오류. 이미 읽은 값은 그대로 두고 기록만 한다. */
    fun listenerFailed(e: Exception) {
        Log.w(tag, "구독 오류: ${e.describe()}")
    }

    /** SDK 가 콜백 대신 바로 예외를 던지는 경우(권한 등)도 같은 길로 보낸다. */
    private inline fun guarded(step: String, fail: (String, Exception) -> Unit, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            fail(step, e)
        }
    }

    private companion object {
        const val MAX_ATTEMPTS = 5
        const val RETRY_DELAY_MS = 1_000L
    }
}

internal fun CapabilityConfig.readable(): Boolean = accessType == AccessType.READ || accessType == AccessType.READ_WRITE

private val UNSUPPORTED_CODES = setOf(
    ErrorCode.ERROR_NOT_SUPPORTED_PROPERTY,
    ErrorCode.ERROR_NOT_SUPPORTED_AREA,
    ErrorCode.ERROR_NOT_SUPPORTED_VALUE_TYPE,
    ErrorCode.ERROR_SECURITY_PERMISSION,
)

/**
 * 문서상 VehicleException.errorCode.
 * Vehicle 2.3.2 는 VehicleException 클래스를 컴파일 경로에 내보내지 않아서, 공개 getter 를 이름으로 부른다.
 * 그래도 못 얻으면 메시지에 들어 있는 코드 이름으로 찾는다.
 */
internal fun Exception.vehicleErrorCode(): ErrorCode? =
    runCatching { javaClass.getMethod("getErrorCode").invoke(this) as? ErrorCode }.getOrNull()
        ?: ErrorCode.values().firstOrNull { message?.contains(it.name) == true }

internal fun Exception.isUnsupported(): Boolean = this is SecurityException || vehicleErrorCode() in UNSUPPORTED_CODES

internal fun Exception.isConnectionProblem(): Boolean = vehicleErrorCode() == ErrorCode.ERROR_SERVICE_CONNECTION_FAILURE

internal fun Exception.describe(): String = "${javaClass.simpleName} ${vehicleErrorCode() ?: ""} ${message.orEmpty()}".trim()
