package com.pouchvision.inspector

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.pouchvision.inspector.databinding.ActivityMenuBinding

class MenuActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMenuBinding

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(
            savedInstanceState
        )

        binding =
            ActivityMenuBinding.inflate(
                layoutInflater
            )

        setContentView(
            binding.root
        )

        /*
         * Model / Line 선택 영역 초기화
         */
        setupProductionContext()

        /*
         * 기존 메뉴 버튼 연결
         */
        setupButtons()
    }

    override fun onResume() {

        super.onResume()

        /*
         * 다른 화면에서 돌아왔을 때도
         * 현재 Model / Line 표시를 최신 상태로 유지합니다.
         */
        refreshCurrentProductionText()
    }

    /*
     * =========================================================
     * Model / Line 생산 조건 설정
     * =========================================================
     */

    private fun setupProductionContext() {

        val models =
            ProductionContextStore.getModels(
                this
            )

        val lines =
            ProductionContextStore.getLines(
                this
            )

        val modelAdapter =
            ArrayAdapter(
                this,
                android.R.layout.simple_spinner_item,
                models
            )

        modelAdapter.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item
        )

        binding.spinnerModel.adapter =
            modelAdapter

        val lineAdapter =
            ArrayAdapter(
                this,
                android.R.layout.simple_spinner_item,
                lines
            )

        lineAdapter.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item
        )

        binding.spinnerLine.adapter =
            lineAdapter

        /*
         * 앱에 저장되어 있는 마지막 선택값을 불러와
         * Spinner의 현재 위치에 맞춥니다.
         */
        val current =
            ProductionContextStore.getCurrent(
                this
            )

        val modelPosition =
            models.indexOf(
                current.model
            )

        if (
            modelPosition >= 0
        ) {

            binding.spinnerModel.setSelection(
                modelPosition
            )
        }

        val linePosition =
            lines.indexOf(
                current.line
            )

        if (
            linePosition >= 0
        ) {

            binding.spinnerLine.setSelection(
                linePosition
            )
        }

        refreshCurrentProductionText()

        /*
         * 선택 적용
         */
        binding.btnProductionApply
            .setOnClickListener {

                val selectedModel =
                    binding.spinnerModel.selectedItem
                        ?.toString()
                        ?.trim()
                        .orEmpty()

                val selectedLine =
                    binding.spinnerLine.selectedItem
                        ?.toString()
                        ?.trim()
                        .orEmpty()

                if (
                    selectedModel.isBlank() ||
                    selectedLine.isBlank()
                ) {

                    Toast.makeText(
                        this,
                        "Model과 Line을 선택해주세요.",
                        Toast.LENGTH_SHORT
                    ).show()

                    return@setOnClickListener
                }

                ProductionContextStore.setCurrent(
                    context = this,
                    model = selectedModel,
                    line = selectedLine
                )

                refreshCurrentProductionText()

                Toast.makeText(
                    this,
                    "생산 조건이 적용되었습니다.\n" +
                        "$selectedModel / $selectedLine",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    /*
     * =========================================================
     * 현재 선택 표시
     * =========================================================
     */

    private fun refreshCurrentProductionText() {

        val current =
            ProductionContextStore.getCurrent(
                this
            )

        binding.tvCurrentProductionContext.text =
            "현재 선택 : ${current.model}  |  ${current.line}"
    }

    /*
     * =========================================================
     * 기존 메뉴 버튼
     * =========================================================
     */

    private fun setupButtons() {

        /*
         * =====================================================
         * 종합검사
         * =====================================================
         */
        binding.btnTotalInspection
            .setOnClickListener {

                startActivity(
                    Intent(
                        this,
                        TotalInspectionActivity::class.java
                    )
                )
            }

        /*
         * =====================================================
         * 품질 Dashboard
         * =====================================================
         */
        binding.btnDashboard
            .setOnClickListener {

                startActivity(
                    Intent(
                        this,
                        DashboardActivity::class.java
                    )
                )
            }

        /*
         * =====================================================
         * Bottom Corner 검사
         * =====================================================
         */
        binding.btnBottomCorner
            .setOnClickListener {

                startActivity(
                    Intent(
                        this,
                        MainActivity::class.java
                    )
                )
            }

        binding.btnBottomCornerGuide
            .setOnClickListener {

                openGuide(
                    GuideActivity.TYPE_BOTTOM_CORNER
                )
            }

        /*
         * =====================================================
         * Seal 검사
         * =====================================================
         */
        binding.btnSealInspection
            .setOnClickListener {

                startActivity(
                    Intent(
                        this,
                        SealActivity::class.java
                    )
                )
            }

        binding.btnSealGuide
            .setOnClickListener {

                openGuide(
                    GuideActivity.TYPE_SEAL
                )
            }

        /*
         * =====================================================
         * Forming 검사
         * =====================================================
         */
        binding.btnFormingInspection
            .setOnClickListener {

                startActivity(
                    Intent(
                        this,
                        FormingActivity::class.java
                    )
                )
            }

        binding.btnFormingGuide
            .setOnClickListener {

                openGuide(
                    GuideActivity.TYPE_FORMING
                )
            }

        /*
         * =====================================================
         * Tab 검사
         * =====================================================
         */
        binding.btnTabInspection
            .setOnClickListener {

                startActivity(
                    Intent(
                        this,
                        TabActivity::class.java
                    )
                )
            }

        binding.btnTabGuide
            .setOnClickListener {

                openGuide(
                    GuideActivity.TYPE_TAB
                )
            }

        /*
         * =====================================================
         * 분해검사
         * =====================================================
         */
        binding.btnDisassemblyInspection
            .setOnClickListener {

                startActivity(
                    Intent(
                        this,
                        DisassemblyActivity::class.java
                    )
                )
            }

        /*
         * =====================================================
         * 검사 이력
         * =====================================================
         */
        binding.btnHistory
            .setOnClickListener {

                startActivity(
                    Intent(
                        this,
                        HistoryActivity::class.java
                    )
                )
            }
    }

    /*
     * =========================================================
     * 검사 안내 화면
     * =========================================================
     */

    private fun openGuide(
        guideType: String
    ) {

        val intent =
            Intent(
                this,
                GuideActivity::class.java
            )

        intent.putExtra(
            GuideActivity.EXTRA_GUIDE_TYPE,
            guideType
        )

        startActivity(
            intent
        )
    }
}
