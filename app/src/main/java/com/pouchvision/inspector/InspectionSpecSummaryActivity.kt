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
 * Model / Line별 검사 기준 요약 화면
 * =============================================================
 *
 * 목적
 * - 선택한 Model / Line의 5개 검사 기준을 한 화면에서 확인
 * - BOTTOM CORNER / SEAL / FORMING / TAB / DISASSEMBLY
 * - 기준값 수정은 기존 "검사 기준 설정" 화면에서 수행
 *
 * 이 파일은 XML 없이 Kotlin 코드로 화면을 구성합니다.
 * 다음 단계에서 Manifest와 Menu에 연결합니다.
 * =============================================================
 */

class InspectionSpecSummaryActivity :
    AppCompatActivity() {

    private lateinit var spinnerModel:
        Spinner

    private lateinit var spinnerLine:
        Spinner

    private lateinit var tvSelection:
        TextView

    private lateinit var specContainer:
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

        restoreCurrentSelection()

        refreshSummary()
    }

    override fun onResume() {

        super.onResume()

        if (
            ::specContainer.isInitialized
        ) {

            refreshSummary()
        }
    }

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
            "Model / Line별 5개 Inspection Spec Summary"

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
            dp(18)
        )

        root.addView(
            subtitle
        )

        tvSelection =
            TextView(
                this
            )

        tvSelection.textSize =
            15f

        tvSelection.setTextColor(
            Color.parseColor(
                "#0B7285"
            )
        )

        tvSelection.setPadding(
            dp(14),
            dp(12),
            dp(14),
            dp(12)
        )

        tvSelection.setBackgroundColor(
            Color.parseColor(
                "#E3F8FF"
            )
        )

        root.addView(
            tvSelection,
            matchWrap()
        )

        addGap(
            root,
            14
        )

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
                54
            )
        )

        addGap(
            selectorCard,
            10
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

        val sectionTitle =
            TextView(
                this
            )

        sectionTitle.text =
            "현재 적용 기준"

        sectionTitle.textSize =
            21f

        sectionTitle.setTextColor(
            Color.parseColor(
                "#102A43"
            )
        )

        sectionTitle.setTypeface(
            null,
            android.graphics.Typeface.BOLD
        )

        root.addView(
            sectionTitle
        )

        addGap(
            root,
            10
        )

        specContainer =
            LinearLayout(
                this
            )

        specContainer.orientation =
            LinearLayout.VERTICAL

        root.addView(
            specContainer,
            matchWrap()
        )

        addGap(
            root,
            14
        )

        val refreshButton =
            Button(
                this
            )

        refreshButton.text =
            "새로고침"

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

        val note =
            TextView(
                this
            )

        note.text =
            """
※ 이 화면은 현재 저장된 Model / Line별 판정 기준을 확인하는 요약 화면입니다.
※ 기준값 변경은 [검사 기준 설정] 화면에서 수행합니다.
※ 실제 양산 Spec 적용 전 승인된 Master Sample / 관리 기준과 비교 검증이 필요합니다.
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

    private fun restoreCurrentSelection() {

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

            val lineIndex =
                lines.indexOf(
                    preferredLine
                )

            if (
                lineIndex >= 0
            ) {
                spinnerLine.setSelection(
                    lineIndex
                )
            }
        }
    }

    private fun refreshSummary() {

        if (
            !::spinnerModel.isInitialized ||
            !::spinnerLine.isInitialized ||
            !::specContainer.isInitialized
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

        tvSelection.text =
            "조회 조건 : $model / $line"

        specContainer.removeAllViews()

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
            dp(5),
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
            14f

        criteria.setTextColor(
            Color.parseColor(
                "#334E68"
            )
        )

        criteria.setLineSpacing(
            0f,
            1.15f
        )

        card.addView(
            criteria
        )

        specContainer.addView(
            card
        )
    }

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

    private fun matchWrap(): LinearLayout.LayoutParams {
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
