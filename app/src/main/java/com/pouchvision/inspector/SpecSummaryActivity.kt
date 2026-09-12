package com.pouchvision.inspector

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

/*
 * =============================================================
 * Model / Line별 검사 기준 요약 화면
 * =============================================================
 *
 * 기능
 * - Model 선택
 * - 선택 Model에 맞는 Line 선택
 * - 5개 검사 항목의 현재 기준값을 한 화면에 요약
 *
 * 검사 항목
 * 1) BOTTOM CORNER
 * 2) SEAL
 * 3) FORMING
 * 4) TAB
 * 5) DISASSEMBLY
 *
 * 중요
 * - 이 파일을 추가하는 것만으로 기존 검사 로직은 바뀌지 않습니다.
 * - 다음 단계에서 Manifest와 메뉴 버튼을 연결합니다.
 * =============================================================
 */

class SpecSummaryActivity :
    AppCompatActivity() {

    private lateinit var spinnerModel:
        Spinner

    private lateinit var spinnerLine:
        Spinner

    private lateinit var tvCurrentProduction:
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

        setupAdapters()

        restoreCurrentProductionSelection()

        refreshSummary()
    }

    /*
     * =========================================================
     * 화면 구성
     * =========================================================
     */

    private fun createScreen(): View {

        val scrollView =
            ScrollView(
                this
            )

        scrollView.setBackgroundColor(
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
            "검사 기준 요약"

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
            "Model / Line별 5개 검사 Spec Summary"

        subtitle.textSize =
            16f

        subtitle.setTextColor(
            Color.parseColor(
                "#486581"
            )
        )

        subtitle.setPadding(
            0,
            dp(4),
            0,
            dp(16)
        )

        root.addView(
            subtitle
        )

        tvCurrentProduction =
            TextView(
                this
            )

        tvCurrentProduction.textSize =
            14f

        tvCurrentProduction.setTextColor(
            Color.parseColor(
                "#0B7285"
            )
        )

        tvCurrentProduction.setPadding(
            dp(14),
            dp(12),
            dp(14),
            dp(12)
        )

        tvCurrentProduction.setBackgroundColor(
            Color.parseColor(
                "#E3F8FF"
            )
        )

        root.addView(
            tvCurrentProduction,
            matchWrap()
        )

        addGap(
            root,
            14
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

        val selectorTitle =
            TextView(
                this
            )

        selectorTitle.text =
            "조회 조건"

        selectorTitle.textSize =
            19f

        selectorTitle.setTextColor(
            Color.parseColor(
                "#102A43"
            )
        )

        selectorTitle.setTypeface(
            null,
            android.graphics.Typeface.BOLD
        )

        selectorCard.addView(
            selectorTitle
        )

        addGap(
            selectorCard,
            12
        )

        addLabel(
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
            10
        )

        addLabel(
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
            12
        )

        val refreshButton =
            Button(
                this
            )

        refreshButton.text =
            "기준 요약 새로고침"

        refreshButton.textSize =
            15f

        refreshButton.setTextColor(
            Color.WHITE
        )

        refreshButton.setBackgroundColor(
            Color.parseColor(
                "#12344D"
            )
        )

        refreshButton.setOnClickListener {

            refreshSummary()
        }

        selectorCard.addView(
            refreshButton,
            matchHeight(
                54
            )
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
         * 운영 안내
         */
        val note =
            TextView(
                this
            )

        note.text =
            """
※ 이 화면은 현재 저장된 Model / Line별 검사 기준을 요약해서 보여줍니다.

※ 실제 판정 기준 변경은 [검사 기준 설정] 화면에서 수행합니다.

※ 양산 적용 전에는 승인된 Master Sample / Spec과 반드시 비교 검증해야 합니다.
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

        addGap(
            root,
            12
        )

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

        scrollView.addView(
            root
        )

        return scrollView
    }

    /*
     * =========================================================
     * Spinner 설정
     * =========================================================
     */

    private fun setupAdapters() {

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

    /*
     * =========================================================
     * 현재 생산조건으로 초기화
     * =========================================================
     */

    private fun restoreCurrentProductionSelection() {

        val current =
            ProductionContextStore.getCurrent(
                this
            )

        tvCurrentProduction.text =
            "현재 검사 생산 조건 : ${current.model} / ${current.line}"

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

    /*
     * =========================================================
     * Model 선택에 따른 Line 갱신
     * =========================================================
     */

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
     * 5개 검사 기준 요약
     * =========================================================
     */

    private fun refreshSummary() {

        if (
            !::summaryContainer.isInitialized
        ) {
            return
        }

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

        summaryContainer.removeAllViews()

        val header =
            TextView(
                this
            )

        header.text =
            "$model / $line"

        header.textSize =
            22f

        header.setTextColor(
            Color.parseColor(
                "#102A43"
            )
        )

        header.setTypeface(
            null,
            android.graphics.Typeface.BOLD
        )

        header.setPadding(
            0,
            0,
            0,
            dp(10)
        )

        summaryContainer.addView(
            header
        )

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

                addSpecCard(
                    type = type,
                    spec = spec
                )
            }
    }

    /*
     * =========================================================
     * 검사 항목 Card
     * =========================================================
     */

    private fun addSpecCard(
        type: InspectionSpecStore.InspectionType,
        spec: InspectionSpecStore.InspectionSpec
    ) {

        val card =
            LinearLayout(
                this
            )

        card.orientation =
            LinearLayout.VERTICAL

        card.setPadding(
            dp(14),
            dp(14),
            dp(14),
            dp(14)
        )

        card.setBackgroundColor(
            Color.WHITE
        )

        val params =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )

        params.bottomMargin =
            dp(10)

        card.layoutParams =
            params

        val title =
            TextView(
                this
            )

        title.text =
            type.displayName

        title.textSize =
            18f

        title.setTextColor(
            typeColor(
                type
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
                    "점수 방향 : 낮을수록 양호"

                InspectionSpecStore.ScoreDirection.HIGHER_IS_BETTER ->
                    "점수 방향 : 높을수록 양호"
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
            dp(8)
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
            15f

        criteria.setTextColor(
            Color.parseColor(
                "#334E68"
            )
        )

        criteria.setPadding(
            dp(10),
            dp(10),
            dp(10),
            dp(10)
        )

        criteria.setBackgroundColor(
            Color.parseColor(
                "#F8FAFC"
            )
        )

        card.addView(
            criteria
        )

        /*
         * 숫자 요약
         */
        val numeric =
            TextView(
                this
            )

        numeric.text =
            "기준값 : " +
                formatValue(
                    spec.normalBoundary
                ) +
                " / " +
                formatValue(
                    spec.warningBoundary
                ) +
                " / " +
                formatValue(
                    spec.limitBoundary
                )

        numeric.textSize =
            13f

        numeric.setTextColor(
            Color.parseColor(
                "#486581"
            )
        )

        numeric.setPadding(
            0,
            dp(8),
            0,
            0
        )

        card.addView(
            numeric
        )

        summaryContainer.addView(
            card
        )
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

    private fun typeColor(
        type: InspectionSpecStore.InspectionType
    ): Int {

        return when (
            type
        ) {

            InspectionSpecStore.InspectionType.BOTTOM_CORNER ->
                Color.parseColor(
                    "#7B1FA2"
                )

            InspectionSpecStore.InspectionType.SEAL ->
                Color.parseColor(
                    "#1565C0"
                )

            InspectionSpecStore.InspectionType.FORMING ->
                Color.parseColor(
                    "#2E7D32"
                )

            InspectionSpecStore.InspectionType.TAB ->
                Color.parseColor(
                    "#EF6C00"
                )

            InspectionSpecStore.InspectionType.DISASSEMBLY ->
                Color.parseColor(
                    "#C62828"
                )
        }
    }

    private fun formatValue(
        value: Double
    ): String {

        return if (
            value %
            1.0 ==
            0.0
        ) {

            value
                .toInt()
                .toString()

        } else {

            String.format(
                Locale.getDefault(),
                "%.1f",
                value
            )
        }
    }

    private fun addLabel(
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

        parent.addView(
            View(
                this
            ),
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
