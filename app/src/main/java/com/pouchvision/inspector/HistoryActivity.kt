package com.pouchvision.inspector

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.pouchvision.inspector.databinding.ActivityHistoryBinding
import java.util.Locale

class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding

    private val inspectionTypes =
        listOf(
            "전체 검사",
            "BOTTOM CORNER",
            "SEAL",
            "FORMING",
            "TAB"
        )

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(
            savedInstanceState
        )

        binding =
            ActivityHistoryBinding.inflate(
                layoutInflater
            )

        setContentView(
            binding.root
        )

        setupSpinner()

        binding.btnHistoryRefresh
            .setOnClickListener {

                loadHistory()
            }

        binding.btnHistoryBack
            .setOnClickListener {

                finish()
            }

        loadHistory()
    }


    /*
     * =========================================================
     * 필터 Spinner
     * =========================================================
     */

    private fun setupSpinner() {

        val adapter =
            ArrayAdapter(
                this,
                android.R.layout.simple_spinner_item,
                inspectionTypes
            )

        adapter.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item
        )

        binding.spinnerInspectionType.adapter =
            adapter

        binding.spinnerInspectionType.setSelection(
            0
        )

        binding.spinnerInspectionType
            .onItemSelectedListener =

            object :
                AdapterView.OnItemSelectedListener {

                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {

                    loadHistory()
                }

                override fun onNothingSelected(
                    parent: AdapterView<*>?
                ) {
                }
            }
    }


    /*
     * =========================================================
     * 저장 이력 불러오기
     * =========================================================
     */

    private fun loadHistory() {

        val allRecords =
            InspectionHistoryStore.load(
                this
            )

        val selectedType =
            binding.spinnerInspectionType
                .selectedItem
                ?.toString()
                ?: "전체 검사"


        val filteredRecords =
            if (
                selectedType ==
                "전체 검사"
            ) {

                allRecords

            } else {

                allRecords.filter {

                    it.inspectionType ==
                        selectedType
                }
            }


        updateSummary(
            selectedType,
            filteredRecords
        )


        showHistoryItems(
            filteredRecords
        )
    }


    /*
     * =========================================================
     * 요약
     * =========================================================
     */

    private fun updateSummary(
        selectedType: String,
        records:
        List<
            InspectionHistoryStore
                .InspectionRecord
            >
    ) {

        var normalCount =
            0

        var warningCount =
            0

        var limitCount =
            0

        var ngCount =
            0


        for (
            record in records
        ) {

            when {

                record.judgment
                    .contains(
                        "한계"
                    ) -> {

                    limitCount++
                }


                record.judgment
                    .contains(
                        "불량"
                    ) -> {

                    ngCount++
                }


                record.judgment
                    .contains(
                        "주의"
                    ) -> {

                    warningCount++
                }


                record.judgment
                    .contains(
                        "정상"
                    ) -> {

                    normalCount++
                }
            }
        }


        val averageScore =
            if (
                records.isNotEmpty()
            ) {

                records
                    .map {
                        it.score
                    }
                    .average()

            } else {

                0.0
            }


        binding.tvHistorySummary.text =
            String.format(
                Locale.getDefault(),

                """
현재 필터 : %s

저장된 검사 결과 : %d건

정상 : %d건
주의 : %d건
한계정상 : %d건
불량 : %d건

평균 Quality Score : %.1f / 100
                """.trimIndent(),

                selectedType,
                records.size,
                normalCount,
                warningCount,
                limitCount,
                ngCount,
                averageScore
            )
    }


    /*
     * =========================================================
     * 개별 검사 이력 표시
     * =========================================================
     */

    private fun showHistoryItems(
        records:
        List<
            InspectionHistoryStore
                .InspectionRecord
            >
    ) {

        binding.historyContainer
            .removeAllViews()


        if (
            records.isEmpty()
        ) {

            val emptyText =
                TextView(
                    this
                )

            emptyText.text =
                """
아직 저장된 검사 결과가 없습니다.

검사 화면에서 ROI 검사를 실행한 뒤
'검사 결과 저장' 버튼을 눌러주세요.
                """.trimIndent()


            emptyText.textSize =
                14f

            emptyText.setTextColor(
                Color.parseColor(
                    "#829AB1"
                )
            )

            emptyText.gravity =
                Gravity.CENTER

            emptyText.setPadding(
                dp(
                    16
                ),
                dp(
                    28
                ),
                dp(
                    16
                ),
                dp(
                    28
                )
            )


            binding.historyContainer
                .addView(
                    emptyText
                )

            return
        }


        for (
            record in records
        ) {

            addHistoryCard(
                record
            )
        }
    }


    /*
     * =========================================================
     * 검사 카드
     * =========================================================
     */

    private fun addHistoryCard(
        record:
        InspectionHistoryStore
            .InspectionRecord
    ) {

        val card =
            LinearLayout(
                this
            )

        card.orientation =
            LinearLayout.VERTICAL

        card.setPadding(
            dp(
                14
            ),
            dp(
                14
            ),
            dp(
                14
            ),
            dp(
                14
            )
        )


        val cardParams =
            LinearLayout
                .LayoutParams(
                    LinearLayout
                        .LayoutParams
                        .MATCH_PARENT,

                    LinearLayout
                        .LayoutParams
                        .WRAP_CONTENT
                )

        cardParams.bottomMargin =
            dp(
                10
            )


        card.layoutParams =
            cardParams


        card.setBackgroundColor(
            Color.parseColor(
                "#F8FAFC"
            )
        )


        /*
         * 검사 종류
         */

        val typeText =
            TextView(
                this
            )

        typeText.text =
            record.inspectionType

        typeText.textSize =
            17f

        typeText.setTextColor(
            Color.parseColor(
                "#102A43"
            )
        )

        typeText.setTypeface(
            null,
            android.graphics
                .Typeface.BOLD
        )


        /*
         * 판정
         */

        val judgmentText =
            TextView(
                this
            )

        judgmentText.text =
            "판정 : ${record.judgment}"

        judgmentText.textSize =
            16f

        judgmentText.setTypeface(
            null,
            android.graphics
                .Typeface.BOLD
        )

        judgmentText.setTextColor(
            judgmentColor(
                record.judgment
            )
        )


        /*
         * 주요 정보
         */

        val infoText =
            TextView(
                this
            )

        infoText.text =
            String.format(
                Locale.getDefault(),

                """
검사 일시 : %s
Quality Score : %.1f / 100
민감도 : %d%%
                """.trimIndent(),

                record.dateTime,
                record.score,
                record.sensitivity
            )

        infoText.textSize =
            14f

        infoText.setTextColor(
            Color.parseColor(
                "#486581"
            )
        )

        infoText.setLineSpacing(
            0f,
            1.15f
        )


        /*
         * 상세 결과
         */

        val detailText =
            TextView(
                this
            )

        detailText.text =
            record.details

        detailText.textSize =
            13f

        detailText.setTextColor(
            Color.parseColor(
                "#627D98"
            )
        )

        detailText.setPadding(
            0,
            dp(
                8
            ),
            0,
            0
        )


        /*
         * 카드에 추가
         */

        card.addView(
            typeText
        )

        card.addView(
            judgmentText
        )

        card.addView(
            infoText
        )

        card.addView(
            detailText
        )


        binding.historyContainer
            .addView(
                card
            )
    }


    /*
     * =========================================================
     * 판정 색상
     * =========================================================
     */

    private fun judgmentColor(
        judgment: String
    ): Int {

        return when {

            judgment.contains(
                "불량"
            ) -> {

                Color.parseColor(
                    "#C62828"
                )
            }


            judgment.contains(
                "한계"
            ) -> {

                Color.parseColor(
                    "#EF6C00"
                )
            }


            judgment.contains(
                "주의"
            ) -> {

                Color.parseColor(
                    "#F9A825"
                )
            }


            else -> {

                Color.parseColor(
                    "#2E7D32"
                )
            }
        }
    }


    /*
     * =========================================================
     * dp 변환
     * =========================================================
     */

    private fun dp(
        value: Int
    ): Int {

        return (
            value *
                resources
                    .displayMetrics
                    .density
            )
            .toInt()
    }
}
