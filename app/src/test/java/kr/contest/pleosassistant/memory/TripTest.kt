package kr.contest.pleosassistant.memory

import kr.contest.pleosassistant.status.Reading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/** 목적지별 챙길 것 · 음성 부탁 · 동승자 물건 */
class TripTest {

    private val now = LocalDateTime.of(2026, 11, 10, 8, 30)
    private val today: LocalDate = now.toLocalDate()

    private fun parked() = Seat.entries
        .fold(MemoryState()) { s, seat -> s.onSeat(seat, Reading.Value(false), now) }
        .onGear(Reading.Value(Gear.PARK), now)

    private fun MemoryState.withOffice() = addRoutine("회사", listOf("텀블러", "사원증", "지갑"))
    private fun MemoryState.say(text: String) = submit(text, now, spoken = true)
    private fun MemoryState.navi(meters: Int, destination: String = "42dot", bookmark: String? = "회사") =
        onNavi(NaviStatus(destination, bookmark, meters, meters / 10), now)

    // ---- 말 알아듣기 ----

    @Test
    fun `임시 소지품 부탁`() {
        val c = parseVoiceCommand("나 오늘 보고서류 가져왔어 챙기라고 해줘", today, emptyList())
        assertEquals(VoiceCommand.Remember(ParsedBelonging("보고서류", null, today), null), c)
    }

    @Test
    fun `이름으로 시작하면 그 동승자의 물건`() {
        val c = parseVoiceCommand("민수 가방 뒷자리에 뒀어", today, listOf("민수"))
        assertEquals(VoiceCommand.Remember(ParsedBelonging("가방", "뒷자리", null), "민수"), c)
        assertEquals(VoiceCommand.Collected("가방", "민수"), parseVoiceCommand("민수님 가방 챙겼어", today, listOf("민수")))
    }

    @Test
    fun `챙겼어 · 다 챙겼어`() {
        assertEquals(VoiceCommand.Collected("보고서류", null), parseVoiceCommand("보고서류 챙겼어", today, emptyList()))
        assertEquals(VoiceCommand.Collected(null, null), parseVoiceCommand("다 챙겼어요", today, emptyList()))
    }

    @Test
    fun `목적지마다 늘 챙길 것`() {
        assertEquals(
            VoiceCommand.AddRoutine("회사", listOf("텀블러", "사원증", "지갑")),
            parseVoiceCommand("회사 갈 때 텀블러, 사원증, 지갑 챙기라고 해줘", today, emptyList()),
        )
        assertEquals(
            VoiceCommand.AddRoutine("헬스장", listOf("운동화", "물", "회원카드")),
            parseVoiceCommand("헬스장 갈 때는 운동화랑 물 회원카드 챙겨줘", today, emptyList()),
        )
    }

    @Test
    fun `부탁이 아닌 말은 받지 않는다`() {
        assertNull(parseVoiceCommand("오늘 날씨 어때", today, emptyList()))
        assertEquals(MemoryMessages.VOICE_NOT_UNDERSTOOD, parked().say("오늘 날씨 어때").announcement!!.text)
    }

    @Test
    fun `매일 챙길 것`() {
        assertEquals(VoiceCommand.AddRoutine(EVERY_DAY, listOf("텀블러")), parseVoiceCommand("매일 텀블러 챙기라고해줘", today, emptyList()))
        val state = parked().say("매일 텀블러 챙기라고 해줘")
        assertEquals("매일 텀블러 챙기라고 알려 드릴게요.", state.announcement!!.text)

        // 어느 목적지로 가든 함께 말하고, 매일은 목적지 이름으로 쓰지 않는다
        val departed = state.withOffice().navi(3000).onGear(Reading.Value(Gear.DRIVE), now)
        assertEquals("회사 가시네요. 텀블러, 사원증, 지갑 챙기셨나요?", departed.announcement!!.text)
        val gym = state.navi(3000, destination = "강남 헬스장", bookmark = null).onGear(Reading.Value(Gear.DRIVE), now)
        assertEquals("강남 헬스장 가시네요. 텀블러 챙기셨나요?", gym.announcement!!.text)

        // 내비 없이 다녀도 내릴 때 말한다
        val plain = state.onGear(Reading.Value(Gear.DRIVE), now).onGear(Reading.Value(Gear.PARK), now)
        assertEquals("텀블러 챙기세요.", plain.announcement!!.text)
    }

    // ---- 목적지 맞추기 ----

    @Test
    fun `즐겨찾기 회사이거나 목적지 이름에 장소 말이 있으면 그 목적지의 챙길 것`() {
        val routines = listOf(Routine("회사", listOf("사원증")), Routine("헬스장", listOf("운동화")))
        assertEquals("회사", routineFor(routines, "42dot", "회사")?.place)
        assertEquals("헬스장", routineFor(routines, "강남 스포츠 헬스장", null)?.place)
        assertNull(routineFor(routines, "이마트", null))
    }

    // ---- 한 번의 길 ----

