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

        /*
         * 저장되어 있는 현재 Model / Line
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

        /*
         * 현재 Model에 맞는 Line 목록을 먼저 표시합니다.
         */
        updateLineSpinnerForModel(
            model = current.model,
            preferredLine = current.line
        )

        /*
         * Model을 바꾸는 즉시
         * 해당 Model에 등록된 Line만 다시 표시합니다.
         */
        binding.spinnerModel.onItemSelectedListener =
            object :
                android.widget.AdapterView.OnItemSelectedListener {

                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>?,
                    view: android.view.View?,
                    position: Int,
                    id: Long
                ) {

                    val selectedModel =
                        binding.spinnerModel.selectedItem
                            ?.toString()
                            ?.trim()
                            .orEmpty()

                    if (
                        selectedModel.isBlank()
                    ) {

                        return
                    }

                    val saved =
                        ProductionContextStore.getCurrent(
                            this@MenuActivity
                        )

                    val preferredLine =
                        if (
                            saved.model ==
                            selectedModel
                        ) {

                            saved.line

                        } else {

                            ""
                        }

                    updateLineSpinnerForModel(
                        model = selectedModel,
                        preferredLine = preferredLine
                    )
                }

                override fun onNothingSelected(
                    parent: android.widget.AdapterView<*>?
                ) {
                    // 아무 작업도 하지 않습니다.
                }
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
     * Model별 Line Spinner 갱신
     * =========================================================
     */

    private fun updateLineSpinnerForModel(
        model: String,
        preferredLine: String = ""
    ) {

        val lines =
            ProductionContextStore.getLinesForModel(
                model
            )

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

        val preferredPosition =
            lines.indexOf(
                preferredLine
            )

        if (
            preferredPosition >= 0
        ) {

            binding.spinnerLine.setSelection(
                preferredPosition
            )

        } else if (
            lines.isNotEmpty()
        ) {

            binding.spinnerLine.setSelection(
                0
            )
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
         * Spec Summary
         * =====================================================
         */
        binding.btnInspectionSpecSummary
            .setOnClickListener {

                startActivity(
                    Intent(
                        this,
                        InspectionSpecSummaryActivity::class.java
                    )
                )
            }

        /*
         * =====================================================
         * 검사 기준 설정
         * =====================================================
         */
        binding.btnInspectionSpecSettings
            .setOnClickListener {

                startActivity(
                    Intent(
                        this,
                        InspectionSpecSettingsActivity::class.java
                    )
                )
            }

        /*
         * =====================================================
         * Telegram 알림 설정
         * =====================================================
         */
        binding.btnTelegramSettings
            .setOnClickListener {

                startActivity(
                    Intent(
                        this,
                        TelegramSettingsActivity::class.java
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
