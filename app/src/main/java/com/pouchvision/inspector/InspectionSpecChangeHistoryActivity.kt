package com.pouchvision.inspector

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Model / Line별 검사 기준값 변경 이력 화면.
 *
 * 표시 내용
 * - 변경 일시
 * - Model / Line
 * - 검사 항목
 * - 저장 / 기본값 복원 구분
 * - 변경 전 기준값
 * - 변경 후 기준값
 * - Model / Line / 검사 항목 / 변경 유형 필터
 *
 * 중요
 * - 이 화면은 기준값을 수정하지 않습니다.
 * - InspectionSpecChangeHistoryStore에 저장된 이력만 읽어서 표시합니다.
 */
class InspectionSpecChangeHistoryActivity : AppCompatActivity() {

    private lateinit var historyContainer: LinearLayout
    private lateinit var summaryText: TextView
    private lateinit var spinnerModel: Spinner
    private lateinit var spinnerLine: Spinner
    private lateinit var spinnerInspection: Spinner
    private lateinit var spinnerAction: Spinner

    private var selectedModel: String = ALL_MODELS
    private var selectedLine: String = ALL_LINES
    private var selectedInspectionType: InspectionSpecStore.InspectionType? = null
    private var selectedAction: InspectionSpecChangeHistoryStore.ChangeAction? = null

    companion object {
        private const val ALL_MODELS = "전체 Model"
        private const val ALL_LINES = "전체 Line"
        private const val ALL_INSPECTIONS = "전체 검사 항목"
        private const val ALL_ACTIONS = "전체 변경 유형"
        private const val ACTION_SAVED = "기준 저장"
        private const val ACTION_RESET = "기본값 복원"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(createContentView())
        refreshHistory()
    }

    override fun onResume() {
        super.onResume()

        if (::historyContainer.isInitialized) {
            refreshHistory()
        }
    }

