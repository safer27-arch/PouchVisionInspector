package com.pouchvision.inspector

import android.content.Context
import androidx.appcompat.app.AlertDialog

/*
 * =============================================================
 * 촬영 표준화 가이드
 * =============================================================
 *
 * 목적
 * - 작업자별 촬영 거리 / 각도 / 조명 / ROI 위치 편차를 줄입니다.
 * - 검사 알고리즘과 OK/NG 판정 기준은 변경하지 않습니다.
 * - 5개 검사 항목에서 공통으로 사용할 수 있습니다.
 *
 * 적용 대상
 * - Bottom Corner
 * - Seal
 * - Forming
 * - Tab
 * - Disassembly
 * =============================================================
 */

object CaptureGuideHelper {

    const val TYPE_BOTTOM_CORNER =
        "BOTTOM CORNER"

    const val TYPE_SEAL =
        "SEAL"

    const val TYPE_FORMING =
        "FORMING"

    const val TYPE_TAB =
        "TAB"

    const val TYPE_DISASSEMBLY =
        "DISASSEMBLY"

    data class CaptureGuide(

        val title: String,

        val targetPosition: String,

        val recommendedFill: String,

        val angleGuide: String,

        val lightingGuide: String,

        val additionalGuide: String
    )

    /*
     * =========================================================
     * 검사 항목별 촬영 가이드
     * =========================================================
     */

    fun getGuide(
        inspectionType: String
    ): CaptureGuide {

        return when {

            inspectionType.equals(
                TYPE_BOTTOM_CORNER,
                ignoreCase = true
            ) -> {

                CaptureGuide(
                    title =
                        "BOTTOM CORNER 촬영 가이드",

                    targetPosition =
                        "검사할 Bottom Corner가 ROI 중앙에 오도록 맞춰주세요.",

                    recommendedFill =
                        "Corner 검사부위가 ROI의 약 60~80%를 차지하도록 촬영해주세요.",

                    angleGuide =
                        "카메라를 Pouch 표면과 최대한 평행하게 유지해주세요.",

                    lightingGuide =
                        "은색 Pouch 표면의 강한 직접 반사와 짙은 그림자를 피해주세요.",

                    additionalGuide =
                        "Fixture, Roller, 문자보다 실제 Corner 주름 부위가 ROI 안에서 가장 잘 보이도록 맞춰주세요."
                )
            }

            inspectionType.equals(
                TYPE_SEAL,
                ignoreCase = true
            ) -> {

                CaptureGuide(
                    title =
                        "SEAL 촬영 가이드",

                    targetPosition =
                        "검사할 Seal Line과 바로 인접한 Pouch 영역을 ROI 안에 함께 포함해주세요.",

                    recommendedFill =
                        "Seal 검사부위가 ROI의 약 70~85%를 차지하도록 촬영해주세요.",

                    angleGuide =
                        "Seal Line이 화면에서 과도하게 기울어지지 않도록 카메라를 최대한 평행하게 유지해주세요.",

                    lightingGuide =
                        "Seal 표면의 반사광이 한쪽에 집중되지 않도록 조명을 조절해주세요.",

                    additionalGuide =
                        "문자, Barcode, Fixture Edge가 ROI의 중심을 차지하지 않도록 해주세요."
                )
            }

            inspectionType.equals(
                TYPE_FORMING,
                ignoreCase = true
            ) -> {

                CaptureGuide(
                    title =
                        "FORMING 촬영 가이드",

                    targetPosition =
                        "Forming Cup과 좌우/상하 형상 경계가 ROI 안에 충분히 포함되도록 맞춰주세요.",

                    recommendedFill =
                        "Forming 검사부위가 ROI의 약 70~90%를 차지하도록 촬영해주세요.",

                    angleGuide =
                        "카메라가 Forming 면을 비스듬히 보지 않도록 정면에 가깝게 유지해주세요.",

                    lightingGuide =
                        "형상 모서리에 강한 하이라이트가 생기거나 한쪽이 지나치게 어두워지지 않게 해주세요.",

                    additionalGuide =
                        "전체 형상을 비교할 수 있도록 한쪽 Corner만 지나치게 확대하지 않는 것이 좋습니다."
                )
            }

            inspectionType.equals(
                TYPE_TAB,
                ignoreCase = true
            ) -> {

                CaptureGuide(
                    title =
                        "TAB 촬영 가이드",

                    targetPosition =
                        "Tab과 Tab 주변 Seal 경계가 ROI 안에 함께 포함되도록 맞춰주세요.",

                    recommendedFill =
                        "Tab 및 주변 기준부가 ROI의 약 60~80%를 차지하도록 촬영해주세요.",

                    angleGuide =
                        "Tab의 기울기와 위치 비교가 가능하도록 카메라 회전을 최대한 일정하게 유지해주세요.",

                    lightingGuide =
                        "Tab 금속면과 Pouch 표면의 강한 반사로 경계가 사라지지 않도록 조명을 조절해주세요.",

                    additionalGuide =
                        "Tab만 크게 확대하기보다 주변 Seal과 기준 Edge를 함께 포함하는 것이 좋습니다."
                )
            }

            inspectionType.equals(
                TYPE_DISASSEMBLY,
                ignoreCase = true
            ) -> {

                CaptureGuide(
                    title =
                        "분해검사 촬영 가이드",

                    targetPosition =
                        "확인할 내부 영역이 ROI 중앙에 오도록 하고 필요한 구조물이 빠지지 않게 해주세요.",

                    recommendedFill =
                        "확인 대상이 ROI의 약 70~90%를 차지하도록 촬영해주세요.",

                    angleGuide =
                        "분해된 Sample을 가능한 평평하게 놓고 카메라를 정면에 가깝게 유지해주세요.",

                    lightingGuide =
                        "전극/분리막/Pouch 표면의 반사와 손 그림자가 검사 부위를 가리지 않게 해주세요.",

                    additionalGuide =
                        "주변 작업대나 불필요한 물체보다 실제 확인 대상이 ROI 대부분을 차지하도록 촬영해주세요."
                )
            }

            else -> {

                CaptureGuide(
                    title =
                        "촬영 가이드",

                    targetPosition =
                        "검사 대상이 ROI 중앙에 오도록 맞춰주세요.",

                    recommendedFill =
                        "검사 대상이 ROI의 약 60~80%를 차지하도록 촬영해주세요.",

                    angleGuide =
                        "카메라를 검사 대상면과 최대한 평행하게 유지해주세요.",

                    lightingGuide =
                        "강한 직접 반사와 짙은 그림자를 피해주세요.",

                    additionalGuide =
                        "검사 대상 외의 불필요한 배경이 ROI를 많이 차지하지 않게 해주세요."
                )
            }
        }
    }

