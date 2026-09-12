package com.pouchvision.inspector

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/*
 * =============================================================
 * Model / Line별 검사 기준 Summary 화면
 * =============================================================
 *
 * 기능
 * - Model 선택
 * - 선택한 Model에 해당하는 Line만 표시
 * - BOTTOM CORNER / SEAL / FORMING / TAB / DISASSEMBLY
 *   5개 기준을 한 화면에서 동시에 확인
 * - 현재 저장된 Model / Line별 기준값을 그대로 표시
 *
 * 중요
 * - 이 Activity를 추가하는 단계에서는 기존 기능을 변경하지 않습니다.
 * - 다음 단계에서 Manifest 등록 + 메뉴 버튼 연결을 진행합니다.
 * =============================================================
 */

class InspectionSpecSummaryActivity :
    AppCompatActivity() {

    private lateinit var spinnerModel:
        Spinner

    private lateinit var spinnerLine:
        Spinner

    private lateinit var tvSelectedContext:
        TextView

    private lateinit var summaryContainer:
        LinearLayout

    private var suppressSelectionEvent =
        false

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(
            savedInstanceState
        )

        supportActionBar
            ?.hide()

        setContentView(
            createScreen()
        )

        setupModelSpinner()

        restoreCurrentProductionSelection()

        refreshSummary()
    }

    /*
     * =========================================================
     * 화면 구성
     * =========================================================
     */

    private fun createScreen(): View {

        val scroll =
            ScrollView(
                this
            )

        scroll.setBackgroundColor(
            Color.parseColor(
                "#F4F7FA"
            )
        )

        val root =
            LinearLayout(
                this
            )

        root.orientation =
            LinearLayout.VERTICAL

        root.setPadding(
            dp(20),
            dp(22),
            dp(20),
            dp(30)
        )

        /*
         * Header
         */
        val title =
            TextView(
                this
            )

        title.text =
            "Spec Summary"

        title.textSize =
            30f

        title.setTextColor(
            Color.parseColor(
                "#102A43"
            )
        )

        title.setTypeface(
            null,
            android.graphics.Typeface.BOLD
        )

        root.addView(
            title
        )

        val subtitle =
            TextView(
                this
            )

        subtitle.text =
            "Model / Line별 5개 검사 기준 요약"

        subtitle.textSize =
            17f

        subtitle.setTextColor(
            Color.parseColor(
                "#486581"
            )
        )

        subtitle.setPadding(
            0,
            dp(4),
            0,
            dp(18)
        )

        root.addView(
            subtitle
        )

        /*
         * 선택 영역
         */
        val selectorCard =
            LinearLayout(
                this
            )

        selectorCard.orientation =
            LinearLayout.VERTICAL

        selectorCard.setPadding(
            dp(16),
            dp(16),
            dp(16),
            dp(16)
        )

        selectorCard.setBackgroundColor(
            Color.WHITE
        )

        addFieldLabel(
            selectorCard,
            "Model"
        )

        spinnerModel =
            Spinner(
                this
            )

        selectorCard.addView(
            spinnerModel,
            matchHeight(
                56
            )
        )

        addGap(
            selectorCard,
            12
        )

        addFieldLabel(
            selectorCard,
            "Line"
        )

        spinnerLine =
            Spinner(
                this
            )

        selectorCard.addView(
            spinnerLine,
            matchHeight(
                56
            )
        )

        addGap(
            selectorCard,
            14
        )

        tvSelectedContext =
            TextView(
                this
            )

        tvSelectedContext.textSize =
            15f

        tvSelectedContext.setTextColor(
            Color.parseColor(
                "#0B7285"
            )
        )

        tvSelectedContext.setPadding(
            dp(12),
            dp(10),
            dp(12),
            dp(10)
        )

        tvSelectedContext.setBackgroundColor(
            Color.parseColor(
                "#E3F8FF"
            )
        )

        selectorCard.addView(
            tvSelectedContext
        )

        root.addView(
            selectorCard,
            matchWrap()
        )

        addGap(
            root,
            14
        )

        /*
         * Summary 영역
         */
        summaryContainer =
            LinearLayout(
                this
            )

        summaryContainer.orientation =
            LinearLayout.VERTICAL

        root.addView(
            summaryContainer,
            matchWrap()
        )

        addGap(
            root,
            12
        )

        /*
         * 새로고침
         */
        val refreshButton =
            Button(
                this
            )

        refreshButton.text =
            "기준값 새로고침"

        refreshButton.textSize =
            15f

        refreshButton.setOnClickListener {

            refreshSummary()
        }

        root.addView(
            refreshButton,
            matchHeight(
                56
            )
        )

        addGap(
            root,
            10
        )

        /*
         * 메뉴 복귀
         */
        val backButton =
            Button(
                this
            )

        backButton.text =
            "메뉴로 돌아가기"

        backButton.textSize =
            15f

        backButton.setOnClickListener {

            finish()
        }

        root.addView(
            backButton,
            matchHeight(
                56
            )
        )

        addGap(
            root,
            16
        )

        /*
         * 안내
         */
        val note =
            TextView(
                this
            )

        note.text =
            """
※ 이 화면은 현재 저장된 Model / Line별 검사 기준을 한눈에 확인하는 Summary 화면입니다.

※ 실제 검사 판정은 각 검사 Activity가 현재 선택된 Model / Line 기준값을 읽어 사용합니다.

※ 기준 변경은 [검사 기준 설정] 화면에서 진행합니다.
            """.trimIndent()

        note.textSize =
            13f

        note.setTextColor(
            Color.parseColor(
                "#627D98"
            )
        )

        note.setPadding(
            dp(12),
            dp(12),
            dp(12),
            dp(12)
        )

        note.setBackgroundColor(
            Color.parseColor(
                "#FFF8E1"
            )
        )

        root.addView(
            note,
            matchWrap()
        )

        scroll.addView(
            root
        )

        return scroll
    }

    /*
     * =========================================================
     * Model / Line Spinner
     * =========================================================
     */

    private fun setupModelSpinner() {

        val models =
            ProductionContextStore.getModels(
                this
            )

        spinnerModel.adapter =
            ArrayAdapter(
                this,
                android.R.layout.simple_spinner_item,
                models
            ).apply {

                setDropDownViewResource(
                    android.R.layout.simple_spinner_dropdown_item
                )
            }

        spinnerModel.onItemSelectedListener =
            object :
                AdapterView.OnItemSelectedListener {

                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {

                    if (
                        suppressSelectionEvent
                    ) {
                        return
                    }

                    updateLineSpinner(
                        preferredLine = null
                    )

                    refreshSummary()
                }

                override fun onNothingSelected(
                    parent: AdapterView<*>?
                ) {
                    // no-op
                }
            }

        spinnerLine.onItemSelectedListener =
            object :
                AdapterView.OnItemSelectedListener {

                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {

                    if (
                        suppressSelectionEvent
                    ) {
                        return
                    }

                    refreshSummary()
                }

                override fun onNothingSelected(
                    parent: AdapterView<*>?
                ) {
                    // no-op
                }
            }
    }

    private fun restoreCurrentProductionSelection() {

        val current =
            ProductionContextStore.getCurrent(
                this
            )

        val models =
            ProductionContextStore.getModels(
                this
            )

        val modelIndex =
            models.indexOf(
                current.model
            )
                .coerceAtLeast(
                    0
                )

        suppressSelectionEvent =
            true

        spinnerModel.setSelection(
            modelIndex
        )

        updateLineSpinner(
            preferredLine = current.line
        )

        suppressSelectionEvent =
            false
    }

    private fun updateLineSpinner(
        preferredLine: String?
    ) {

        val model =
            selectedModel()

        val lines =
            ProductionContextStore.getLinesForModel(
                model
            )

        spinnerLine.adapter =
            ArrayAdapter(
                this,
                android.R.layout.simple_spinner_item,
                lines
            ).apply {

                setDropDownViewResource(
                    android.R.layout.simple_spinner_dropdown_item
                )
            }

        if (
            preferredLine != null
        ) {

            val index =
                lines.indexOf(
                    preferredLine
                )

            if (
                index >=
                0
            ) {

                spinnerLine.setSelection(
                    index
                )
            }
        }
    }

    /*
     * =========================================================
     * Summary 새로고침
     * =========================================================
     */

    private fun refreshSummary() {

        val model =
            selectedModel()

        val line =
            selectedLine()

        if (
            model.isBlank() ||
            line.isBlank()
        ) {
            return
        }

        tvSelectedContext.text =
            "조회 기준 : $model / $line"

        summaryContainer
            .removeAllViews()

        InspectionSpecStore
            .InspectionType
            .values()
            .forEach { type ->

                val spec =
                    InspectionSpecStore.get(
                        context = this,
                        model = model,
                        line = line,
                        inspectionType = type
                    )

                summaryContainer.addView(
                    createSpecCard(
                        spec
                    ),
                    matchWrap()
                )

                addGap(
                    summaryContainer,
                    10
                )
            }
    }

    /*
     * =========================================================
     * 검사 항목별 Card
     * =========================================================
     */

    private fun createSpecCard(
        spec: InspectionSpecStore.InspectionSpec
    ): View {

        val card =
            LinearLayout(
                this
            )

        card.orientation =
            LinearLayout.VERTICAL

        card.setPadding(
            dp(16),
            dp(14),
            dp(16),
            dp(14)
        )

        card.setBackgroundColor(
            Color.WHITE
        )

        val title =
            TextView(
                this
            )

        title.text =
            spec.inspectionType.displayName

        title.textSize =
            18f

        title.setTextColor(
            Color.parseColor(
                "#102A43"
            )
        )

        title.setTypeface(
            null,
            android.graphics.Typeface.BOLD
        )

        card.addView(
            title
        )

        val direction =
            TextView(
                this
            )

        direction.text =
            when (
                spec.scoreDirection
            ) {

                InspectionSpecStore.ScoreDirection.LOWER_IS_BETTER ->
                    "Score 방향 : 낮을수록 양호"

                InspectionSpecStore.ScoreDirection.HIGHER_IS_BETTER ->
                    "Score 방향 : 높을수록 양호"
            }

        direction.textSize =
            13f

        direction.setTextColor(
            Color.parseColor(
                "#627D98"
            )
        )

        direction.setPadding(
            0,
            dp(4),
            0,
            dp(10)
        )

        card.addView(
            direction
        )

        val criteria =
            TextView(
                this
            )

        criteria.text =
            spec.criteriaText()

        criteria.textSize =
            14f

        criteria.setTextColor(
            Color.parseColor(
                "#334E68"
            )
        )

        criteria.setPadding(
            dp(12),
            dp(10),
            dp(12),
            dp(10)
        )

        criteria.setBackgroundColor(
            when (
                spec.inspectionType
            ) {

                InspectionSpecStore.InspectionType.BOTTOM_CORNER ->
                    Color.parseColor(
                        "#FFF8E1"
                    )

                else ->
                    Color.parseColor(
                        "#F0F4F8"
                    )
            }
        )

        card.addView(
            criteria
        )

        return card
    }

    /*
     * =========================================================
     * Helpers
     * =========================================================
     */

    private fun selectedModel(): String {

        return spinnerModel
            .selectedItem
            ?.toString()
            ?.trim()
            .orEmpty()
    }

    private fun selectedLine(): String {

        return spinnerLine
            .selectedItem
            ?.toString()
            ?.trim()
            .orEmpty()
    }

    private fun addFieldLabel(
        parent: LinearLayout,
        text: String
    ) {

        val label =
            TextView(
                this
            )

        label.text =
            text

        label.textSize =
            15f

        label.setTextColor(
            Color.parseColor(
                "#334E68"
            )
        )

        label.setTypeface(
            null,
            android.graphics.Typeface.BOLD
        )

        label.setPadding(
            0,
            0,
            0,
            dp(6)
        )

        parent.addView(
            label
        )
    }

    private fun addGap(
        parent: LinearLayout,
        heightDp: Int
    ) {

        val gap =
            View(
                this
            )

        parent.addView(
            gap,
            LinearLayout.LayoutParams(
                1,
                dp(
                    heightDp
                )
            )
        )
    }

    private fun matchWrap():
        LinearLayout.LayoutParams {

        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }

    private fun matchHeight(
        heightDp: Int
    ): LinearLayout.LayoutParams {

        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(
                heightDp
            )
        )
    }

    private fun dp(
        value: Int
    ): Int {

        return (
            value *
                resources.displayMetrics.density
            )
            .toInt()
    }
}
