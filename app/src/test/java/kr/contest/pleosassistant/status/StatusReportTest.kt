package kr.contest.pleosassistant.status

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusReportTest {

    private val allGood = VehicleSnapshot(
        engineOil = Reading.Value(FluidLevel.NORMAL),
        transmissionOil = Reading.Value(FluidLevel.NORMAL),
        brakeFluid = Reading.Value(FluidLevel.NORMAL),
        washerFluidLow = Reading.Value(false),
        fuelPercent = Reading.Value(60),
        evBatteryPercent = Reading.Unsupported,
        odometerKm = Reading.Value(12345.6f),
    )

    private fun StatusReport.item(id: StatusItemId) = items.single { it.id == id }

    @Test
    fun `모두 정상이면 카드 6개와 좋다는 요약`() {
        val report = buildStatusReport(allGood)
        assertEquals(6, report.items.size)
        assertEquals("오늘 차 상태 좋아요.", report.summary)
        assertEquals(StatusLevel.INFO, report.item(StatusItemId.ODOMETER).level)
        assertEquals("12,345 km", report.item(StatusItemId.ODOMETER).detail)
    }

    @Test
    fun `워셔액 부족은 주의, 요약에 무엇을 할지 나온다`() {
        val report = buildStatusReport(allGood.copy(washerFluidLow = Reading.Value(true)))
        assertEquals(StatusLevel.CAUTION, report.item(StatusItemId.WASHER_FLUID).level)
        assertEquals("워셔액이 부족해요. 주유소나 마트에서 보충하세요. 나머지는 모두 정상이에요.", report.summary)
    }

    @Test
    fun `엔진오일 부족은 주의, 매우 부족은 즉시 조치`() {
        assertEquals(
            StatusLevel.CAUTION,
            buildStatusReport(allGood.copy(engineOil = Reading.Value(FluidLevel.LOW))).item(StatusItemId.ENGINE_OIL).level,
        )
        assertEquals(
            StatusLevel.URGENT,
            buildStatusReport(allGood.copy(engineOil = Reading.Value(FluidLevel.CRITICALLY_LOW))).item(StatusItemId.ENGINE_OIL).level,
        )
    }

    @Test
    fun `브레이크액은 부족만 돼도 즉시 조치`() {
        val item = buildStatusReport(allGood.copy(brakeFluid = Reading.Value(FluidLevel.LOW))).item(StatusItemId.BRAKE_FLUID)
        assertEquals(StatusLevel.URGENT, item.level)
    }

    @Test
    fun `연료 경계값 - 21 정상, 20 주의, 15 주의, 10 즉시, 8 즉시`() {
        fun levelAt(p: Int) = buildStatusReport(allGood.copy(fuelPercent = Reading.Value(p))).item(StatusItemId.FUEL).level
        assertEquals(StatusLevel.OK, levelAt(21))
        assertEquals(StatusLevel.CAUTION, levelAt(20))
        assertEquals(StatusLevel.CAUTION, levelAt(15))
        assertEquals(StatusLevel.URGENT, levelAt(10))
        assertEquals(StatusLevel.URGENT, levelAt(8))
    }

    @Test
    fun `문제가 둘 이상이면 즉시 조치가 먼저`() {
        val report = buildStatusReport(
            allGood.copy(washerFluidLow = Reading.Value(true), fuelPercent = Reading.Value(8)),
        )
        assertEquals(
            "연료가 8% 남았어요. 바로 주유하세요. 워셔액이 부족해요. 주유소나 마트에서 보충하세요. 나머지는 모두 정상이에요.",
            report.summary,
        )
    }

    @Test
    fun `문제가 셋 이상이면 두 개만 말하고 나머지는 개수만`() {
        val report = buildStatusReport(
            allGood.copy(
                washerFluidLow = Reading.Value(true),
                engineOil = Reading.Value(FluidLevel.LOW),
                brakeFluid = Reading.Value(FluidLevel.CRITICALLY_LOW),
            ),
        )
        assertTrue(report.summary.startsWith("브레이크액이 매우 부족해요."))
        assertTrue(report.summary.endsWith("그 밖에 점검할 곳이 1곳 더 있어요."))
    }

    @Test
    fun `지원 안 하는 항목은 카드에 표시만 하고 요약에서 뺀다`() {
        val report = buildStatusReport(allGood.copy(transmissionOil = Reading.Unsupported))
        val item = report.item(StatusItemId.TRANSMISSION_OIL)
        assertEquals(StatusLevel.UNSUPPORTED, item.level)
        assertEquals("이 차량에서 지원 안 함", item.detail)
        assertNull(item.advice)
        assertEquals("오늘 차 상태 좋아요.", report.summary)
    }

    @Test
    fun `연료와 배터리를 모두 지원 안 하면 카드 하나`() {
        val report = buildStatusReport(allGood.copy(fuelPercent = Reading.Unsupported))
        assertEquals(StatusLevel.UNSUPPORTED, report.item(StatusItemId.ENERGY).level)
        assertEquals(6, report.items.size)
    }

    @Test
    fun `전기차는 배터리 카드와 충전 안내`() {
        val report = buildStatusReport(
            allGood.copy(fuelPercent = Reading.Unsupported, evBatteryPercent = Reading.Value(9)),
        )
        val item = report.item(StatusItemId.EV_BATTERY)
        assertEquals(StatusLevel.URGENT, item.level)
        assertEquals("배터리가 9% 남았어요. 바로 충전하세요.", item.advice)
    }

    @Test
    fun `처음에는 확인 중, 일부만 읽혔고 문제가 없으면 계속 확인 중`() {
        assertEquals("차 상태를 확인하고 있어요.", buildStatusReport(VehicleSnapshot()).summary)
        val partial = VehicleSnapshot(engineOil = Reading.Value(FluidLevel.NORMAL))
        assertEquals("차 상태를 확인하고 있어요.", buildStatusReport(partial).summary)
    }

    @Test
    fun `일부를 읽는 중이어도 이미 보인 문제는 바로 말한다`() {
        val partial = VehicleSnapshot(washerFluidLow = Reading.Value(true))
        assertEquals("워셔액이 부족해요. 주유소나 마트에서 보충하세요.", buildStatusReport(partial).summary)
    }

    @Test
    fun `전체 상태는 가장 심각한 문제를 따른다`() {
        assertEquals(StatusLevel.OK, buildStatusReport(allGood).overall)
        assertEquals(StatusLevel.CAUTION, buildStatusReport(allGood.copy(washerFluidLow = Reading.Value(true))).overall)
        assertEquals(
            StatusLevel.URGENT,
            buildStatusReport(allGood.copy(washerFluidLow = Reading.Value(true), fuelPercent = Reading.Value(5))).overall,
        )
        assertEquals(StatusLevel.LOADING, buildStatusReport(VehicleSnapshot()).overall)
    }

    @Test
    fun `잔량 카드는 막대 값을 가진다`() {
        val report = buildStatusReport(allGood.copy(fuelPercent = Reading.Value(15)))
        assertEquals(0.15f, report.item(StatusItemId.FUEL).gauge!!, 0.0001f)
        assertNull(report.item(StatusItemId.ODOMETER).gauge)
    }

    @Test
    fun `읽기에 실패한 항목은 확인 불가, 아무것도 못 읽으면 그렇게 말한다`() {
        val failed = VehicleSnapshot(
            engineOil = Reading.Unavailable,
            transmissionOil = Reading.Unavailable,
            brakeFluid = Reading.Unavailable,
            washerFluidLow = Reading.Unavailable,
            fuelPercent = Reading.Unavailable,
            evBatteryPercent = Reading.Unsupported,
            odometerKm = Reading.Unavailable,
        )
        val report = buildStatusReport(failed)
        assertEquals(StatusLevel.UNKNOWN, report.item(StatusItemId.ENGINE_OIL).level)
        assertEquals("차 상태를 읽지 못했어요.", report.summary)
    }
}