    /*
     * =========================================================
     * 전체 가이드 문구
     * =========================================================
     */

    fun buildGuideText(
        inspectionType: String
    ): String {

        val guide =
            getGuide(
                inspectionType
            )

        return """
✓ 검사부위를 ROI 중앙에 위치

✓ ${guide.recommendedFill}

✓ ${guide.angleGuide}

✓ ${guide.lightingGuide}

✓ 흔들림 없이 촬영하고 카메라 렌즈가 깨끗한지 확인해주세요.

✓ 가능하면 같은 검사 항목은 매일 비슷한 거리와 방향에서 촬영해주세요.

항목별 추가 안내
${guide.additionalGuide}

※ 촬영 가이드는 사진 편차를 줄이기 위한 보조 기능입니다.
※ 실제 판정 기준과는 별도이며 검사 Score를 직접 변경하지 않습니다.
        """.trimIndent()
    }

    /*
     * =========================================================
     * 짧은 가이드 문구
     *
     * 검사 화면 상단이나 Toast 등에 사용할 수 있습니다.
     * =========================================================
     */

    fun buildShortGuideText(
        inspectionType: String
    ): String {

        val guide =
            getGuide(
                inspectionType
            )

        return """
${guide.targetPosition}
${guide.recommendedFill}
카메라 각도와 조명을 일정하게 유지해주세요.
        """.trimIndent()
    }

    /*
     * =========================================================
     * 공용 촬영 가이드 Dialog
     * =========================================================
     */

    fun showGuideDialog(
        context: Context,
        inspectionType: String
    ) {

        val guide =
            getGuide(
                inspectionType
            )

        AlertDialog.Builder(
            context
        )
            .setTitle(
                guide.title
            )
            .setMessage(
                buildGuideText(
                    inspectionType
                )
            )
            .setPositiveButton(
                "확인",
                null
            )
            .show()
    }
}
