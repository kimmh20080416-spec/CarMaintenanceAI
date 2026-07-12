package com.carai.maintenance.data

data class MaintenanceItem(
    val name: String,
    val intervalKm: Int,
    val intervalMonths: Int,
    val note: String
)

data class MaintenanceRecommendation(
    val item: MaintenanceItem,
    val status: Status,
    val kmUntilDue: Int
) {
    enum class Status { OVERDUE, DUE_SOON, OK }
}

/**
 * 일반적인 국내/수입 승용차 기준의 표준 소모품 교체 주기입니다.
 * 실제 제조사 매뉴얼과 다를 수 있으니 참고용으로만 사용하세요.
 * (엔진 종류: 가솔린/디젤에 따라 별도 리스트 적용)
 */
object MaintenanceSchedule {

    private val gasolineItems = listOf(
        MaintenanceItem("엔진오일 & 오일필터", 10000, 6, "가혹조건(단거리/정체 잦음) 시 5,000km마다"),
        MaintenanceItem("에어필터(에어클리너)", 20000, 12, "먼지 많은 환경에서는 더 자주"),
        MaintenanceItem("에어컨(캐빈) 필터", 15000, 12, "미세먼지 심한 계절엔 더 자주 점검"),
        MaintenanceItem("점화플러그", 40000, 24, "이리듐 플러그는 60,000~100,000km까지 가능"),
        MaintenanceItem("브레이크 패드(전)", 30000, 24, "제동습관에 따라 편차 큼, 두께 3mm 이하 시 교체"),
        MaintenanceItem("브레이크 패드(후)", 40000, 24, "전륜보다 마모 느림"),
        MaintenanceItem("브레이크 오일", 40000, 24, "2년 주기 권장, 흡습 시 제동력 저하"),
        MaintenanceItem("냉각수(부동액)", 40000, 24, "장기 미교체 시 워터펌프/라디에이터 손상 위험"),
        MaintenanceItem("변속기 오일(자동)", 60000, 48, "가혹조건 시 40,000km"),
        MaintenanceItem("타이밍벨트/체인 점검", 100000, 84, "타이밍벨트 차량은 60,000~100,000km 교체 필수"),
        MaintenanceItem("타이어", 50000, 48, "편마모/트레드 1.6mm 이하 시 즉시 교체"),
        MaintenanceItem("배터리", 40000, 36, "시동 약해짐, 3~4년 주기"),
        MaintenanceItem("와이퍼 블레이드", 10000, 12, "발수력 저하, 소음 발생 시 조기 교체")
    )

    private val dieselItems = gasolineItems.filter { it.name != "점화플러그" } + listOf(
        MaintenanceItem("연료필터(디젤)", 20000, 24, "디젤 연료 계통 보호를 위해 정기 교체 필수"),
        MaintenanceItem("DPF(매연저감장치) 점검", 40000, 24, "재생 실패 잦으면 전문 점검 필요"),
        MaintenanceItem("글로우 플러그", 60000, 48, "냉시동 어려움 발생 시 조기 점검")
    )

    fun itemsFor(engineType: EngineType): List<MaintenanceItem> =
        if (engineType == EngineType.DIESEL) dieselItems else gasolineItems

    /**
     * 현재 주행거리를 기준으로 각 항목이 이번 교체 주기 내에서
     * 몇 km를 주행했는지 계산해 상태를 매깁니다.
     * (최초 등록/직전 교체 시점 정보가 없으므로, 주기의 배수 위치로 근사 추정합니다.)
     */
    fun recommend(currentKm: Int, engineType: EngineType): List<MaintenanceRecommendation> {
        return itemsFor(engineType).map { item ->
            val remainderInCycle = currentKm % item.intervalKm
            val kmUntilDue = item.intervalKm - remainderInCycle
            val status = when {
                kmUntilDue <= 0 || remainderInCycle == 0 && currentKm > 0 -> MaintenanceRecommendation.Status.DUE_SOON
                kmUntilDue <= item.intervalKm * 0.1 -> MaintenanceRecommendation.Status.DUE_SOON
                else -> MaintenanceRecommendation.Status.OK
            }
            MaintenanceRecommendation(item, status, kmUntilDue)
        }.sortedBy { it.kmUntilDue }
    }
}

enum class EngineType(val label: String) {
    GASOLINE("가솔린"),
    DIESEL("디젤"),
    HYBRID("하이브리드")
}
