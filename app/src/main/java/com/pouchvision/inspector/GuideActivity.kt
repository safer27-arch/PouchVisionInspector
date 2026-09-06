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


    private fun showGuide(
        type: String
    ) {

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


    /*
     * =========================================================
     * BOTTOM CORNER
     * =========================================================
     */
    private fun showBottomCornerGuide() {

        binding.tvGuideTitle.text =
            "BOTTOM CORNER 주름 검사"

        binding.tvGuideSubtitle.text =
            "Bottom Corner Wrinkle Inspection"

        binding.imgGuideSample.setImageResource(
            R.drawable.guide_bottom_corner
        )

        binding.tvSampleNotice.text =
            "※ 정상 → 주의 → 불량 상태를 이해하기 위한 시뮬레이션 예시입니다."

        binding.tvGuidePurpose.text =
            """
파우치 Bottom Corner 부위의 주름, 접힘 및 국부적인 형상 변화를 확인합니다.

Corner 주변에서 발생하는 비정상적인 표면 변화와 Edge 집중 부위를 찾아 검사자가 확인할 수 있도록 지원합니다.
            """.trimIndent()

        binding.tvGuideRoi.text =
            """
Bottom Corner의 Seal 끝단과 Corner 접힘 발생 가능 영역이 ROI 안에 들어오도록 설정합니다.

문자, Barcode, 강한 반사광은 가능하면 ROI에서 제외하는 것을 권장합니다.
            """.trimIndent()

        binding.tvGuidePoints.text =
            """
• Corner 주름 집중 여부
• 접힘 또는 눌림 형상
• 국부적인 강한 Edge
• Corner 형상 변화
• 정상 영역 대비 표면 패턴 변화
            """.trimIndent()

        binding.tvGuideMethod.text =
            """
1. Bottom Corner가 잘 보이는 사진을 선택합니다.
2. 검사할 Corner에 ROI를 맞춥니다.
3. 사진 확대와 ROI 크기를 조정합니다.
4. 민감도를 설정합니다.
5. 검사를 실행합니다.
6. 빨간 후보 표시와 Corner Score를 함께 확인합니다.
            """.trimIndent()
    }


    /*
     * =========================================================
     * SEAL
     * =========================================================
     */
    private fun showSealGuide() {

        binding.tvGuideTitle.text =
            "SEAL 검사"

        binding.tvGuideSubtitle.text =
            "Seal Inspection"

        binding.imgGuideSample.setImageResource(
            R.drawable.guide_seal
        )

        binding.tvSampleNotice.text =
            "※ Seal 정상·주의·불량 상태를 이해하기 위한 시뮬레이션 예시입니다."

        binding.tvGuidePurpose.text =
            """
파우치 Seal 부위의 균일성, 선형 변화, 주름, 들뜸 및 국부 변형을 확인합니다.

Seal Line을 따라 발생하는 비정상적인 영상 변화를 비교하는 보조 검사입니다.
            """.trimIndent()

        binding.tvGuideRoi.text =
            """
실제 검사하려는 Seal Line을 따라 ROI를 길게 맞춥니다.

좌측 Seal, 우측 Seal, Bottom Seal을 각각 검사하는 방식을 권장합니다.
            """.trimIndent()

        binding.tvGuidePoints.text =
            """
• Seal Line 균일성
• 국부적인 주름
• 들뜸 형태
• 강한 Edge 집중
• Seal 표면 불균일

※ 실제 Seal Width(mm)는 Calibration이 필요합니다.
            """.trimIndent()

        binding.tvGuideMethod.text =
            """
1. 검사할 Seal이 잘 보이는 사진을 선택합니다.
2. Seal Line을 따라 ROI를 맞춥니다.
3. ROI 가로·세로 크기를 조절합니다.
4. 민감도를 설정합니다.
5. SEAL 검사를 실행합니다.
6. 빨간 후보 위치와 Seal Score를 함께 확인합니다.
            """.trimIndent()
    }


    /*
     * =========================================================
     * FORMING
     * =========================================================
     */
    private fun showFormingGuide() {

        binding.tvGuideTitle.text =
            "FORMING 검사"

        binding.tvGuideSubtitle.text =
            "Forming Shape Inspection"

        binding.imgGuideSample.setImageResource(
            R.drawable.guide_forming
        )

        binding.tvSampleNotice.text =
            "※ 제공해주신 파우치 Forming 형상을 기준으로 만든 교육용 시뮬레이션입니다."

        binding.tvGuidePurpose.text =
            """
파우치 Forming Cup의 형상, 주름, 눌림, Dent 및 좌우 형상 차이를 확인합니다.

이번 샘플은 실제 제공해주신 2-Cavity 형태의 Forming 이미지를 기준으로 시각화했습니다.
            """.trimIndent()

        binding.tvGuideRoi.text =
            """
가능하면 Forming Cup 전체 또는 검사하려는 Cup의 Corner와 측벽이 함께 ROI에 들어오도록 설정합니다.

좌우 또는 상·하 형상을 비교하려면 비교 대상이 ROI 안에 충분히 포함되도록 설정합니다.
            """.trimIndent()

        binding.tvGuidePoints.text =
            """
• Cup Corner 형상 변화
• 측벽의 눌림 또는 Dent
• 국부 주름
• 좌우 또는 상·하 형상 차이
• Corner Radius 변화
• 정상 Forming 대비 비대칭
            """.trimIndent()

        binding.tvGuideMethod.text =
            """
1. Forming 전체가 잘 보이는 사진을 선택합니다.
2. 검사 대상 Cup에 ROI를 맞춥니다.
3. 필요하면 사진을 확대합니다.
4. ROI 크기와 민감도를 조정합니다.
5. FORMING 검사를 실행합니다.
6. Wrinkle, Deformation, Symmetry 및 Forming Score를 확인합니다.
            """.trimIndent()
    }


    /*
     * =========================================================
     * TAB
     * =========================================================
     */
    private fun showTabGuide() {

        binding.tvGuideTitle.text =
            "TAB 검사"

        binding.tvGuideSubtitle.text =
            "Tab Position Inspection"

        binding.imgGuideSample.setImageResource(
            R.drawable.guide_tab
        )

        binding.tvSampleNotice.text =
            "※ 제공해주신 실제 Tab 형상을 기준으로 만든 교육용 시뮬레이션입니다."

        binding.tvGuidePurpose.text =
            """
파우치 Tab의 위치, 기울기, Tab-Seal 주변 간격 및 Tab 뿌리부 형상 변화를 확인합니다.

실제 제공해주신 Tab 구조와 주변 Seal 형상을 기준으로 검사 위치를 시각화했습니다.
            """.trimIndent()

        binding.tvGuideRoi.text =
            """
Tab 금속부만 잡지 말고 Tab, Yellow/Orange 절연부, Pouch Seal 기준부가 함께 ROI 안에 들어오도록 설정하는 것을 권장합니다.

주변 기준 형상이 함께 있어야 위치와 기울기 비교가 쉬워집니다.
            """.trimIndent()

        binding.tvGuidePoints.text =
            """
• Tab 중심 위치 편차
• Tab 기울기
• Tab-Seal 간격
• Tab 뿌리부 변형
• 절연부 주변 상태
• 좌·우 기준 대비 위치 변화

※ 현재 앱은 Edge 분포 기반 보조 판정입니다.
            """.trimIndent()

        binding.tvGuideMethod.text =
            """
1. Tab과 주변 Seal이 함께 보이는 사진을 선택합니다.
2. Tab 및 기준 Seal을 포함하도록 ROI를 맞춥니다.
3. 사진 확대와 ROI 크기를 조정합니다.
4. 민감도를 설정합니다.
5. TAB 검사를 실행합니다.
6. Position, Tilt, Spacing, Deformation 및 Tab Score를 확인합니다.
            """.trimIndent()
    }
}