    @Test
    fun `회사로 찍고 출발 · 도착 직전 · 도착 직후에 한 번씩 말한다`() {
        val set = parked().withOffice().navi(3000)
        assertNull("정차 중에 목적지만 정하면 말하지 않는다", set.announcement)
        assertEquals(listOf("텀블러", "사원증", "지갑"), set.report().trip!!.routineItems)

        val departed = set.onGear(Reading.Value(Gear.DRIVE), now)
        assertEquals("회사 가시네요. 텀블러, 사원증, 지갑 챙기셨나요?", departed.announcement!!.text)

        val asked = departed.say("나 오늘 보고서류 가져왔어 챙기라고 해줘")
        assertEquals("보고서류 기억할게요. 회사 도착 전에 알려 드릴게요.", asked.announcement!!.text)

        val near = asked.navi(400)
        assertEquals("곧 회사에 도착해요. 텀블러, 사원증, 지갑, 보고서류 챙기세요.", near.announcement!!.text)
        assertEquals("한 번만 말한다", near.announcement, near.navi(300).announcement)

        val arrived = near.navi(30).onNaviArrived(now)
        assertEquals("회사에 도착했어요. 텀블러, 사원증, 지갑, 보고서류 잊지 마세요.", arrived.announcement!!.text)
        val list = arrived.report().checklist!!
        assertEquals("회사에 도착했어요", list.title)
        assertEquals(listOf("텀블러", "사원증", "지갑", "보고서류"), list.rows.map { it.item })
        assertEquals("P 에 놓아도 또 알리지 않는다", arrived.announcement, arrived.onGear(Reading.Value(Gear.PARK), now).announcement)

        // 챙겼다고 하면 임시 소지품은 바로 지우고, 늘 챙길 것은 표시만 한다
        val collected = arrived.say("보고서류 챙겼어").check("r:텀블러")
        assertTrue(collected.data.belongings.isEmpty())
        assertEquals(2, collected.report().checklist!!.done)
        assertEquals(listOf("텀블러", "사원증", "지갑"), collected.data.routines.single().items)
    }

    @Test
    fun `내비 도착 알림이 없어도 도착 직전 뒤 P 에 놓으면 도착`() {
        val arrived = parked().withOffice().navi(3000)
            .onGear(Reading.Value(Gear.DRIVE), now).navi(200)
            .onGear(Reading.Value(Gear.PARK), now)
        assertEquals("회사에 도착했어요", arrived.report().checklist!!.title)
    }

    @Test
    fun `가는 길에 잠깐 P 에 놓은 것은 도착이 아니다`() {
        val stopped = parked().withOffice().addBelonging("트렁크에 우산 넣었어", now).navi(3000)
            .onGear(Reading.Value(Gear.DRIVE), now)
            .onGear(Reading.Value(Gear.PARK), now)
        assertNull(stopped.arrival)
    }

    @Test
    fun `목적지의 챙길 것이 없으면 출발 때 말하지 않는다`() {
        val departed = parked().navi(3000, destination = "이마트", bookmark = null).onGear(Reading.Value(Gear.DRIVE), now)
        assertNull(departed.announcement)
        assertEquals("이마트", departed.report().trip!!.place)
    }

    // ---- 동승자 물건 ----

    @Test
    fun `동승자 물건은 내릴 때 따로 알리고, 이름을 넣어 지운다`() {
        val seated = parked()
            .onSeat(Seat.PASSENGER, Reading.Value(true), now)
            .namePassenger(Seat.PASSENGER, "민수")
            .say("민수 가방 챙기라고 해줘")
        assertEquals("민수", seated.data.belongings.single().owner)
        assertEquals("민수님 가방 기억할게요. 내릴 때 알려 드릴게요.", seated.announcement!!.text)
        assertEquals("민수님 · 위치 모름 · 도착 시 알림", seated.report().belongings.single().note)

        val left = seated.onSeat(Seat.PASSENGER, Reading.Value(false), now)
        assertEquals("민수님, 가방 챙기세요.", left.announcement!!.text)
        assertEquals("민수님이 내려요", left.report().checklist!!.title)

        assertTrue(left.say("민수 가방 챙겼어").data.belongings.isEmpty())
    }

    @Test
    fun `도착 안내는 내 물건과 동승자 물건을 나눠 말한다`() {
        val items = listOf(
            CheckItem("r:텀블러", "텀블러", null, null, null),
            CheckItem("b:1", "가방", "민수", null, 1),
        )
        assertEquals("곧 회사에 도착해요. 텀블러 챙기세요. 민수님은 가방 챙기세요.", MemoryMessages.approach("회사", items))
    }

    @Test
    fun `화면에서 고른 주인으로 기억한다`() {
        val state = parked().addBelonging("우산", now, owner = "근호")
        assertEquals("근호", state.data.belongings.single().owner)
    }

    @Test
    fun `남은 거리 · 시간 표시`() {
        assertEquals("1.2km · 4분", remainLabel(1200, 240))
        assertEquals("350m · 1분", remainLabel(350, 59))
        assertNull(remainLabel(null, null))
    }

    private fun MemoryState.report() = buildMemoryReport(this, today)
}
