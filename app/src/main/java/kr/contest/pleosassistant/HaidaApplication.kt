package kr.contest.pleosassistant

import ai.pleos.playground.vehicle.Vehicle
import android.app.Application

/**
 * 앱 전체가 함께 쓰는 Pleos Vehicle 연결.
 * Vehicle SDK 가이드대로 앱이 만들어질 때 initialize() 한다. 기능마다 Vehicle 을 새로 만들지 말고 이것을 쓴다.
 */
class HaidaApplication : Application() {

    lateinit var vehicle: Vehicle
        private set

    override fun onCreate() {
        super.onCreate()
        vehicle = Vehicle(this).also { it.initialize() }
    }
}
