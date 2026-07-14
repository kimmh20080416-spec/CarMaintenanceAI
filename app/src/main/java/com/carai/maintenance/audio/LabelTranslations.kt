package com.carai.maintenance.audio

/**
 * 학습 시 사용한 영문 클래스 이름(labels.txt)을 한글 표시 이름과 설명으로 매핑합니다.
 * 새로운 클래스로 다시 학습하면 이 표에도 항목을 추가해주세요.
 * 매핑에 없는 라벨은 원문 그대로 표시됩니다.
 */
object LabelTranslations {

    data class LocalizedLabel(val displayName: String, val description: String)

    private val table = mapOf(
        "background" to LocalizedLabel(
            "배경음 / 소음 없음",
            "특별한 이상음이 감지되지 않았습니다. 소음이 나는 부위에 마이크를 더 가까이 대고 다시 시도해보세요."
        ),
        "brake" to LocalizedLabel(
            "브레이크",
            "브레이크 관련 마찰음일 가능성이 있습니다. 제동할 때 소리가 난다면 브레이크 패드 마모를 점검해보세요."
        ),
        "engine" to LocalizedLabel(
            "엔진",
            "엔진 쪽 소음으로 보입니다. 엔진 내부 부품, 벨트, 마운트 등을 점검해보세요."
        ),
        "interior" to LocalizedLabel(
            "실내",
            "차량 실내에서 나는 소음으로 보입니다. 내장재 마찰음이나 헐거워진 부품이 없는지 확인해보세요."
        ),
        "tire" to LocalizedLabel(
            "타이어",
            "타이어/휠 쪽 소음일 가능성이 있습니다. 타이어 마모 상태나 이물질이 끼었는지 확인해보세요."
        ),
        "undercarriage" to LocalizedLabel(
            "하체",
            "차량 하체(서스펜션, 구동계 등)에서 나는 소음으로 보입니다. 하체 부품 점검을 권장합니다."
        )
    )

    fun localize(rawLabel: String): LocalizedLabel {
        val key = rawLabel.trim().lowercase()
        return table[key] ?: LocalizedLabel(rawLabel, "이 클래스에 대한 설명이 아직 등록되지 않았습니다.")
    }
}
