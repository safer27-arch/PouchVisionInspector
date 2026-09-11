package com.pouchvision.inspector

import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

/*
 * =============================================================
 * Model / Line별 검사 기준 설정 화면
 * =============================================================
 *
 * 특징
 * - 별도 XML 없이 Kotlin 코드만으로 화면 구성
 * - Model 선택 → 해당 Model의 Line만 표시
 * - 검사 항목별 기준값 저장
 * - 현재 Model/Line 기본값 불러오기
 * - 검사 항목별 기본값 복원
 *
 * 중요
 * - 이 Activity를 추가하는 것만으로는 메뉴에 아직 나타나지 않습니다.
 * - 다음 단계에서 AndroidManifest + 메뉴 버튼을 연결합니다.
 * =============================================================
 */

class InspectionSpecSettingsActivity :
    AppCompatActivity() {

    private lateinit var spinnerModel:
        Spinner

    private lateinit var spinnerLine:
        Spinner

    private lateinit var spinnerInspection:
        Spinner

    private lateinit var editNormal:
        EditText

    private lateinit var editWarning:
        EditText

    private lateinit var editLimit:
        EditText

    private lateinit var tvCurrentSelection:
        TextView

    private lateinit var tvDirectionGuide:
        TextView

    private lateinit var tvCriteriaPreview:
        TextView

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

        loadSelectedSpec()
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
            "검사 기준 설정"

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
            "Model / Line별 Inspection Spec"

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
         * 현재 생산 조건 안내
         */
        tvCurrentSelection =
            TextView(
                this
            )

        tvCurrentSelection.textSize =
            15f

        tvCurrentSelection.setTextColor(
            Color.parseColor(
                "#0B7285"
            )
        )

        tvCurrentSelection.setPadding(
            dp(14),
            dp(12),
            dp(14),
            dp(12)
        )

        tvCurrentSelection.setBackgroundColor(
            Color.parseColor(
                "#E3F8FF"
            )
        )

        root.addView(
            tvCurrentSelection,
            matchWrap()
        )

        addGap(
            root,
            14
        )

        /*
         * 기준 설정 Card
         */
        val card =
            LinearLayout(
                this
            )

        card.orientation =
            LinearLayout.VERTICAL

        card.setPadding(
            dp(16),
            dp(16),
            dp(16),
            dp(16)
        )

        card.setBackgroundColor(
            Color.WHITE
        )

        val cardTitle =
            TextView(
                this
            )

        cardTitle.text =
            "Model / Line / 검사 항목 선택"

        cardTitle.textSize =
            20f

        cardTitle.setTextColor(
            Color.parseColor(
                "#102A43"
            )
        )

        cardTitle.setTypeface(
            null,
            android.graphics.Typeface.BOLD
        )

        card.addView(
            cardTitle
        )

        addGap(
            card,
            14
        )

        addFieldLabel(
            card,
            "Model"
        )

        spinnerModel =
            Spinner(
                this
            )

        card.addView(
            spinnerModel,
            matchHeight(
                56
            )
        )

        addGap(
            card,
            12
        )

        addFieldLabel(
            card,
            "Line"
        )

        spinnerLine =
            Spinner(
                this
            )

        card.addView(
            spinnerLine,
            matchHeight(
                56
            )
        )

        addGap(
            card,
            12
        )

        addFieldLabel(
            card,
            "검사 항목"
        )

        spinnerInspection =
            Spinner(
                this
            )

        card.addView(
            spinnerInspection,
            matchHeight(
                56
            )
        )

        addGap(
            card,
            16
        )

        tvDirectionGuide =
            TextView(
                this
            )

        tvDirectionGuide.textSize =
            14f

        tvDirectionGuide.setTextColor(
            Color.parseColor(
                "#486581"
            )
        )

        tvDirectionGuide.setPadding(
            dp(12),
            dp(10),
            dp(12),
            dp(10)
        )

        tvDirectionGuide.setBackgroundColor(
            Color.parseColor(
                "#F0F4F8"
            )
        )

        card.addView(
            tvDirectionGuide
        )

        addGap(
            card,
            16
        )

        /*
         * 기준 1
         */
        addFieldLabel(
            card,
            "정상 기준"
        )

        editNormal =
            createNumberEditText()

        card.addView(
            editNormal,
            matchHeight(
                58
            )
        )

        addGap(
            card,
            12
        )

        /*
         * 기준 2
         */
        addFieldLabel(
            card,
            "주의 기준"
        )

        editWarning =
            createNumberEditText()

        card.addView(
            editWarning,
            matchHeight(
                58
            )
        )

        addGap(
            card,
            12
        )

        /*
         * 기준 3
         */
        addFieldLabel(
            card,
            "한계정상 / 불량 경계 기준"
        )

        editLimit =
            createNumberEditText()

        card.addView(
            editLimit,
            matchHeight(
                58
            )
        )

        addGap(
            card,
            16
        )

        tvCriteriaPreview =
            TextView(
                this
            )

        tvCriteriaPreview.textSize =
            14f

        tvCriteriaPreview.setTextColor(
            Color.parseColor(
                "#334E68"
            )
        )

        tvCriteriaPreview.setPadding(
            dp(12),
            dp(12),
            dp(12),
            dp(12)
        )

        tvCriteriaPreview.setBackgroundColor(
            Color.parseColor(
                "#F8FAFC"
            )
        )

        card.addView(
            tvCriteriaPreview
        )

        root.addView(
            card,
            matchWrap()
        )

        addGap(
            root,
            14
        )

        /*
         * 저장 버튼
         */
        val saveButton =
            Button(
                this
            )

        saveButton.text =
            "현재 기준 저장"

        saveButton.textSize =
            16f

        saveButton.setTextColor(
            Color.WHITE
        )

        saveButton.setBackgroundColor(
            Color.parseColor(
                "#12344D"
            )
        )

        saveButton.setOnClickListener {

            saveCurrentSpec()
        }

        root.addView(
            saveButton,
            matchHeight(
                58
            )
        )

        addGap(
            root,
            10
        )

        /*
         * 기본값 복원
         */
        val resetButton =
            Button(
                this
            )

        resetButton.text =
            "이 검사 항목 기본값 복원"

        resetButton.textSize =
            15f

        resetButton.setOnClickListener {

            confirmReset()
        }

        root.addView(
            resetButton,
            matchHeight(
                56
            )
        )

        addGap(
            root,
            10
        )

        /*
         * 메뉴로 돌아가기
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
            18
        )

        /*
         * 운영상 주의
         */
        val note =
            TextView(
                this
            )

        note.text =
            """
※ 현장 운영 주의
• 기준값은 Model / Line별로 각각 저장됩니다.
• 기준값 변경은 이후 검사 판정에 직접 영향을 줍니다.
• 실제 양산 적용 전에는 승인된 Master Sample / Spec과 비교 검증이 필요합니다.
• 현재 기본값은 초기 프로토타입 판정 기준입니다.
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

        val inspections =
            InspectionSpecStore.InspectionType
                .values()
                .map {
                    it.displayName
                }

        spinnerInspection.adapter =
            ArrayAdapter(
                this,
                android.R.layout.simple_spinner_item,
                inspections
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

                    loadSelectedSpec()
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

                    loadSelectedSpec()
                }

                override fun onNothingSelected(
                    parent: AdapterView<*>?
                ) {
                    // no-op
                }
            }

        spinnerInspection.onItemSelectedListener =
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

                    loadSelectedSpec()
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
     * 현재 생산 Model / Line 선택값으로 초기화
     * =========================================================
     */

    private fun restoreCurrentProductionSelection() {

        val current =
            ProductionContextStore.getCurrent(
                this
            )

        tvCurrentSelection.text =
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

        spinnerInspection.setSelection(
            0
        )

        suppressSelectionEvent =
            false
    }

    /*
     * =========================================================
     * Model 선택에 따라 Line 목록 갱신
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
     * 선택한 Spec 불러오기
     * =========================================================
     */

    private fun loadSelectedSpec() {

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

        val type =
            selectedInspectionType()

        val spec =
            InspectionSpecStore.get(
                context = this,
                model = model,
                line = line,
                inspectionType = type
            )

        editNormal.setText(
            formatValue(
                spec.normalBoundary
            )
        )

        editWarning.setText(
            formatValue(
                spec.warningBoundary
            )
        )

        editLimit.setText(
            formatValue(
                spec.limitBoundary
            )
        )

        updateDirectionGuide(
            spec
        )

        updateCriteriaPreview(
            spec
        )
    }

    /*
     * =========================================================
     * 기준 저장
     * =========================================================
     */

    private fun saveCurrentSpec() {

        val model =
            selectedModel()

        val line =
            selectedLine()

        val type =
            selectedInspectionType()

        val normal =
            parseNumber(
                editNormal
            )

        val warning =
            parseNumber(
                editWarning
            )

        val limit =
            parseNumber(
                editLimit
            )

        if (
            normal == null ||
            warning == null ||
            limit == null
        ) {

            Toast.makeText(
                this,
                "세 기준값을 모두 숫자로 입력해주세요.",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        val default =
            InspectionSpecStore.defaultSpec(
                type
            )

        val spec =
            InspectionSpecStore.InspectionSpec(
                inspectionType =
                    type,

                scoreDirection =
                    default.scoreDirection,

                normalBoundary =
                    normal,

                warningBoundary =
                    warning,

                limitBoundary =
                    limit
            )

        val success =
            InspectionSpecStore.save(
                context = this,
                model = model,
                line = line,
                spec = spec
            )

        if (
            success
        ) {

            Toast.makeText(
                this,
                "$model / $line / ${type.displayName}\n기준값이 저장되었습니다.",
                Toast.LENGTH_LONG
            ).show()

            updateDirectionGuide(
                spec
            )

            updateCriteriaPreview(
                spec
            )

        } else {

            Toast.makeText(
                this,
                invalidRuleMessage(
                    default.scoreDirection
                ),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /*
     * =========================================================
     * 기본값 복원
     * =========================================================
     */

    private fun confirmReset() {

        val model =
            selectedModel()

        val line =
            selectedLine()

        val type =
            selectedInspectionType()

        AlertDialog.Builder(
            this
        )
            .setTitle(
                "기본값 복원"
            )
            .setMessage(
                "$model / $line\n${type.displayName} 기준을 기본값으로 복원하시겠습니까?"
            )
            .setNegativeButton(
                "취소",
                null
            )
            .setPositiveButton(
                "복원"
            ) { _, _ ->

                InspectionSpecStore.reset(
                    context = this,
                    model = model,
                    line = line,
                    inspectionType = type
                )

                loadSelectedSpec()

                Toast.makeText(
                    this,
                    "기본 기준으로 복원되었습니다.",
                    Toast.LENGTH_LONG
                ).show()
            }
            .show()
    }

    /*
     * =========================================================
     * 판정 방향 / Preview
     * =========================================================
     */

    private fun updateDirectionGuide(
        spec: InspectionSpecStore.InspectionSpec
    ) {

        tvDirectionGuide.text =
            when (
                spec.scoreDirection
            ) {

                InspectionSpecStore.ScoreDirection.LOWER_IS_BETTER -> {

                    """
점수 방향 : 낮을수록 양호
BOTTOM CORNER Wrinkle Score 기준입니다.
입력 순서 : 정상 < 주의 < 한계/불량 경계
                    """.trimIndent()
                }

                InspectionSpecStore.ScoreDirection.HIGHER_IS_BETTER -> {

                    """
점수 방향 : 높을수록 양호
Quality Score 기준입니다.
입력 순서 : 정상 > 주의 > 한계/불량 경계
                    """.trimIndent()
                }
            }
    }

    private fun updateCriteriaPreview(
        spec: InspectionSpecStore.InspectionSpec
    ) {

        tvCriteriaPreview.text =
            "현재 판정 기준\n\n" +
                spec.criteriaText()
    }

    /*
     * =========================================================
     * Selection Helpers
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

    private fun selectedInspectionType():
        InspectionSpecStore.InspectionType {

        val position =
            spinnerInspection
                .selectedItemPosition

        return InspectionSpecStore
            .InspectionType
            .values()
            .getOrElse(
                position
            ) {

                InspectionSpecStore
                    .InspectionType
                    .BOTTOM_CORNER
            }
    }

    /*
     * =========================================================
     * Number / UI Helpers
     * =========================================================
     */

    private fun createNumberEditText():
        EditText {

        return EditText(
            this
        ).apply {

            inputType =
                InputType.TYPE_CLASS_NUMBER or
                    InputType.TYPE_NUMBER_FLAG_DECIMAL

            textSize =
                17f

            setPadding(
                dp(14),
                0,
                dp(14),
                0
            )

            setBackgroundColor(
                Color.parseColor(
                    "#F0F4F8"
                )
            )
        }
    }

    private fun parseNumber(
        editText: EditText
    ): Double? {

        return editText
            .text
            ?.toString()
            ?.trim()
            ?.replace(
                ",",
                "."
            )
            ?.toDoubleOrNull()
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

    private fun invalidRuleMessage(
        direction: InspectionSpecStore.ScoreDirection
    ): String {

        return when (
            direction
        ) {

            InspectionSpecStore.ScoreDirection.LOWER_IS_BETTER -> {

                "기준값 순서를 확인해주세요.\n" +
                    "BOTTOM CORNER는 정상 < 주의 < 불량 경계 순서여야 하며 0~100 범위입니다."
            }

            InspectionSpecStore.ScoreDirection.HIGHER_IS_BETTER -> {

                "기준값 순서를 확인해주세요.\n" +
                    "Quality Score는 정상 > 주의 > 불량 경계 순서여야 하며 0~100 범위입니다."
            }
        }
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
