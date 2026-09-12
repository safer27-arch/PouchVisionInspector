package com.pouchvision.inspector

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

/*
 * =============================================================
 * 품질 Dashboard
 * =============================================================
 *
 * 기능
 * - 오늘 / 최근 7일 / 이번 달
 * - 전체 Model 또는 특정 Model
 * - 전체 Line 또는 특정 Line
 * - Model 선택 시 해당 Model의 Line만 표시
 * - Quality Score 요약
 * - 정상 / 주의 / 한계정상 / 불량 건수
 * - 우선 확인 항목
 * - 검사 항목별 현황
 *
 * 중요
 * - Dashboard는 저장된 검사 이력을 집계해서 보여주는 화면입니다.
 * - 검사 알고리즘과 판정 기준은 변경하지 않습니다.
 * =============================================================
 */

class DashboardActivity :
    AppCompatActivity() {

    companion object {

        private const val TYPE_TOTAL_SESSION =
            "TOTAL SESSION"

        private const val TYPE_BOTTOM =
            "BOTTOM CORNER"

        private const val TYPE_SEAL =
            "SEAL"

        private const val TYPE_FORMING =
            "FORMING"

        private const val TYPE_TAB =
            "TAB"

        private const val TYPE_DISASSEMBLY =
            "DISASSEMBLY"

        private const val FILTER_ALL_MODELS =
            "전체 Model"

        private const val FILTER_ALL_LINES =
            "전체 Line"
    }

    private enum class Period(
        val title: String
    ) {

        TODAY(
            "오늘"
        ),

        WEEK(
            "최근 7일"
        ),

        MONTH(
            "이번 달"
        )
    }

    private data class ItemSummary(
        val type: String,
        val count: Int,
        val averageScore: Double,
        val issueCount: Int,
        val issueRate: Double
    )

    private val inspectionOrder =
        listOf(
            TYPE_BOTTOM,
            TYPE_SEAL,
            TYPE_FORMING,
            TYPE_TAB,
            TYPE_DISASSEMBLY
        )

    private lateinit var rootContent:
        LinearLayout

    private lateinit var spinnerModel:
        Spinner

    private lateinit var spinnerLine:
        Spinner

    private lateinit var periodStatusText:
        TextView

    private var selectedPeriod =
        Period.TODAY

    /*
     * Spinner를 최초 구성할 때 발생하는
     * 불필요한 중복 render를 줄이기 위한 Flag
     */
    private var filterUiReady =
        false

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(
            savedInstanceState
        )

        setContentView(
            createScreen()
        )

        setupProductionFilters()

        filterUiReady =
            true

        renderDashboard()
    }

    override fun onResume() {

        super.onResume()

        if (
            ::rootContent.isInitialized &&
            filterUiReady
        ) {

            renderDashboard()
        }
    }

    /*
     * =========================================================
     * 전체 화면
     * =========================================================
     */

    private fun createScreen(): View {

        val scrollView =
            ScrollView(
                this
            ).apply {

                isFillViewport =
                    true

                setBackgroundColor(
                    Color.parseColor(
                        "#F4F7FA"
                    )
                )
            }

        val outer =
            LinearLayout(
                this
            ).apply {

                orientation =
                    LinearLayout.VERTICAL
            }

        /*
         * Header
         */
        val header =
            LinearLayout(
                this
            ).apply {

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    dp(20),
                    dp(18),
                    dp(20),
                    dp(18)
                )

                setBackgroundColor(
                    Color.parseColor(
                        "#102F4A"
                    )
                )
            }

        header.addView(
            TextView(
                this
            ).apply {

                text =
                    "품질 Dashboard"

                textSize =
                    28f

                setTypeface(
                    null,
                    Typeface.BOLD
                )

                setTextColor(
                    Color.WHITE
                )
            }
        )

        header.addView(
            TextView(
                this
            ).apply {

                text =
                    "Pouch Quality Management"

                textSize =
                    14f

                setTextColor(
                    Color.parseColor(
                        "#D9E6F2"
                    )
                )

                setPadding(
                    0,
                    dp(4),
                    0,
                    0
                )
            }
        )

        outer.addView(
            header
        )

        rootContent =
            LinearLayout(
                this
            ).apply {

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    dp(16),
                    dp(16),
                    dp(16),
                    dp(24)
                )
            }

        /*
         * =====================================================
         * Model / Line 필터
         * =====================================================
         */

        rootContent.addView(
            sectionTitle(
                "생산 조건"
            )
        )

        val filterCard =
            createCard()

        filterCard.addView(
            labelText(
                "Model"
            )
        )

        spinnerModel =
            Spinner(
                this
            ).apply {

                minimumHeight =
                    dp(52)

                setPadding(
                    dp(8),
                    0,
                    dp(8),
                    0
                )

                background =
                    roundedBackground(
                        "#F4F6F8",
                        8f
                    )
            }

        filterCard.addView(
            spinnerModel,
            fullWidthParams(
                top = 4,
                bottom = 10
            )
        )

        filterCard.addView(
            labelText(
                "Line"
            )
        )

        spinnerLine =
            Spinner(
                this
            ).apply {

                minimumHeight =
                    dp(52)

                setPadding(
                    dp(8),
                    0,
                    dp(8),
                    0
                )

                background =
                    roundedBackground(
                        "#F4F6F8",
                        8f
                    )
            }

        filterCard.addView(
            spinnerLine,
            fullWidthParams(
                top = 4,
                bottom = 0
            )
        )

        rootContent.addView(
            filterCard
        )

        /*
         * =====================================================
         * 기간
         * =====================================================
         */

        rootContent.addView(
            sectionTitle(
                "조회 기간"
            )
        )

        val periodRow =
            LinearLayout(
                this
            ).apply {

                orientation =
                    LinearLayout.HORIZONTAL

                gravity =
                    Gravity.CENTER
            }

        periodRow.addView(
            periodButton(
                "오늘"
            ) {

                selectedPeriod =
                    Period.TODAY

                renderDashboard()
            }
        )

        periodRow.addView(
            periodButton(
                "최근 7일"
            ) {

                selectedPeriod =
                    Period.WEEK

                renderDashboard()
            }
        )

        periodRow.addView(
            periodButton(
                "이번 달"
            ) {

                selectedPeriod =
                    Period.MONTH

                renderDashboard()
            }
        )

        rootContent.addView(
            periodRow
        )

        periodStatusText =
            TextView(
                this
            ).apply {

                textSize =
                    13f

                setTextColor(
                    Color.parseColor(
                        "#627D98"
                    )
                )

                setPadding(
                    dp(2),
                    dp(10),
                    dp(2),
                    dp(8)
                )
            }

        rootContent.addView(
            periodStatusText
        )

        /*
         * 이 아래 View들은 renderDashboard()에서 재생성합니다.
         *
         * 고정 View 개수:
         * 0 생산조건 제목
         * 1 생산조건 Card
         * 2 조회기간 제목
         * 3 기간 버튼 Row
         * 4 기간 상태 Text
         */
        outer.addView(
            rootContent
        )

        scrollView.addView(
            outer
        )

        return scrollView
    }

    /*
     * =========================================================
     * Model / Line 필터
     * =========================================================
     */

    private fun setupProductionFilters() {

        val modelItems =
            mutableListOf<String>()

        modelItems.add(
            FILTER_ALL_MODELS
        )

        modelItems.addAll(
            ProductionContextStore.getModels(
                this
            )
        )

        spinnerModel.adapter =
            createVisibleSpinnerAdapter(
                modelItems
            )

        /*
         * Dashboard 최초 진입 시
         * 전체 Model을 기본으로 보여줍니다.
         */
        spinnerModel.setSelection(
            0
        )

        updateLineSpinner(
            model = FILTER_ALL_MODELS,
            preferredLine = FILTER_ALL_LINES
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

                    val selectedModel =
                        spinnerModel.selectedItem
                            ?.toString()
                            ?: FILTER_ALL_MODELS

                    updateLineSpinner(
                        model = selectedModel,
                        preferredLine = FILTER_ALL_LINES
                    )

                    if (
                        filterUiReady
                    ) {

                        renderDashboard()
                    }
                }

                override fun onNothingSelected(
                    parent: AdapterView<*>?
                ) {
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
                        filterUiReady
                    ) {

                        renderDashboard()
                    }
                }

                override fun onNothingSelected(
                    parent: AdapterView<*>?
                ) {
                }
            }
    }

    private fun updateLineSpinner(
        model: String,
        preferredLine: String =
            FILTER_ALL_LINES
    ) {

        val lines =
            if (
                model ==
                FILTER_ALL_MODELS
            ) {

                ProductionContextStore
                    .getModels(
                        this
                    )
                    .flatMap { modelName ->

                        ProductionContextStore
                            .getLinesForModel(
                                modelName
                            )
                    }
                    .distinct()
                    .sortedWith(
                        compareBy<String> {

                            it.removePrefix(
                                "Line "
                            )
                                .toIntOrNull()
                                ?: Int.MAX_VALUE
                        }
                            .thenBy {
                                it
                            }
                    )

            } else {

                ProductionContextStore
                    .getLinesForModel(
                        model
                    )
            }

        val items =
            mutableListOf<String>()

        items.add(
            FILTER_ALL_LINES
        )

        items.addAll(
            lines
        )

        spinnerLine.adapter =
            createVisibleSpinnerAdapter(
                items
            )

        val preferredPosition =
            items.indexOf(
                preferredLine
            )

        spinnerLine.setSelection(
            if (
                preferredPosition >= 0
            ) {
                preferredPosition
            } else {
                0
            }
        )
    }

    /*
     * =========================================================
     * Dashboard 갱신
     * =========================================================
     */

    private fun renderDashboard() {

        /*
         * 고정 UI 5개 아래만 삭제합니다.
         */
        while (
            rootContent.childCount >
            5
        ) {

            rootContent.removeViewAt(
                5
            )
        }

        val allRecords =
            InspectionHistoryStore.load(
                this
            )

        val startTime =
            periodStartTime(
                selectedPeriod
            )

        val selectedModel =
            spinnerModel.selectedItem
                ?.toString()
                ?: FILTER_ALL_MODELS

        val selectedLine =
            spinnerLine.selectedItem
                ?.toString()
                ?: FILTER_ALL_LINES

        val periodRecords =
            allRecords.filter { record ->

                val periodMatches =
                    record.id >=
                        startTime

                val modelMatches =
                    selectedModel ==
                        FILTER_ALL_MODELS ||
                        record.model.equals(
                            selectedModel,
                            ignoreCase = true
                        )

                val lineMatches =
                    selectedLine ==
                        FILTER_ALL_LINES ||
                        record.line.equals(
                            selectedLine,
                            ignoreCase = true
                        )

                periodMatches &&
                    modelMatches &&
                    lineMatches
            }

        /*
         * TOTAL SESSION은 개별 검사 Score / 건수 계산에서 제외합니다.
         */
        val inspectionRecords =
            periodRecords.filter { record ->

                !record.inspectionType.equals(
                    TYPE_TOTAL_SESSION,
                    ignoreCase = true
                ) &&
                    inspectionOrder.any { type ->

                        record.inspectionType.equals(
                            type,
                            ignoreCase = true
                        )
                    }
            }

        val totalSessions =
            periodRecords.count { record ->

                record.inspectionType.equals(
                    TYPE_TOTAL_SESSION,
                    ignoreCase = true
                )
            }

        periodStatusText.text =
            buildString {

                append(
                    selectedPeriod.title
                )

                append(
                    "  |  "
                )

                append(
                    selectedModel
                )

                append(
                    "  |  "
                )

                append(
                    selectedLine
                )
            }

        addOverallSummary(
            records = inspectionRecords,
            totalSessions = totalSessions
        )

        addQualityRiskSignals(
            inspectionRecords
        )

        addTrendSummary(
            inspectionRecords
        )

        addLongTermTrendSummary(
            allRecords = allRecords,
            selectedModel = selectedModel,
            selectedLine = selectedLine
        )

        addInspectionComplianceSummary(
            allRecords = allRecords,
            selectedModel = selectedModel,
            selectedLine = selectedLine
        )

        addJudgmentSummary(
            inspectionRecords
        )

        addAttentionSummary(
            inspectionRecords
        )

        addRecentIssueSummary(
            inspectionRecords
        )

        addInspectionItemSummary(
            inspectionRecords
        )

        addActionButtons()
    }

    /*
     * =========================================================
     * 전체 품질 요약
     * =========================================================
     */

    private fun addOverallSummary(
        records:
        List<
            InspectionHistoryStore
                .InspectionRecord
            >,
        totalSessions: Int
    ) {

        rootContent.addView(
            sectionTitle(
                "품질 요약"
            )
        )

        val count =
            records.size

        val average =
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

        val minimum =
            records.minByOrNull {
                it.score
            }

        val issueCount =
            records.count {

                judgmentSeverity(
                    it.judgment
                ) >=
                    2
            }

        val issueRate =
            if (
                count >
                0
            ) {

                issueCount.toDouble() /
                    count.toDouble() *
                    100.0

            } else {

                0.0
            }

        val card =
            createCard()

        card.addView(
            TextView(
                this
            ).apply {

                text =
                    String.format(
                        Locale.getDefault(),
                        "평균 Quality Score  %.1f / 100",
                        average
                    )

                textSize =
                    21f

                setTypeface(
                    null,
                    Typeface.BOLD
                )

                setTextColor(
                    scoreColor(
                        average
                    )
                )
            }
        )

        val progress =
            ProgressBar(
                this,
                null,
                android.R.attr.progressBarStyleHorizontal
            ).apply {

                max =
                    100

                this.progress =
                    average
                        .roundToInt()
                        .coerceIn(
                            0,
                            100
                        )

                progressTintList =
                    ColorStateList.valueOf(
                        scoreColor(
                            average
                        )
                    )

                progressBackgroundTintList =
                    ColorStateList.valueOf(
                        Color.parseColor(
                            "#D9E2EC"
                        )
                    )
            }

        card.addView(
            progress,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(12)
            ).apply {

                topMargin =
                    dp(10)

                bottomMargin =
                    dp(12)
            }
        )

        card.addView(
            TextView(
                this
            ).apply {

                text =
                    String.format(
                        Locale.getDefault(),
                        """
검사 건수 : %d건
종합검사 Session : %d회
주의 이상 발생 : %d건 (%.1f%%)
최저 Score : %s
                        """.trimIndent(),

                        count,
                        totalSessions,
                        issueCount,
                        issueRate,

                        if (
                            minimum !=
                            null
                        ) {

                            String.format(
                                Locale.getDefault(),
                                "%s %.1f / %s",
                                displayTypeName(
                                    minimum.inspectionType
                                ),
                                minimum.score,
                                minimum.judgment
                            )

                        } else {

                            "-"
                        }
                    )

                textSize =
                    15f

                setTextColor(
                    Color.parseColor(
                        "#334E68"
                    )
                )

                setLineSpacing(
                    0f,
                    1.18f
                )
            }
        )

        rootContent.addView(
            card
        )
    }

    /*
     * =========================================================
     * 품질 위험 신호 / 조기경보
     * =========================================================
     *
     * 현재 선택된 기간 / Model / Line 데이터만 사용합니다.
     *
     * 신호 예:
     * - 최근 불량 또는 한계정상 발생
     * - 최근 3회 연속 Score 하락
     * - 최근 3건 평균이 직전 3건 평균보다 5점 이상 하락
     * - 주의 이상 비율 50% 이상
     *
     * ML 학습 없이 저장된 검사 이력으로 예방 품질 신호를 만듭니다.
     */
    private fun addQualityRiskSignals(
        records:
        List<
            InspectionHistoryStore
                .InspectionRecord
            >
    ) {

        rootContent.addView(
            sectionTitle(
                "품질 위험 신호"
            )
        )

        val card =
            createCard()

        if (
            records.isEmpty()
        ) {

            card.addView(
                TextView(
                    this
                ).apply {

                    text =
                        "현재 조건에 분석할 검사 데이터가 없습니다."

                    textSize =
                        14f

                    setTextColor(
                        Color.parseColor(
                            "#829AB1"
                        )
                    )
                }
            )

            rootContent.addView(
                card
            )

            return
        }

        data class RiskSignal(
            val level: Int,
            val item: String,
            val message: String
        )

        val signals =
            mutableListOf<
                RiskSignal
                >()

        for (
            type in
            inspectionOrder
        ) {

            val itemRecords =
                records
                    .filter {
                        it.inspectionType.equals(
                            type,
                            ignoreCase = true
                        )
                    }
                    .sortedBy {
                        it.id
                    }

            if (
                itemRecords.isEmpty()
            ) {
                continue
            }

            val latest =
                itemRecords.last()

            val issueCount =
                itemRecords.count {
                    judgmentSeverity(
                        it.judgment
                    ) >=
                        2
                }

            val issueRate =
                issueCount.toDouble() /
                    itemRecords.size.toDouble() *
                    100.0

            /*
             * 가장 최근 판정 자체가 위험한 경우
             */
            when {

                latest.judgment.contains(
                    "불량"
                ) -> {

                    signals.add(
                        RiskSignal(
                            level = 4,
                            item =
                                displayTypeName(
                                    type
                                ),
                            message =
                                String.format(
                                    Locale.getDefault(),
                                    "최근 불량 발생 · Score %.1f",
                                    latest.score
                                )
                        )
                    )
                }

                latest.judgment.contains(
                    "한계"
                ) -> {

                    signals.add(
                        RiskSignal(
                            level = 3,
                            item =
                                displayTypeName(
                                    type
                                ),
                            message =
                                String.format(
                                    Locale.getDefault(),
                                    "최근 한계정상 발생 · Score %.1f",
                                    latest.score
                                )
                        )
                    )
                }
            }

            /*
             * 최근 3회 연속 Score 하락
             */
            if (
                itemRecords.size >=
                3
            ) {

                val last3 =
                    itemRecords
                        .takeLast(
                            3
                        )

                if (
                    last3[0].score >
                    last3[1].score &&
                    last3[1].score >
                    last3[2].score
                ) {

                    val drop =
                        last3[0].score -
                            last3[2].score

                    signals.add(
                        RiskSignal(
                            level = 3,
                            item =
                                displayTypeName(
                                    type
                                ),
                            message =
                                String.format(
                                    Locale.getDefault(),
                                    "3회 연속 Score 하락 · %.1f → %.1f (▼ %.1f)",
                                    last3[0].score,
                                    last3[2].score,
                                    drop
                                )
                        )
                    )
                }
            }

            /*
             * 최근 3건 평균 vs 직전 3건 평균
             */
            if (
                itemRecords.size >=
                6
            ) {

                val previous3 =
                    itemRecords
                        .dropLast(
                            3
                        )
                        .takeLast(
                            3
                        )
                        .map {
                            it.score
                        }
                        .average()

                val recent3 =
                    itemRecords
                        .takeLast(
                            3
                        )
                        .map {
                            it.score
                        }
                        .average()

                val drop =
                    previous3 -
                        recent3

                if (
                    drop >=
                    5.0
                ) {

                    signals.add(
                        RiskSignal(
                            level =
                                if (
                                    drop >=
                                    10.0
                                ) {
                                    4
                                } else {
                                    3
                                },
                            item =
                                displayTypeName(
                                    type
                                ),
                            message =
                                String.format(
                                    Locale.getDefault(),
                                    "최근 평균 악화 · %.1f → %.1f (▼ %.1f)",
                                    previous3,
                                    recent3,
                                    drop
                                )
                        )
                    )
                }
            }

            /*
             * 충분한 데이터가 쌓였을 때 이상률 확인
             */
            if (
                itemRecords.size >=
                4 &&
                issueRate >=
                50.0
            ) {

                signals.add(
                    RiskSignal(
                        level =
                            if (
                                issueRate >=
                                75.0
                            ) {
                                4
                            } else {
                                2
                            },
                        item =
                            displayTypeName(
                                type
                            ),
                        message =
                            String.format(
                                Locale.getDefault(),
                                "주의 이상 비율 %.0f%% (%d/%d건)",
                                issueRate,
                                issueCount,
                                itemRecords.size
                            )
                    )
                )
            }
        }

        val sortedSignals =
            signals
                .sortedWith(
                    compareByDescending<RiskSignal> {
                        it.level
                    }.thenBy {
                        it.item
                    }
                )
                .take(
                    6
                )

        if (
            sortedSignals.isEmpty()
        ) {

            card.addView(
                TextView(
                    this
                ).apply {

                    text =
                        "✓ 현재 조건에서 뚜렷한 품질 악화 신호가 없습니다.\n" +
                            "검사 데이터가 누적되면 연속 하락과 평균 악화를 자동 감지합니다."

                    textSize =
                        14f

                    setTextColor(
                        Color.parseColor(
                            "#2E7D32"
                        )
                    )

                    setTypeface(
                        null,
                        Typeface.BOLD
                    )
                }
            )

        } else {

            sortedSignals.forEachIndexed {
                    index,
                    signal ->

                val levelText =
                    when (
                        signal.level
                    ) {

                        4 ->
                            "위험"

                        3 ->
                            "경고"

                        else ->
                            "주의"
                    }

                val levelColor =
                    when (
                        signal.level
                    ) {

                        4 ->
                            Color.parseColor(
                                "#C62828"
                            )

                        3 ->
                            Color.parseColor(
                                "#E65100"
                            )

                        else ->
                            Color.parseColor(
                                "#C49000"
                            )
                    }

                card.addView(
                    TextView(
                        this
                    ).apply {

                        text =
                            "[$levelText] ${signal.item}\n${signal.message}"

                        textSize =
                            15f

                        setTypeface(
                            null,
                            Typeface.BOLD
                        )

                        setTextColor(
                            levelColor
                        )

                        setPadding(
                            0,
                            if (
                                index ==
                                0
                            ) {
                                0
                            } else {
                                dp(12)
                            },
                            0,
                            dp(8)
                        )
                    }
                )
            }

            card.addView(
                TextView(
                    this
                ).apply {

                    text =
                        "※ 위험 신호는 저장된 검사 이력의 변화 추세를 이용한 예방 관리용 지표입니다."

                    textSize =
                        12f

                    setTextColor(
                        Color.parseColor(
                            "#829AB1"
                        )
                    )

                    setPadding(
                        0,
                        dp(8),
                        0,
                        0
                    )
                }
            )
        }

        rootContent.addView(
            card
        )
    }

    /*
     * =========================================================
     * 장기 품질 추세
     * =========================================================
     *
     * - 최근 7일: 일별 검사 건수 / 평균 Score / 이상 판정률
     * - 최근 4주: 주별 검사 건수 / 평균 Score / 이상 판정률
     *
     * 현재 Dashboard의 Model / Line 필터를 그대로 적용합니다.
     * 기간 버튼(오늘/7일/이번 달)과는 별개로 장기 변화를 보기 위한
     * 고정 범위 요약입니다.
     * =========================================================
     */

    private fun addLongTermTrendSummary(
        allRecords:
        List<
            InspectionHistoryStore
                .InspectionRecord
            >,
        selectedModel: String,
        selectedLine: String
    ) {

        rootContent.addView(
            sectionTitle(
                "장기 품질 추세"
            )
        )

        val filtered =
            allRecords
                .filter { record ->

                    val isInspection =
                        !record.inspectionType.equals(
                            TYPE_TOTAL_SESSION,
                            ignoreCase = true
                        )

                    val modelMatches =
                        selectedModel ==
                            FILTER_ALL_MODELS ||
                            record.model.equals(
                                selectedModel,
                                ignoreCase = true
                            )

                    val lineMatches =
                        selectedLine ==
                            FILTER_ALL_LINES ||
                            record.line.equals(
                                selectedLine,
                                ignoreCase = true
                            )

                    isInspection &&
                        modelMatches &&
                        lineMatches
                }

        val card =
            createCard()

        val intro =
            TextView(
                this
            ).apply {

                text =
                    "최근 7일 일별 + 최근 4주 주별 추세\n" +
                        "※ 이상 판정률 = 주의 / 한계정상 / 불량 비율"

                textSize =
                    13f

                setTextColor(
                    Color.parseColor(
                        "#627D98"
                    )
                )
            }

        card.addView(
            intro
        )

        val dailyTitle =
            TextView(
                this
            ).apply {

                text =
                    "최근 7일 · 일별"

                textSize =
                    16f

                setTypeface(
                    typeface,
                    Typeface.BOLD
                )

                setTextColor(
                    Color.parseColor(
                        "#102A43"
                    )
                )

                setPadding(
                    0,
                    dp(14),
                    0,
                    dp(6)
                )
            }

        card.addView(
            dailyTitle
        )

        val dailyText =
            TextView(
                this
            )

        dailyText.text =
            buildDailyTrendText(
                records = filtered
            )

        dailyText.textSize =
            14f

        dailyText.setTextColor(
            Color.parseColor(
                "#334E68"
            )
        )

        dailyText.setLineSpacing(
            0f,
            1.18f
        )

        card.addView(
            dailyText
        )

        val weeklyTitle =
            TextView(
                this
            ).apply {

                text =
                    "최근 4주 · 주별"

                textSize =
                    16f

                setTypeface(
                    typeface,
                    Typeface.BOLD
                )

                setTextColor(
                    Color.parseColor(
                        "#102A43"
                    )
                )

                setPadding(
                    0,
                    dp(16),
                    0,
                    dp(6)
                )
            }

        card.addView(
            weeklyTitle
        )

        val weeklyText =
            TextView(
                this
            )

        weeklyText.text =
            buildWeeklyTrendText(
                records = filtered
            )

        weeklyText.textSize =
            14f

        weeklyText.setTextColor(
            Color.parseColor(
                "#334E68"
            )
        )

        weeklyText.setLineSpacing(
            0f,
            1.18f
        )

        card.addView(
            weeklyText
        )

        val signal =
            buildLongTermSignal(
                records = filtered
            )

        if (
            signal.isNotBlank()
        ) {

            val signalView =
                TextView(
                    this
                ).apply {

                    text =
                        signal

                    textSize =
                        14f

                    setTypeface(
                        typeface,
                        Typeface.BOLD
                    )

                    setTextColor(
                        Color.parseColor(
                            "#B54708"
                        )
                    )

                    setPadding(
                        0,
                        dp(14),
                        0,
                        0
                    )
                }

            card.addView(
                signalView
            )
        }

        rootContent.addView(
            card
        )
    }

    private fun buildDailyTrendText(
        records:
        List<
            InspectionHistoryStore
                .InspectionRecord
            >
    ): String {

        val now =
            Calendar.getInstance()

        return buildString {

            for (
                offset in 6 downTo 0
            ) {

                val day =
                    (
                        now.clone() as
                            Calendar
                        )

                day.add(
                    Calendar.DAY_OF_YEAR,
                    -offset
                )

                day.set(
                    Calendar.HOUR_OF_DAY,
                    0
                )

                day.set(
                    Calendar.MINUTE,
                    0
                )

                day.set(
                    Calendar.SECOND,
                    0
                )

                day.set(
                    Calendar.MILLISECOND,
                    0
                )

                val start =
                    day.timeInMillis

                day.add(
                    Calendar.DAY_OF_YEAR,
                    1
                )

                val end =
                    day.timeInMillis

                val dayRecords =
                    records.filter {
                        it.id >=
                            start &&
                        it.id <
                            end
                    }

                val labelCalendar =
                    Calendar.getInstance()
                        .apply {
                            timeInMillis =
                                start
                        }

                val label =
                    String.format(
                        Locale.getDefault(),
                        "%02d/%02d",
                        labelCalendar.get(
                            Calendar.MONTH
                        ) + 1,
                        labelCalendar.get(
                            Calendar.DAY_OF_MONTH
                        )
                    )

                if (
                    dayRecords.isEmpty()
                ) {

                    append(
                        "$label  ·  0건"
                    )

                } else {

                    val average =
                        dayRecords
                            .map {
                                it.score
                            }
                            .average()

                    val issueCount =
                        dayRecords.count {
                            judgmentSeverity(
                                it.judgment
                            ) >=
                                2
                        }

                    val issueRate =
                        issueCount
                            .toDouble() /
                            dayRecords.size
                                .toDouble() *
                            100.0

                    append(
                        "$label  ·  ${dayRecords.size}건  ·  Avg ${
                            String.format(
                                Locale.getDefault(),
                                "%.1f",
                                average
                            )
                        }  ·  이상 ${
                            String.format(
                                Locale.getDefault(),
                                "%.0f",
                                issueRate
                            )
                        }%"
                    )
                }

                if (
                    offset >
                    0
                ) {

                    append(
                        "\n"
                    )
                }
            }
        }
    }

    private fun buildWeeklyTrendText(
        records:
        List<
            InspectionHistoryStore
                .InspectionRecord
            >
    ): String {

        val currentWeekStart =
            Calendar.getInstance()
                .apply {

                    firstDayOfWeek =
                        Calendar.MONDAY

                    set(
                        Calendar.DAY_OF_WEEK,
                        Calendar.MONDAY
                    )

                    set(
                        Calendar.HOUR_OF_DAY,
                        0
                    )

                    set(
                        Calendar.MINUTE,
                        0
                    )

                    set(
                        Calendar.SECOND,
                        0
                    )

                    set(
                        Calendar.MILLISECOND,
                        0
                    )
                }

        return buildString {

            for (
                weekOffset in 3 downTo 0
            ) {

                val startCalendar =
                    (
                        currentWeekStart.clone() as
                            Calendar
                        )

                startCalendar.add(
                    Calendar.WEEK_OF_YEAR,
                    -weekOffset
                )

                val endCalendar =
                    (
                        startCalendar.clone() as
                            Calendar
                        )

                endCalendar.add(
                    Calendar.WEEK_OF_YEAR,
                    1
                )

                val weekRecords =
                    records.filter {
                        it.id >=
                            startCalendar.timeInMillis &&
                        it.id <
                            endCalendar.timeInMillis
                    }

                val endLabel =
                    (
                        endCalendar.clone() as
                            Calendar
                        ).apply {
                            add(
                                Calendar.DAY_OF_YEAR,
                                -1
                            )
                        }

                val label =
                    String.format(
                        Locale.getDefault(),
                        "%02d/%02d~%02d/%02d",
                        startCalendar.get(
                            Calendar.MONTH
                        ) + 1,
                        startCalendar.get(
                            Calendar.DAY_OF_MONTH
                        ),
                        endLabel.get(
                            Calendar.MONTH
                        ) + 1,
                        endLabel.get(
                            Calendar.DAY_OF_MONTH
                        )
                    )

                if (
                    weekRecords.isEmpty()
                ) {

                    append(
                        "$label  ·  0건"
                    )

                } else {

                    val average =
                        weekRecords
                            .map {
                                it.score
                            }
                            .average()

                    val issueCount =
                        weekRecords.count {
                            judgmentSeverity(
                                it.judgment
                            ) >=
                                2
                        }

                    val issueRate =
                        issueCount
                            .toDouble() /
                            weekRecords.size
                                .toDouble() *
                            100.0

                    append(
                        "$label  ·  ${weekRecords.size}건  ·  Avg ${
                            String.format(
                                Locale.getDefault(),
                                "%.1f",
                                average
                            )
                        }  ·  이상 ${
                            String.format(
                                Locale.getDefault(),
                                "%.0f",
                                issueRate
                            )
                        }%"
                    )
                }

                if (
                    weekOffset >
                    0
                ) {

                    append(
                        "\n"
                    )
                }
            }
        }
    }

    private fun buildLongTermSignal(
        records:
        List<
            InspectionHistoryStore
                .InspectionRecord
            >
    ): String {

        val dayStart =
            Calendar.getInstance()
                .apply {

                    set(
                        Calendar.HOUR_OF_DAY,
                        0
                    )

                    set(
                        Calendar.MINUTE,
                        0
                    )

                    set(
                        Calendar.SECOND,
                        0
                    )

                    set(
                        Calendar.MILLISECOND,
                        0
                    )
                }

        val recentStart =
            (
                dayStart.clone() as
                    Calendar
                ).apply {
                add(
                    Calendar.DAY_OF_YEAR,
                    -2
                )
            }
                .timeInMillis

        val previousStart =
            (
                dayStart.clone() as
                    Calendar
                ).apply {
                add(
                    Calendar.DAY_OF_YEAR,
                    -5
                )
            }
                .timeInMillis

        val tomorrow =
            (
                dayStart.clone() as
                    Calendar
                ).apply {
                add(
                    Calendar.DAY_OF_YEAR,
                    1
                )
            }
                .timeInMillis

        val recent3 =
            records.filter {
                it.id >=
                    recentStart &&
                it.id <
                    tomorrow
            }

        val previous3 =
            records.filter {
                it.id >=
                    previousStart &&
                it.id <
                    recentStart
            }

        if (
            recent3.isEmpty() ||
            previous3.isEmpty()
        ) {

            return ""
        }

        val recentAverage =
            recent3
                .map {
                    it.score
                }
                .average()

        val previousAverage =
            previous3
                .map {
                    it.score
                }
                .average()

        val drop =
            previousAverage -
                recentAverage

        val recentIssueRate =
            recent3.count {
                judgmentSeverity(
                    it.judgment
                ) >=
                    2
            }
                .toDouble() /
                recent3.size
                    .toDouble() *
                100.0

        val previousIssueRate =
            previous3.count {
                judgmentSeverity(
                    it.judgment
                ) >=
                    2
            }
                .toDouble() /
                previous3.size
                    .toDouble() *
                100.0

        return when {

            drop >=
                10.0 -> {

                "🚨 최근 3일 평균 Score가 이전 3일 대비 ${
                    String.format(
                        Locale.getDefault(),
                        "%.1f",
                        drop
                    )
                }점 하락했습니다."
            }

            drop >=
                5.0 -> {

                "⚠ 최근 3일 평균 Score가 이전 3일 대비 ${
                    String.format(
                        Locale.getDefault(),
                        "%.1f",
                        drop
                    )
                }점 하락했습니다."
            }

            recentIssueRate -
                previousIssueRate >=
                20.0 -> {

                "⚠ 최근 3일 이상 판정률이 이전 3일 대비 ${
                    String.format(
                        Locale.getDefault(),
                        "%.0f",
                        recentIssueRate -
                            previousIssueRate
                    )
                }%p 증가했습니다."
            }

            else ->
                ""
        }
    }

    /*
     * =========================================================
     * 검사 주기 준수 현황
     * =========================================================
     * 기본 관리 기준: 60분마다 최소 1건의 검사 결과 저장.
     * 현재 선택 Model / Line 기준으로 오늘 경과 시간대를 확인합니다.
     * 동일 60분 구간에 여러 건을 검사해도 준수 슬롯은 1회로 계산합니다.
     * =========================================================
     */
    private fun addInspectionComplianceSummary(
        allRecords: List<InspectionHistoryStore.InspectionRecord>,
        selectedModel: String,
        selectedLine: String
    ) {
        rootContent.addView(sectionTitle("검사 주기 준수 현황"))

        val records =
            allRecords.filter { record ->
                !record.inspectionType.equals(TYPE_TOTAL_SESSION, ignoreCase = true) &&
                    (selectedModel == FILTER_ALL_MODELS ||
                        record.model.equals(selectedModel, ignoreCase = true)) &&
                    (selectedLine == FILTER_ALL_LINES ||
                        record.line.equals(selectedLine, ignoreCase = true))
            }

        val now = Calendar.getInstance()
        val start = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // 현재 진행 중인 시간대까지 계획 슬롯으로 포함합니다.
        val plannedSlots = now.get(Calendar.HOUR_OF_DAY) + 1
        val completedHours = mutableSetOf<Int>()

        records.forEach { record ->
            if (record.id >= start.timeInMillis && record.id <= now.timeInMillis) {
                val c = Calendar.getInstance().apply { timeInMillis = record.id }
                completedHours.add(c.get(Calendar.HOUR_OF_DAY))
            }
        }

        val actualSlots = completedHours.size
        val compliance =
            if (plannedSlots > 0) actualSlots.toDouble() / plannedSlots.toDouble() * 100.0
            else 0.0

        val missing = (0 until plannedSlots).filter { it !in completedHours }

        val card = createCard()

        val main = TextView(this).apply {
            text =
                "관리 기준 : 60분마다 최소 1회\n" +
                "오늘 계획 : ${plannedSlots}회  |  실시 : ${actualSlots}회\n" +
                "검사 준수율 : ${String.format(Locale.getDefault(), "%.1f", compliance)}%"
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(
                when {
                    compliance >= 90.0 -> Color.parseColor("#2E7D32")
                    compliance >= 70.0 -> Color.parseColor("#E67E00")
                    else -> Color.parseColor("#C62828")
                }
            )
            setLineSpacing(0f, 1.18f)
        }
        card.addView(main)

        val missingText = TextView(this).apply {
            text =
                if (missing.isEmpty()) {
                    "\n✅ 현재까지 누락 시간대 없음"
                } else {
                    val labels = missing.takeLast(8).joinToString(", ") {
                        String.format(Locale.getDefault(), "%02d:00~%02d:00", it, (it + 1) % 24)
                    }
                    val more = if (missing.size > 8) "\n외 ${missing.size - 8}개 시간대" else ""
                    "\n⚠ 누락 시간대\n$labels$more"
                }
            textSize = 14f
            setTextColor(Color.parseColor("#486581"))
            setLineSpacing(0f, 1.18f)
        }
        card.addView(missingText)

        val guide = TextView(this).apply {
            text =
                "\n※ 현재 버전은 00:00부터 현재 시각까지 60분 단위로 계산합니다.\n" +
                "※ 다음 단계에서 실제 근무시간/교대시간을 설정값으로 연결할 수 있습니다."
            textSize = 12f
            setTextColor(Color.parseColor("#829AB1"))
        }
        card.addView(guide)

        rootContent.addView(card)
    }

    /*
     * =========================================================
     * 최근 Quality Score 추이
     * =========================================================
     *
     * 외부 Chart Library 없이 Android Canvas만 사용합니다.
     * 현재 선택한 기간 / Model / Line 조건에서 최근 30건을 표시합니다.
     */
    private fun addTrendSummary(
        records:
        List<
            InspectionHistoryStore
                .InspectionRecord
            >
    ) {

        rootContent.addView(
            sectionTitle(
                "최근 Quality Score 추이"
            )
        )

        val recent =
            records
                .sortedBy {
                    it.id
                }
                .takeLast(
                    30
                )

        val card =
            createCard()

        if (
            recent.isEmpty()
        ) {

            card.addView(
                TextView(
                    this
                ).apply {

                    text =
                        "현재 조건에 표시할 검사 데이터가 없습니다."

                    textSize =
                        14f

                    setTextColor(
                        Color.parseColor(
                            "#829AB1"
                        )
                    )
                }
            )

            rootContent.addView(
                card
            )

            return
        }

        val latest =
            recent.last()

        val average =
            recent
                .map {
                    it.score
                }
                .average()

        val previousAverage =
            if (
                recent.size >=
                6
            ) {

                recent
                    .dropLast(
                        minOf(
                            5,
                            recent.size
                        )
                    )
                    .takeLast(
                        5
                    )
                    .map {
                        it.score
                    }
                    .average()

            } else {

                average
            }

        val latestAverage =
            recent
                .takeLast(
                    minOf(
                        5,
                        recent.size
                    )
                )
                .map {
                    it.score
                }
                .average()

        val delta =
            latestAverage -
                previousAverage

        val trendText =
            when {

                delta >=
                    3.0 ->
                    "최근 Score 상승"

                delta <=
                    -3.0 ->
                    "최근 Score 하락"

                else ->
                    "최근 Score 안정"
            }

        card.addView(
            TextView(
                this
            ).apply {

                text =
                    String.format(
                        Locale.getDefault(),
                        "최근 %d건  |  평균 %.1f  |  최신 %.1f / %s",
                        recent.size,
                        average,
                        latest.score,
                        latest.judgment
                    )

                textSize =
                    14f

                setTextColor(
                    Color.parseColor(
                        "#486581"
                    )
                )
            }
        )

        card.addView(
            ScoreTrendView(
                this
            ).apply {

                setRecords(
                    recent
                )
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(190)
            ).apply {

                topMargin =
                    dp(10)

                bottomMargin =
                    dp(8)
            }
        )

        card.addView(
            TextView(
                this
            ).apply {

                text =
                    String.format(
                        Locale.getDefault(),
                        "%s  |  최근 5건 평균 변화 %+,.1f점",
                        trendText,
                        delta
                    )

                textSize =
                    14f

                setTypeface(
                    null,
                    Typeface.BOLD
                )

                setTextColor(
                    when {

                        delta <=
                            -3.0 ->
                            Color.parseColor(
                                "#C62828"
                            )

                        delta >=
                            3.0 ->
                            Color.parseColor(
                                "#2E7D32"
                            )

                        else ->
                            Color.parseColor(
                                "#486581"
                            )
                    }
                )
            }
        )

        rootContent.addView(
            card
        )
    }

    /*
     * =========================================================
     * 판정 현황
     * =========================================================
     */

    private fun addJudgmentSummary(
        records:
        List<
            InspectionHistoryStore
                .InspectionRecord
            >
    ) {

        rootContent.addView(
            sectionTitle(
                "판정 현황"
            )
        )

        var normal =
            0

        var warning =
            0

        var limit =
            0

        var ng =
            0

        records.forEach { record ->

            when {

                record.judgment.contains(
                    "불량"
                ) -> {
                    ng++
                }

                record.judgment.contains(
                    "한계"
                ) -> {
                    limit++
                }

                record.judgment.contains(
                    "주의"
                ) -> {
                    warning++
                }

                record.judgment.contains(
                    "정상"
                ) -> {
                    normal++
                }
            }
        }

        val row1 =
            LinearLayout(
                this
            ).apply {

                orientation =
                    LinearLayout.HORIZONTAL
            }

        row1.addView(
            countCard(
                "정상",
                normal,
                "#2E7D32"
            )
        )

        row1.addView(
            countCard(
                "주의",
                warning,
                "#D89000"
            )
        )

        val row2 =
            LinearLayout(
                this
            ).apply {

                orientation =
                    LinearLayout.HORIZONTAL
            }

        row2.addView(
            countCard(
                "한계정상",
                limit,
                "#EF6C00"
            )
        )

        row2.addView(
            countCard(
                "불량",
                ng,
                "#C62828"
            )
        )

        rootContent.addView(
            row1
        )

        rootContent.addView(
            row2
        )
    }

    /*
     * =========================================================
     * 우선 확인 항목
     * =========================================================
     */

    private fun addAttentionSummary(
        records:
        List<
            InspectionHistoryStore
                .InspectionRecord
            >
    ) {

        rootContent.addView(
            sectionTitle(
                "우선 확인 항목"
            )
        )

        val summaries =
            inspectionOrder.mapNotNull { type ->

                val itemRecords =
                    records.filter {

                        it.inspectionType.equals(
                            type,
                            ignoreCase = true
                        )
                    }

                if (
                    itemRecords.isEmpty()
                ) {

                    null

                } else {

                    val issueCount =
                        itemRecords.count {

                            judgmentSeverity(
                                it.judgment
                            ) >=
                                2
                        }

                    val issueRate =
                        issueCount.toDouble() /
                            itemRecords.size.toDouble() *
                            100.0

                    ItemSummary(
                        type = type,
                        count = itemRecords.size,
                        averageScore =
                            itemRecords
                                .map {
                                    it.score
                                }
                                .average(),
                        issueCount =
                            issueCount,
                        issueRate =
                            issueRate
                    )
                }
            }

        val attention =
            summaries
                .sortedWith(
                    compareByDescending<ItemSummary> {
                        it.issueRate
                    }
                        .thenBy {
                            it.averageScore
                        }
                )
                .firstOrNull()

        val card =
            createCard()

        card.addView(
            TextView(
                this
            ).apply {

                if (
                    attention ==
                    null
                ) {

                    text =
                        "현재 조건에 검사 데이터가 없습니다."

                    setTextColor(
                        Color.parseColor(
                            "#627D98"
                        )
                    )

                } else {

                    text =
                        String.format(
                            Locale.getDefault(),
                            """
%s

검사 %d건
평균 Score %.1f / 100
주의 이상 %d건 (%.1f%%)

※ 발생률이 높은 항목을 우선 표시합니다.
                            """.trimIndent(),

                            displayTypeName(
                                attention.type
                            ),
                            attention.count,
                            attention.averageScore,
                            attention.issueCount,
                            attention.issueRate
                        )

                    setTextColor(
                        if (
                            attention.issueCount >
                            0
                        ) {

                            Color.parseColor(
                                "#C46A00"
                            )

                        } else {

                            Color.parseColor(
                                "#2E7D32"
                            )
                        }
                    )
                }

                textSize =
                    17f

                setTypeface(
                    null,
                    Typeface.BOLD
                )

                setLineSpacing(
                    0f,
                    1.15f
                )
            }
        )

        rootContent.addView(
            card
        )
    }

    /*
     * =========================================================
     * 최근 이상 발생
     * =========================================================
     */
    private fun addRecentIssueSummary(
        records:
        List<
            InspectionHistoryStore
                .InspectionRecord
            >
    ) {

        rootContent.addView(
            sectionTitle(
                "최근 이상 발생"
            )
        )

        val issues =
            records
                .filter {

                    judgmentSeverity(
                        it.judgment
                    ) >=
                        2
                }
                .sortedByDescending {
                    it.id
                }
                .take(
                    5
                )

        val card =
            createCard()

        if (
            issues.isEmpty()
        ) {

            card.addView(
                TextView(
                    this
                ).apply {

                    text =
                        "현재 조회 조건에서 주의 이상 발생 이력이 없습니다."

                    textSize =
                        14f

                    setTextColor(
                        Color.parseColor(
                            "#2E7D32"
                        )
                    )
                }
            )

            rootContent.addView(
                card
            )

            return
        }

        issues.forEachIndexed {
                index,
                record ->

            card.addView(
                TextView(
                    this
                ).apply {

                    text =
                        String.format(
                            Locale.getDefault(),
                            "%d. %s  |  %.1f점  |  %s\n%s  ·  %s  ·  %s",
                            index +
                                1,
                            displayTypeName(
                                record.inspectionType
                            ),
                            record.score,
                            record.judgment,
                            record.dateTime,
                            record.model.ifBlank {
                                "-"
                            },
                            record.line.ifBlank {
                                "-"
                            }
                        )

                    textSize =
                        14f

                    setTextColor(
                        if (
                            record.judgment.contains(
                                "불량"
                            )
                        ) {

                            Color.parseColor(
                                "#C62828"
                            )

                        } else {

                            Color.parseColor(
                                "#C46A00"
                            )
                        }
                    )

                    setPadding(
                        0,
                        if (
                            index ==
                            0
                        ) {
                            0
                        } else {
                            dp(10)
                        },
                        0,
                        dp(8)
                    )
                }
            )
        }

        rootContent.addView(
            card
        )
    }

    /*
     * =========================================================
     * 검사 항목별 현황
     * =========================================================
     */

    private fun addInspectionItemSummary(
        records:
        List<
            InspectionHistoryStore
                .InspectionRecord
            >
    ) {

        rootContent.addView(
            sectionTitle(
                "검사 항목별 현황"
            )
        )

        inspectionOrder.forEach { type ->

            val itemRecords =
                records.filter {

                    it.inspectionType.equals(
                        type,
                        ignoreCase = true
                    )
                }

            val card =
                createCard()

            card.addView(
                TextView(
                    this
                ).apply {

                    text =
                        displayTypeName(
                            type
                        )

                    textSize =
                        17f

                    setTypeface(
                        null,
                        Typeface.BOLD
                    )

                    setTextColor(
                        Color.parseColor(
                            "#102A43"
                        )
                    )
                }
            )

            if (
                itemRecords.isEmpty()
            ) {

                card.addView(
                    TextView(
                        this
                    ).apply {

                        text =
                            "검사 데이터 없음"

                        textSize =
                            14f

                        setTextColor(
                            Color.parseColor(
                                "#829AB1"
                            )
                        )

                        setPadding(
                            0,
                            dp(8),
                            0,
                            0
                        )
                    }
                )

                rootContent.addView(
                    card
                )

                return@forEach
            }

            val average =
                itemRecords
                    .map {
                        it.score
                    }
                    .average()

            val issueCount =
                itemRecords.count {

                    judgmentSeverity(
                        it.judgment
                    ) >=
                        2
                }

            val issueRate =
                issueCount.toDouble() /
                    itemRecords.size.toDouble() *
                    100.0

            val latestRecord =
                itemRecords.maxByOrNull {
                    it.id
                }

            card.addView(
                TextView(
                    this
                ).apply {

                    text =
                        String.format(
                            Locale.getDefault(),
                            "검사 %d건  |  평균 %.1f  |  주의 이상 %d건 (%.1f%%)\n최신 : %.1f점 / %s",
                            itemRecords.size,
                            average,
                            issueCount,
                            issueRate,
                            latestRecord?.score ?: 0.0,
                            latestRecord?.judgment ?: "-"
                        )

                    textSize =
                        14f

                    setTextColor(
                        Color.parseColor(
                            "#486581"
                        )
                    )

                    setPadding(
                        0,
                        dp(6),
                        0,
                        dp(6)
                    )
                }
            )

            val progress =
                ProgressBar(
                    this,
                    null,
                    android.R.attr.progressBarStyleHorizontal
                ).apply {

                    max =
                        100

                    this.progress =
                        average
                            .roundToInt()
                            .coerceIn(
                                0,
                                100
                            )

                    progressTintList =
                        ColorStateList.valueOf(
                            scoreColor(
                                average
                            )
                        )

                    progressBackgroundTintList =
                        ColorStateList.valueOf(
                            Color.parseColor(
                                "#D9E2EC"
                            )
                        )
                }

            card.addView(
                progress,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(10)
                )
            )

            rootContent.addView(
                card
            )
        }
    }

    /*
     * =========================================================
     * 하단 버튼
     * =========================================================
     */

    private fun addActionButtons() {

        rootContent.addView(
            actionButton(
                "Dashboard 새로고침"
            ) {

                renderDashboard()
            }
        )

        rootContent.addView(
            actionButton(
                "검사 이력 / Trend / CSV 보기"
            ) {

                startActivity(
                    Intent(
                        this,
                        HistoryActivity::class.java
                    )
                )
            }
        )

        rootContent.addView(
            actionButton(
                "메뉴로 돌아가기"
            ) {

                finish()
            }
        )
    }

    /*
     * =========================================================
     * 기간 계산
     * =========================================================
     */

    private fun periodStartTime(
        period: Period
    ): Long {

        val calendar =
            Calendar.getInstance()

        return when (
            period
        ) {

            Period.TODAY -> {

                calendar.set(
                    Calendar.HOUR_OF_DAY,
                    0
                )

                calendar.set(
                    Calendar.MINUTE,
                    0
                )

                calendar.set(
                    Calendar.SECOND,
                    0
                )

                calendar.set(
                    Calendar.MILLISECOND,
                    0
                )

                calendar.timeInMillis
            }

            Period.WEEK -> {

                calendar.set(
                    Calendar.HOUR_OF_DAY,
                    0
                )

                calendar.set(
                    Calendar.MINUTE,
                    0
                )

                calendar.set(
                    Calendar.SECOND,
                    0
                )

                calendar.set(
                    Calendar.MILLISECOND,
                    0
                )

                calendar.add(
                    Calendar.DAY_OF_YEAR,
                    -6
                )

                calendar.timeInMillis
            }

            Period.MONTH -> {

                calendar.set(
                    Calendar.DAY_OF_MONTH,
                    1
                )

                calendar.set(
                    Calendar.HOUR_OF_DAY,
                    0
                )

                calendar.set(
                    Calendar.MINUTE,
                    0
                )

                calendar.set(
                    Calendar.SECOND,
                    0
                )

                calendar.set(
                    Calendar.MILLISECOND,
                    0
                )

                calendar.timeInMillis
            }
        }
    }

    /*
     * =========================================================
     * 판정 Severity
     *
     * 1 정상
     * 2 주의
     * 3 한계정상
     * 4 불량
     * =========================================================
     */

    private fun judgmentSeverity(
        judgment: String
    ): Int {

        return when {

            judgment.contains(
                "불량"
            ) -> {
                4
            }

            judgment.contains(
                "한계"
            ) -> {
                3
            }

            judgment.contains(
                "주의"
            ) -> {
                2
            }

            judgment.contains(
                "정상"
            ) -> {
                1
            }

            else -> {
                0
            }
        }
    }

    private fun displayTypeName(
        type: String
    ): String {

        return when {

            type.equals(
                TYPE_BOTTOM,
                ignoreCase = true
            ) -> {
                "BOTTOM CORNER"
            }

            type.equals(
                TYPE_SEAL,
                ignoreCase = true
            ) -> {
                "SEAL"
            }

            type.equals(
                TYPE_FORMING,
                ignoreCase = true
            ) -> {
                "FORMING"
            }

            type.equals(
                TYPE_TAB,
                ignoreCase = true
            ) -> {
                "TAB"
            }

            type.equals(
                TYPE_DISASSEMBLY,
                ignoreCase = true
            ) -> {
                "분해검사"
            }

            else -> {
                type
            }
        }
    }

    /*
     * =========================================================
     * 공용 UI
     * =========================================================
     */

    /*
     * =========================================================
     * Spinner 가시성 고정 Adapter
     * =========================================================
     *
     * Samsung / Android 다크모드 또는 제조사 테마와 무관하게
     * Model / Line 선택값과 드롭다운 목록을 항상
     * 진한 글씨 + 밝은 배경으로 표시합니다.
     * =========================================================
     */

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

                styleSpinnerView(
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

                styleSpinnerView(
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

    private fun styleSpinnerView(
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
                dp(14),
                0,
                dp(14),
                0
            )

            view.setBackgroundColor(
                Color.parseColor(
                    if (
                        isDropDown
                    ) {
                        "#FFFFFF"
                    } else {
                        "#F4F6F8"
                    }
                )
            )
        }
    }

    private fun sectionTitle(
        title: String
    ): TextView {

        return TextView(
            this
        ).apply {

            text =
                title

            textSize =
                19f

            setTypeface(
                null,
                Typeface.BOLD
            )

            setTextColor(
                Color.parseColor(
                    "#102A43"
                )
            )

            setPadding(
                dp(2),
                dp(14),
                dp(2),
                dp(8)
            )
        }
    }

    private fun labelText(
        value: String
    ): TextView {

        return TextView(
            this
        ).apply {

            text =
                value

            textSize =
                14f

            setTypeface(
                null,
                Typeface.BOLD
            )

            setTextColor(
                Color.parseColor(
                    "#334E68"
                )
            )
        }
    }

    private fun createCard():
        LinearLayout {

        return LinearLayout(
            this
        ).apply {

            orientation =
                LinearLayout.VERTICAL

            setPadding(
                dp(16),
                dp(16),
                dp(16),
                dp(16)
            )

            background =
                roundedBackground(
                    "#FFFFFF",
                    12f
                )

            layoutParams =
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {

                    bottomMargin =
                        dp(10)
                }
        }
    }

    private fun countCard(
        title: String,
        count: Int,
        colorText: String
    ): View {

        val card =
            LinearLayout(
                this
            ).apply {

                orientation =
                    LinearLayout.VERTICAL

                gravity =
                    Gravity.CENTER

                setPadding(
                    dp(10),
                    dp(14),
                    dp(10),
                    dp(14)
                )

                background =
                    roundedBackground(
                        "#FFFFFF",
                        10f
                    )

                layoutParams =
                    LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    ).apply {

                        setMargins(
                            dp(4),
                            dp(4),
                            dp(4),
                            dp(4)
                        )
                    }
            }

        card.addView(
            TextView(
                this
            ).apply {

                text =
                    title

                textSize =
                    14f

                setTextColor(
                    Color.parseColor(
                        "#486581"
                    )
                )
            }
        )

        card.addView(
            TextView(
                this
            ).apply {

                text =
                    count.toString()

                textSize =
                    24f

                setTypeface(
                    null,
                    Typeface.BOLD
                )

                setTextColor(
                    Color.parseColor(
                        colorText
                    )
                )
            }
        )

        return card
    }

    private fun periodButton(
        textValue: String,
        onClick: () -> Unit
    ): Button {

        return Button(
            this
        ).apply {

            text =
                textValue

            textSize =
                13f

            isAllCaps =
                false

            setTextColor(
                Color.WHITE
            )

            backgroundTintList =
                ColorStateList.valueOf(
                    Color.parseColor(
                        "#335C81"
                    )
                )

            setOnClickListener {
                onClick()
            }

            layoutParams =
                LinearLayout.LayoutParams(
                    0,
                    dp(48),
                    1f
                ).apply {

                    setMargins(
                        dp(3),
                        0,
                        dp(3),
                        0
                    )
                }
        }
    }

    private fun actionButton(
        textValue: String,
        onClick: () -> Unit
    ): Button {

        return Button(
            this
        ).apply {

            text =
                textValue

            textSize =
                15f

            isAllCaps =
                false

            setTextColor(
                Color.WHITE
            )

            backgroundTintList =
                ColorStateList.valueOf(
                    Color.parseColor(
                        "#102F4A"
                    )
                )

            setOnClickListener {
                onClick()
            }

            layoutParams =
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(56)
                ).apply {

                    topMargin =
                        dp(6)
                }
        }
    }

    private fun scoreColor(
        score: Double
    ): Int {

        return when {

            score >=
                80.0 -> {

                Color.parseColor(
                    "#2E7D32"
                )
            }

            score >=
                60.0 -> {

                Color.parseColor(
                    "#D89000"
                )
            }

            score >=
                40.0 -> {

                Color.parseColor(
                    "#EF6C00"
                )
            }

            else -> {

                Color.parseColor(
                    "#C62828"
                )
            }
        }
    }

    private fun roundedBackground(
        colorText: String,
        radiusDp: Float
    ): GradientDrawable {

        return GradientDrawable().apply {

            shape =
                GradientDrawable.RECTANGLE

            setColor(
                Color.parseColor(
                    colorText
                )
            )

            cornerRadius =
                dp(
                    radiusDp
                        .roundToInt()
                )
                    .toFloat()
        }
    }

    private fun fullWidthParams(
        top: Int,
        bottom: Int
    ): LinearLayout.LayoutParams {

        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(52)
        ).apply {

            topMargin =
                dp(top)

            bottomMargin =
                dp(bottom)
        }
    }

    /*
     * =========================================================
     * 간단한 Score Trend View
     * =========================================================
     */
    private class ScoreTrendView(
        context: Context
    ) : View(
        context
    ) {

        private var records:
            List<
                InspectionHistoryStore
                    .InspectionRecord
                > =
            emptyList()

        private val gridPaint =
            Paint(
                Paint.ANTI_ALIAS_FLAG
            ).apply {

                color =
                    Color.parseColor(
                        "#D9E2EC"
                    )

                strokeWidth =
                    1f
            }

        private val linePaint =
            Paint(
                Paint.ANTI_ALIAS_FLAG
            ).apply {

                color =
                    Color.parseColor(
                        "#335C81"
                    )

                strokeWidth =
                    5f

                style =
                    Paint.Style.STROKE

                strokeJoin =
                    Paint.Join.ROUND

                strokeCap =
                    Paint.Cap.ROUND
            }

        private val pointPaint =
            Paint(
                Paint.ANTI_ALIAS_FLAG
            ).apply {

                color =
                    Color.parseColor(
                        "#102F4A"
                    )

                style =
                    Paint.Style.FILL
            }

        private val textPaint =
            Paint(
                Paint.ANTI_ALIAS_FLAG
            ).apply {

                color =
                    Color.parseColor(
                        "#627D98"
                    )

                textSize =
                    28f
            }

        fun setRecords(
            source:
            List<
                InspectionHistoryStore
                    .InspectionRecord
                >
        ) {

            records =
                source

            invalidate()
        }

        override fun onDraw(
            canvas: Canvas
        ) {

            super.onDraw(
                canvas
            )

            if (
                records.isEmpty()
            ) {

                return
            }

            val left =
                70f

            val right =
                width.toFloat() -
                    20f

            val top =
                18f

            val bottom =
                height.toFloat() -
                    42f

            val chartWidth =
                (
                    right -
                        left
                    )
                    .coerceAtLeast(
                        1f
                    )

            val chartHeight =
                (
                    bottom -
                        top
                    )
                    .coerceAtLeast(
                        1f
                    )

            listOf(
                100f,
                75f,
                50f,
                25f,
                0f
            ).forEach { score ->

                val y =
                    top +
                        (
                            100f -
                                score
                            ) /
                        100f *
                        chartHeight

                canvas.drawLine(
                    left,
                    y,
                    right,
                    y,
                    gridPaint
                )

                canvas.drawText(
                    score
                        .toInt()
                        .toString(),
                    6f,
                    y +
                        9f,
                    textPaint
                )
            }

            val path =
                Path()

            records.forEachIndexed {
                    index,
                    record ->

                val x =
                    if (
                        records.size ==
                        1
                    ) {

                        left +
                            chartWidth /
                            2f

                    } else {

                        left +
                            index.toFloat() /
                            (
                                records.size -
                                    1
                                ).toFloat() *
                            chartWidth
                    }

                val score =
                    record.score
                        .toFloat()
                        .coerceIn(
                            0f,
                            100f
                        )

                val y =
                    top +
                        (
                            100f -
                                score
                            ) /
                        100f *
                        chartHeight

                if (
                    index ==
                    0
                ) {

                    path.moveTo(
                        x,
                        y
                    )

                } else {

                    path.lineTo(
                        x,
                        y
                    )
                }
            }

            canvas.drawPath(
                path,
                linePaint
            )

            records.forEachIndexed {
                    index,
                    record ->

                val x =
                    if (
                        records.size ==
                        1
                    ) {

                        left +
                            chartWidth /
                            2f

                    } else {

                        left +
                            index.toFloat() /
                            (
                                records.size -
                                    1
                                ).toFloat() *
                            chartWidth
                    }

                val score =
                    record.score
                        .toFloat()
                        .coerceIn(
                            0f,
                            100f
                        )

                val y =
                    top +
                        (
                            100f -
                                score
                            ) /
                        100f *
                        chartHeight

                canvas.drawCircle(
                    x,
                    y,
                    6f,
                    pointPaint
                )
            }
        }
    }

    private fun dp(
        value: Int
    ): Int {

        return (
            value *
                resources.displayMetrics.density
            )
            .roundToInt()
    }
}
