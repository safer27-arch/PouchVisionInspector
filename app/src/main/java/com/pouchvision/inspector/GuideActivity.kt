package com.pouchvision.inspector

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.pouchvision.inspector.databinding.ActivityGuideBinding

class GuideActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGuideBinding

    companion object {
        const val EXTRA_GUIDE_TYPE = "guide_type"

        const val TYPE_BOTTOM_CORNER = "bottom_corner"
        const val TYPE_SEAL = "seal"
        const val TYPE_FORMING = "forming"
        const val TYPE_TAB = "tab"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding =
            ActivityGuideBinding.inflate(layoutInflater)

        setContentView(binding.root)

        val guideType =
            intent.getStringExtra(EXTRA_GUIDE_TYPE)
                ?: TYPE_BOTTOM_CORNER

        showGuide(guideType)

        binding.btnGuideBack.setOnClickListener {
            finish()
        }
    }

    private fun showGuide(type: String) {

        when (type) {

            TYPE_BOTTOM_CORNER -> {
                showBottomCornerGuide()
            }

            TYPE_SEAL -> {
                showSealGuide()
            }

            TYPE_FORMING -> {
                showFormingGuide()
            }

            TYPE_TAB -> {
                showTabGuide()
            }

            else -> {
                showBottomCornerGuide()
            }
        }
    }

    private fun showBottomCornerGuide() {

        binding.tvGuideTitle.text =
            "BOTTOM CORNER 주름 검사"

        binding.tvGuideSubtitle.text =
            "Bottom Corner Wrinkle Inspection"

        binding.tvGuidePurpose.text =
            """
파우치 하단 Corner 부위에서 발생할 수 있는 주름, 접힘, 국부 형상 변화를 이미지로 확인합니다.

특히 Corner 부근의 비정상적인 선형 변화나 국부 변형 후보를 찾아 검사자가 쉽게 확인할 수 있도록 지원합니다.
            """.trimIndent()

        binding.tvGuideRoi.text =
            """
ROI는 검사하려는 Bottom Corner 부위에 맞춥니다.

권장 범위:
• Bottom Seal 끝단
• Corner 접힘 발생 가능 영역
• 파우치 모서리 형상부

가능하면 문자, Barcode, 강한 반사광이 ROI 안에 많이 포함되지 않도록 설정하는 것이 좋습니다.
            """.trimIndent()

        binding.tvGuidePoints.text =
            """
• Corner 주름 발생 여부
• 접힘 또는 눌림 형상
• 국부적으로 강한 Edge 증가
• 정상 샘플 대비 Corner 형상 변화
• 비정상적인 선형 패턴 집중 여부
            """.trimIndent()

        binding.tvGuideMethod.text =
            """
1. Bottom Corner가 잘 보이는 사진을 선택합니다.
2. 검사할 Corner 부위에 녹색 ROI를 맞춥니다.
3. 필요하면 사진을 확대하고 ROI 가로/세로 크기를 조정합니다.
4. 민감도를 설정합니다.
5. 검사를 실행합니다.
6. 빨간 후보 표시와 Corner Score를 함께 확인합니다.
            """.trimIndent()

        setSampleTexts(
            normal = "정상\nCorner 형상이 매끄럽고\n주름이 거의 없는 상태",
            warning = "주의\n미세 주름 또는\n국부 변화가 보이는 상태",
            ng = "불량\n주름·접힘·변형이\n뚜렷한 상태"
        )
    }

    private fun showSealGuide() {

        binding.tvGuideTitle.text =
            "SEAL 검사"

        binding.tvGuideSubtitle.text =
            "Seal Inspection"

        binding.tvGuidePurpose.text =
            """
파우치 Seal 부위의 균일성, 선형 변화, 주름, 들뜸 및 국부 변형 후보를 확인합니다.

Seal Line을 따라 발생하는 비정상적인 이미지 변화를 수치화하여 비교할 수 있도록 지원합니다.
            """.trimIndent()

        binding.tvGuideRoi.text =
            """
ROI는 실제 검사하려는 Seal Line을 따라 길게 맞춥니다.

권장 범위:
• 좌측 Seal
• 우측 Seal
• Bottom Seal
• Seal 끝단 또는 Corner 접속부

현재 버전에서는 한 번에 한 Seal 영역을 선택해 검사하는 방식을 권장합니다.
            """.trimIndent()

        binding.tvGuidePoints.text =
            """
• Seal 선형 균일성
• 국부적인 주름 또는 접힘
• 들뜸으로 추정되는 형상 변화
• 강한 Edge 집중 부위
• 정상 Seal 대비 불규칙한 패턴

※ 실제 Seal Width(mm)는 기준 길이 또는 Calibration 설정 후 적용해야 합니다.
            """.trimIndent()

        binding.tvGuideMethod.text =
            """
1. 검사할 Seal이 잘 보이는 사진을 선택합니다.
2. Seal Line을 따라 ROI를 길게 맞춥니다.
3. 사진 확대 또는 ROI 가로/세로 크기를 조절합니다.
4. 민감도를 설정합니다.
5. SEAL 검사를 실행합니다.
6. 빨간 후보 위치와 Seal Score를 함께 확인합니다.
            """.trimIndent()

        setSampleTexts(
            normal = "정상\nSeal Line이 비교적 일정하고\n국부 변화가 적은 상태",
            warning = "주의\n일부 선형 변화나\n미세 주름이 보이는 상태",
            ng = "불량\n강한 변형·주름·들뜸이\n뚜렷한 상태"
        )
    }

    private fun showFormingGuide() {

        binding.tvGuideTitle.text =
            "FORMING 검사"

        binding.tvGuideSubtitle.text =
            "Forming Shape Inspection"

        binding.tvGuidePurpose.text =
            """
파우치 Forming 부위의 전체 형상, 주름, 눌림, Dent 및 좌우 비대칭 후보를 확인합니다.

정상 형상과 비교했을 때 국부적으로 달라진 부위가 있는지 검사하는 보조 기능입니다.
            """.trimIndent()

        binding.tvGuideRoi.text =
            """
ROI는 Forming 형상이 충분히 포함되도록 설정합니다.

권장 범위:
• Forming 중심부
• Forming Corner
• 형상 변화가 의심되는 측면
• 좌우 대칭 비교가 가능한 영역

비대칭을 볼 때는 가능한 한 좌우 형상이 ROI 안에 함께 포함되도록 맞추는 것이 좋습니다.
            """.trimIndent()

        binding.tvGuidePoints.text =
            """
• Forming 형상 균일성
• 국부 주름
• 눌림 또는 Dent
• 좌우 비대칭
• 비정상 Edge 집중
• 정상 제품 대비 형상 변화
            """.trimIndent()

        binding.tvGuideMethod.text =
            """
1. Forming 형상이 잘 보이는 사진을 선택합니다.
2. 검사 대상 Forming 부위가 충분히 포함되도록 ROI를 맞춥니다.
3. 필요하면 확대 및 ROI 크기를 조정합니다.
4. 민감도를 설정합니다.
5. FORMING 검사를 실행합니다.
6. Wrinkle, Deformation, Symmetry Error와 Forming Score를 함께 확인합니다.
            """.trimIndent()

        setSampleTexts(
            normal = "정상\n형상이 비교적 균일하고\n좌우 차이가 적은 상태",
            warning = "주의\n미세 주름·눌림 또는\n형상 편차가 보이는 상태",
            ng = "불량\nDent·심한 주름·비대칭이\n뚜렷한 상태"
        )
    }

    private fun showTabGuide() {

        binding.tvGuideTitle.text =
            "TAB 검사"

        binding.tvGuideSubtitle.text =
            "Tab Position Inspection"

        binding.tvGuidePurpose.text =
            """
Tab의 위치, 기울기, 주변 간격 및 국부 변형 후보를 이미지로 확인합니다.

현재 버전은 ROI 내부의 Edge 분포를 이용해 위치 편차와 기울기 후보를 비교하는 보조 분석 방식입니다.
            """.trimIndent()

        binding.tvGuideRoi.text =
            """
ROI에는 Tab과 주변 Pouch 또는 Seal 기준부가 함께 보이도록 맞춥니다.

권장 범위:
• Tab 전체
• Tab 뿌리부
• Tab과 Seal의 경계
• 위치 비교 기준이 되는 주변 형상

Tab만 너무 작게 잡으면 주변 기준과 비교하기 어려우므로 약간 넓게 ROI를 잡는 것이 좋습니다.
            """.trimIndent()

        binding.tvGuidePoints.text =
            """
• Tab 위치 편차
• Tab 기울기
• Seal과의 간격 Balance
• Tab 뿌리부 국부 변형
• 주변 형상 대비 비정상 Edge

※ 실제 위치(mm), 간격(mm), 각도 판정은 기준 좌표와 Calibration을 추가해야 합니다.
            """.trimIndent()

        binding.tvGuideMethod.text =
            """
1. Tab과 주변 기준부가 함께 보이는 사진을 선택합니다.
2. Tab과 주변 Seal이 ROI에 포함되도록 맞춥니다.
3. 확대 및 ROI 크기를 조정합니다.
4. 민감도를 설정합니다.
5. TAB 검사를 실행합니다.
6. Position, Tilt, Spacing, Deformation 및 Tab Score를 함께 확인합니다.
            """.trimIndent()

        setSampleTexts(
            normal = "정상\nTab 위치와 기울기가\n비교적 안정적인 상태",
            warning = "주의\n미세 위치 편차 또는\n기울기가 보이는 상태",
            ng = "불량\n큰 위치 편차·기울기·\n국부 변형이 있는 상태"
        )
    }

    private fun setSampleTexts(
        normal: String,
        warning: String,
        ng: String
    ) {

        binding.tvSampleNormal.text =
            normal

        binding.tvSampleWarning.text =
            warning

        binding.tvSampleNg.text =
            ng
    }
}