    private fun createContentView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F4F6F8"))
        }

        root.addView(createHeader())

        val scrollView = ScrollView(this).apply {
            isFillViewport = true
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(24))
        }

        val guideText = TextView(this).apply {
            text = "Model / Line별 검사 기준값 변경 기록입니다.\n저장 또는 기본값 복원 시 자동으로 기록됩니다."
            textSize = 14f
            setTextColor(Color.parseColor("#486581"))
            setLineSpacing(0f, 1.18f)
        }

        content.addView(
            guideText,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(14)
            }
        )

        content.addView(
            createFilterCard(),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(14)
            }
        )

        summaryText = TextView(this).apply {
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#102A43"))
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = roundedBackground(
                fillColor = "#EAF2F8",
                strokeColor = "#BCCCDC",
                radiusDp = 10
            )
        }

        content.addView(
            summaryText,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(14)
            }
        )

        historyContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        content.addView(
            historyContainer,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        content.addView(
            createActionButton(
                text = "이력 새로고침",
                backgroundColor = "#335C81"
            ) {
                refreshHistory()
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(50)
            ).apply {
                topMargin = dp(8)
            }
        )

        content.addView(
            createActionButton(
                text = "메뉴로 돌아가기",
                backgroundColor = "#102F4A"
            ) {
                finish()
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(50)
            ).apply {
                topMargin = dp(10)
            }
        )

        scrollView.addView(
            content,
            android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
            )
        )

        root.addView(
            scrollView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        return root
    }

    private fun createHeader(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(14))
            setBackgroundColor(Color.parseColor("#102F4A"))

            addView(
                TextView(this@InspectionSpecChangeHistoryActivity).apply {
                    text = "SPEC CHANGE HISTORY"
                    textSize = 20f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(Color.WHITE)
                }
            )

            addView(
                TextView(this@InspectionSpecChangeHistoryActivity).apply {
                    text = "검사 기준값 변경 이력"
                    textSize = 14f
                    setTextColor(Color.parseColor("#D9EAF7"))
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(2)
                }
            )
        }
    }

    private fun refreshHistory() {
        val allRecords =
            try {
                InspectionSpecChangeHistoryStore.getAll(this)
            } catch (_: Exception) {
                emptyList()
            }

        val records =
            allRecords.filter { record ->
                val modelMatches =
                    selectedModel == ALL_MODELS ||
                        record.model == selectedModel

                val lineMatches =
                    selectedLine == ALL_LINES ||
                        record.line == selectedLine

                val inspectionMatches =
                    selectedInspectionType == null ||
                        record.inspectionType == selectedInspectionType

                val actionMatches =
                    selectedAction == null ||
                        record.action == selectedAction

                modelMatches &&
                    lineMatches &&
                    inspectionMatches &&
                    actionMatches
            }

        historyContainer.removeAllViews()

        summaryText.text =
            if (allRecords.isEmpty()) {
                "저장된 기준 변경 이력이 없습니다."
            } else if (records.isEmpty()) {
                "선택한 조건의 변경 이력이 없습니다.  ·  전체 ${allRecords.size}건"
            } else {
                "표시 ${records.size}건  ·  전체 ${allRecords.size}건"
            }

        if (records.isEmpty()) {
            historyContainer.addView(
                createEmptyCard(
                    hasAnyHistory = allRecords.isNotEmpty()
                )
            )
            return
        }

        records.forEachIndexed { index, record ->
            historyContainer.addView(
                createHistoryCard(
                    index = index,
                    record = record
                ),
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dp(12)
                }
            )
        }
    }

    private fun createFilterCard(): View {
        val card =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(
                    dp(14),
                    dp(14),
                    dp(14),
                    dp(14)
                )
                background =
                    roundedBackground(
                        fillColor = "#FFFFFF",
                        strokeColor = "#D9E2EC",
                        radiusDp = 12
                    )
            }

        card.addView(
            TextView(this).apply {
                text = "이력 필터"
                textSize = 15f
                setTypeface(
                    typeface,
                    Typeface.BOLD
                )
                setTextColor(
                    Color.parseColor(
                        "#102A43"
                    )
                )
            }
        )

        card.addView(
            createFieldLabel(
                "Model"
            ),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(10)
            }
        )

        spinnerModel =
            Spinner(this).apply {
                minimumHeight =
                    dp(48)
            }

        card.addView(
            spinnerModel,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48)
            )
        )

        card.addView(
            createFieldLabel(
                "Line"
            ),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(10)
            }
        )

        spinnerLine =
            Spinner(this).apply {
                minimumHeight =
                    dp(48)
            }

        card.addView(
            spinnerLine,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48)
            )
        )

        card.addView(
            createFieldLabel(
                "검사 항목"
            ),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(10)
            }
        )

        spinnerInspection =
            Spinner(this).apply {
                minimumHeight =
                    dp(48)
            }

        card.addView(
            spinnerInspection,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48)
            )
        )

        card.addView(
            createFieldLabel(
                "변경 유형"
            ),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(10)
            }
        )

        spinnerAction =
            Spinner(this).apply {
                minimumHeight =
                    dp(48)
            }

        card.addView(
            spinnerAction,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48)
            )
        )

        val models =
            mutableListOf(
                ALL_MODELS
            ).apply {
                addAll(
                    ProductionContextStore.getModels(
                        this@InspectionSpecChangeHistoryActivity
                    )
                )
            }

        spinnerModel.adapter =
            createVisibleSpinnerAdapter(
                models
            )

        updateLineFilter(
            model = ALL_MODELS,
            refresh = false
        )

        val inspectionItems =
            mutableListOf(
                ALL_INSPECTIONS
            ).apply {
                addAll(
                    InspectionSpecStore.InspectionType.values()
                        .map {
                            it.displayName
                        }
                )
            }

        spinnerInspection.adapter =
            createVisibleSpinnerAdapter(
                inspectionItems
            )

        spinnerAction.adapter =
            createVisibleSpinnerAdapter(
                listOf(
                    ALL_ACTIONS,
                    ACTION_SAVED,
                    ACTION_RESET
                )
            )

        spinnerModel.onItemSelectedListener =
            object :
                AdapterView.OnItemSelectedListener {

                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    selectedModel =
                        parent?.getItemAtPosition(
                            position
                        )?.toString()
                            ?: ALL_MODELS

                    updateLineFilter(
                        model = selectedModel,
                        refresh = true
                    )
                }

                override fun onNothingSelected(
                    parent: AdapterView<*>?
                ) {
                    selectedModel =
                        ALL_MODELS

                    updateLineFilter(
                        model = ALL_MODELS,
                        refresh = true
                    )
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
                    selectedLine =
                        parent?.getItemAtPosition(
                            position
                        )?.toString()
                            ?: ALL_LINES

                    if (
                        ::historyContainer.isInitialized
                    ) {
                        refreshHistory()
                    }
                }

                override fun onNothingSelected(
                    parent: AdapterView<*>?
                ) {
                    selectedLine =
                        ALL_LINES

                    if (
                        ::historyContainer.isInitialized
                    ) {
                        refreshHistory()
                    }
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
                    val selectedLabel =
                        parent?.getItemAtPosition(
                            position
                        )?.toString()
                            ?: ALL_INSPECTIONS

                    selectedInspectionType =
                        if (
                            selectedLabel == ALL_INSPECTIONS
                        ) {
                            null
                        } else {
                            InspectionSpecStore.InspectionType.values()
                                .firstOrNull {
                                    it.displayName == selectedLabel
                                }
                        }

                    if (
                        ::historyContainer.isInitialized
                    ) {
                        refreshHistory()
                    }
                }

                override fun onNothingSelected(
                    parent: AdapterView<*>?
                ) {
                    selectedInspectionType =
                        null

                    if (
                        ::historyContainer.isInitialized
                    ) {
                        refreshHistory()
                    }
                }
            }

        spinnerAction.onItemSelectedListener =
            object :
                AdapterView.OnItemSelectedListener {

                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    val selectedLabel =
                        parent?.getItemAtPosition(
                            position
                        )?.toString()
                            ?: ALL_ACTIONS

                    selectedAction =
                        when (selectedLabel) {
                            ACTION_SAVED ->
                                InspectionSpecChangeHistoryStore.ChangeAction.SAVED

                            ACTION_RESET ->
                                InspectionSpecChangeHistoryStore.ChangeAction.RESET_TO_DEFAULT

                            else ->
                                null
                        }

                    if (
                        ::historyContainer.isInitialized
                    ) {
                        refreshHistory()
                    }
                }

                override fun onNothingSelected(
                    parent: AdapterView<*>?
                ) {
                    selectedAction =
                        null

                    if (
                        ::historyContainer.isInitialized
                    ) {
                        refreshHistory()
                    }
                }
            }

        return card
    }

    private fun updateLineFilter(
        model: String,
        refresh: Boolean
    ) {
        val lines =
            mutableListOf(
                ALL_LINES
            )

        if (
            model == ALL_MODELS
        ) {
            val allLines =
                ProductionContextStore.getModels(
                    this
                )
                    .flatMap {
                        ProductionContextStore.getLinesForModel(
                            it
                        )
                    }
                    .distinct()
                    .sortedBy {
                        it.filter(
                            Char::isDigit
                        )
                            .toIntOrNull()
                            ?: Int.MAX_VALUE
                    }

            lines.addAll(
                allLines
            )
        } else {
            lines.addAll(
                ProductionContextStore.getLinesForModel(
                    model
                )
            )
        }

        val previousLine =
            selectedLine

        spinnerLine.adapter =
            createVisibleSpinnerAdapter(
                lines
            )

        val targetIndex =
            lines.indexOf(
                previousLine
            )
                .takeIf {
                    it >= 0
                }
                ?: 0

        spinnerLine.setSelection(
            targetIndex,
            false
        )

        selectedLine =
            lines[
                targetIndex
            ]

        if (
            refresh &&
            ::historyContainer.isInitialized
        ) {
            refreshHistory()
        }
    }

    private fun createFieldLabel(
        text: String
    ): TextView {
        return TextView(this).apply {
            this.text =
                text

            textSize =
                13f

            setTypeface(
                typeface,
                Typeface.BOLD
            )

            setTextColor(
                Color.parseColor(
                    "#486581"
                )
            )
        }
    }

    private fun createVisibleSpinnerAdapter(
        items: List<String>
    ): ArrayAdapter<String> {
        return object :
            ArrayAdapter<String>(
                this,
                android.R.layout.simple_spinner_item,
                items
            ) {

            override fun getView(
                position: Int,
                convertView: View?,
                parent: android.view.ViewGroup
            ): View {
                val view =
                    super.getView(
                        position,
                        convertView,
                        parent
                    )

                styleSpinnerText(
                    view,
                    isDropDown = false
                )

                return view
            }

            override fun getDropDownView(
                position: Int,
                convertView: View?,
                parent: android.view.ViewGroup
            ): View {
                val view =
                    super.getDropDownView(
                        position,
                        convertView,
                        parent
                    )

                styleSpinnerText(
                    view,
                    isDropDown = true
                )

                return view
            }
        }.apply {
            setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
            )
        }
    }

    private fun styleSpinnerText(
        view: View,
        isDropDown: Boolean
    ) {
        if (
            view is TextView
        ) {
            view.setTextColor(
                Color.parseColor(
                    "#102A43"
                )
            )

            view.textSize =
                16f

            view.gravity =
                Gravity.CENTER_VERTICAL

            view.setPadding(
                dp(12),
                0,
                dp(12),
                0
            )

            view.setBackgroundColor(
                Color.parseColor(
                    if (
                        isDropDown
                    ) {
                        "#FFFFFF"
                    } else {
                        "#F0F4F8"
                    }
                )
            )
        }
    }

    private fun createEmptyCard(
        hasAnyHistory: Boolean
    ): View {
        return TextView(this).apply {
            text =
                if (
                    hasAnyHistory
                ) {
                    "선택한 Model / Line / 검사 항목 / 변경 유형 조건에 해당하는 변경 기록이 없습니다.\n\n필터를 변경하면 다른 이력을 확인할 수 있습니다."
                } else {
                    "아직 변경 기록이 없습니다.\n\n검사 기준 설정에서 기준값을 저장하거나 기본값으로 복원하면 이곳에 자동 기록됩니다."
                }
            textSize = 14f
            setTextColor(Color.parseColor("#627D98"))
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(28), dp(18), dp(28))
            background = roundedBackground(
                fillColor = "#FFFFFF",
                strokeColor = "#D9E2EC",
                radiusDp = 12
            )
        }
    }

    private fun createHistoryCard(
        index: Int,
        record: InspectionSpecChangeHistoryStore.ChangeRecord
    ): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(15), dp(16), dp(15))
            background = roundedBackground(
                fillColor = "#FFFFFF",
                strokeColor = "#D9E2EC",
                radiusDp = 12
            )
        }

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val title = TextView(this).apply {
            text = "${index + 1}. ${record.inspectionType.displayName}"
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#102A43"))
        }

        topRow.addView(
            title,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        topRow.addView(createActionBadge(record.action))
        card.addView(topRow)

        card.addView(
            detailText(
                "${record.model}  /  ${record.line}"
            ),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(8)
            }
        )

        card.addView(
            detailText(
                formatTimestamp(record.timestamp)
            ),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(3)
            }
        )

        card.addView(
            divider(),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(1)
            ).apply {
                topMargin = dp(12)
                bottomMargin = dp(12)
            }
        )

        val oldLabel = TextView(this).apply {
            text = "변경 전"
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#627D98"))
        }
        card.addView(oldLabel)

        card.addView(
            specValueBox(
                text = formatOldSpec(record),
                fillColor = "#F6F8FA"
            ),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(5)
            }
        )

        val newLabel = TextView(this).apply {
            text = "변경 후"
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#102A43"))
        }
        card.addView(
            newLabel,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(10)
            }
        )

        card.addView(
            specValueBox(
                text = formatNewSpec(record),
                fillColor = "#EAF7F4"
            ),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(5)
            }
        )

        return card
    }

    private fun createActionBadge(
        action: InspectionSpecChangeHistoryStore.ChangeAction
    ): View {
        val isReset =
            action == InspectionSpecChangeHistoryStore.ChangeAction.RESET_TO_DEFAULT

        return TextView(this).apply {
            text =
                if (isReset) {
                    "기본값 복원"
                } else {
                    "기준 저장"
                }

            textSize = 12f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(
                Color.parseColor(
                    if (isReset) {
                        "#8A4B08"
                    } else {
                        "#0B6E4F"
                    }
                )
            )
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(5), dp(10), dp(5))
            background = roundedBackground(
                fillColor =
                    if (isReset) {
                        "#FFF4E5"
                    } else {
                        "#E7F7F1"
                    },
                strokeColor =
                    if (isReset) {
                        "#F0C36E"
                    } else {
                        "#8FD3BC"
                    },
                radiusDp = 20
            )
        }
    }

    private fun detailText(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 14f
            setTextColor(Color.parseColor("#486581"))
        }
    }

    private fun specValueBox(
        text: String,
        fillColor: String
    ): View {
        return TextView(this).apply {
            this.text = text
            textSize = 14f
            setTextColor(Color.parseColor("#102A43"))
            setLineSpacing(0f, 1.12f)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = roundedBackground(
                fillColor = fillColor,
                strokeColor = "#D9E2EC",
                radiusDp = 8
            )
        }
    }

    private fun formatOldSpec(
        record: InspectionSpecChangeHistoryStore.ChangeRecord
    ): String {
        val normal = record.oldNormalBoundary
        val warning = record.oldWarningBoundary
        val limit = record.oldLimitBoundary

        if (
            normal == null ||
            warning == null ||
            limit == null
        ) {
            return "이전 저장값 없음"
        }

        return "정상 기준 ${formatNumber(normal)}  /  주의 기준 ${formatNumber(warning)}  /  한계 기준 ${formatNumber(limit)}"
    }

    private fun formatNewSpec(
        record: InspectionSpecChangeHistoryStore.ChangeRecord
    ): String {
        return "정상 기준 ${formatNumber(record.newNormalBoundary)}  /  주의 기준 ${formatNumber(record.newWarningBoundary)}  /  한계 기준 ${formatNumber(record.newLimitBoundary)}"
    }

    private fun formatNumber(value: Double): String {
        return if (value % 1.0 == 0.0) {
            value.toInt().toString()
        } else {
            String.format(Locale.getDefault(), "%.1f", value)
        }
    }

    private fun formatTimestamp(timestamp: Long): String {
        if (timestamp <= 0L) {
            return "변경 시간 기록 없음"
        }

        return SimpleDateFormat(
            "yyyy-MM-dd HH:mm:ss",
            Locale.getDefault()
        ).format(Date(timestamp))
    }

    private fun divider(): View {
        return View(this).apply {
            setBackgroundColor(Color.parseColor("#D9E2EC"))
        }
    }

    private fun createActionButton(
        text: String,
        backgroundColor: String,
        onClick: () -> Unit
    ): Button {
        return Button(this).apply {
            this.text = text
            textSize = 15f
            isAllCaps = false
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            backgroundTintList = ColorStateList.valueOf(
                Color.parseColor(backgroundColor)
            )
            setOnClickListener {
                onClick()
            }
        }
    }

    private fun roundedBackground(
        fillColor: String,
        strokeColor: String,
        radiusDp: Int
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.parseColor(fillColor))
            setStroke(dp(1), Color.parseColor(strokeColor))
            cornerRadius = dp(radiusDp).toFloat()
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density + 0.5f).toInt()
    }
}
