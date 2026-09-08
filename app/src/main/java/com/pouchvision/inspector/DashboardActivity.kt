package com.pouchvision.inspector

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class DashboardActivity : AppCompatActivity() {

    private lateinit var rootContent: LinearLayout
    private lateinit var periodStatusText: TextView

    private var selectedPeriod =
        Period.TODAY

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
    }

    private val inspectionOrder =
        listOf(
            TYPE_BOTTOM,
            TYPE_SEAL,
            TYPE_FORMING,
            TYPE_TAB,
            TYPE_DISASSEMBLY
        )

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(
            savedInstanceState
        )

        setContentView(
            createScreen()
        )

        renderDashboard()
    }

    override fun onResume() {

        super.onResume()

        if (
            ::rootContent.isInitialized
        ) {

            renderDashboard()
        }
    }

    /*
     * =========================================================
     * 전체 화면 생성
     *
     * XML 파일을 추가하지 않고 Kotlin 코드만으로 구성합니다.
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
         * 상단 Header
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

        val title =
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

        val subtitle =
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

        header.addView(
            title
        )

        header.addView(
            subtitle
        )

        outer.addView(
            header
        )

        /*
         * 본문
         */
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
         * 기간 버튼
         */
        val periodTitle =
            sectionTitle(
                "조회 기간"
            )

        rootContent.addView(
            periodTitle
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

        val btnToday =
            periodButton(
                "오늘"
            ) {

                selectedPeriod =
                    Period.TODAY

                renderDashboard()
            }

        val btnWeek =
            periodButton(
                "최근 7일"
            ) {

                selectedPeriod =
                    Period.WEEK

                renderDashboard()
            }

        val btnMonth =
            periodButton(
                "이번 달"
            ) {

                selectedPeriod =
                    Period.MONTH

                renderDashboard()
            }

        periodRow.addView(
            btnToday
        )

        periodRow.addView(
            btnWeek
        )

        periodRow.addView(
            btnMonth
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
                    dp(6)
                )
            }

        rootContent.addView(
            periodStatusText
        )

        /*
         * Dashboard 내용은 이 아래부터 renderDashboard()가 재생성합니다.
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
     * Dashboard 갱신
     * =========================================================
     */

    private fun renderDashboard() {

        /*
         * periodStatusText 이전까지는 고정 UI이므로
         * 그 아래 View만 삭제합니다.
         */
        while (
            rootContent.childCount >
            3
        ) {

            rootContent.removeViewAt(
                3
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

        val periodRecords =
            allRecords.filter {

                it.id >=
                    startTime
            }

        /*
         * TOTAL SESSION은 개별 검사 건수/Score 계산에서 제외합니다.
         */
        val inspectionRecords =
            periodRecords.filter {

                !it.inspectionType.equals(
                    TYPE_TOTAL_SESSION,
                    ignoreCase = true
                ) &&
                    inspectionOrder.any { type ->

                        it.inspectionType.equals(
                            type,
                            ignoreCase = true
                        )
                    }
            }

        val totalSessions =
            periodRecords.count {

                it.inspectionType.equals(
                    TYPE_TOTAL_SESSION,
                    ignoreCase = true
                )
            }

        periodStatusText.text =
            buildPeriodCaption(
                selectedPeriod
            )

        /*
         * 데이터가 없어도 0건 Dashboard를 보여줍니다.
         */
        addOverallSummary(
            records = inspectionRecords,
            totalSessions = totalSessions
        )

        addJudgmentSummary(
            inspectionRecords
        )

        addAttentionSummary(
            inspectionRecords
        )

        addInspectionItemSummary(
            inspectionRecords
        )

        addActionButtons()
    }

    /*
     * =========================================================
     * 전체 요약
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
            records
                .minByOrNull {
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

                issueCount
                    .toDouble() /
                    count
                        .toDouble() *
                    100.0

            } else {

                0.0
            }

        val card =
            createCard()

        val headline =
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

        card.addView(
            headline
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

        val progressParams =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(12)
            ).apply {

                topMargin =
                    dp(10)

                bottomMargin =
                    dp(12)
            }

        progress.layoutParams =
            progressParams

        card.addView(
            progress
        )

        val summary =
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

        card.addView(
            summary
        )

        rootContent.addView(
            card
        )
    }

    /*
     * =========================================================
     * 정상 / 주의 / 한계 / 불량 건수
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

        for (
            record in records
        ) {

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
                title = "정상",
                count = normal,
                textColor = Color.parseColor(
                    "#2E7D32"
                )
            )
        )

        row1.addView(
            countCard(
                title = "주의",
                count = warning,
                textColor = Color.parseColor(
                    "#D89000"
                )
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
                title = "한계정상",
                count = limit,
                textColor = Color.parseColor(
                    "#EF6C00"
                )
            )
        )

        row2.addView(
            countCard(
                title = "불량",
                count = ng,
                textColor = Color.parseColor(
                    "#C62828"
                )
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
     * 가장 주의가 필요한 항목
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
                        issueCount
                            .toDouble() /
                            itemRecords.size
                                .toDouble() *
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

        val text =
            TextView(
                this
            ).apply {

                if (
                    attention ==
                    null
                ) {

                    this.text =
                        "현재 기간에 검사 데이터가 없습니다."

                    setTextColor(
                        Color.parseColor(
                            "#627D98"
                        )
                    )

                } else {

                    this.text =
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

        card.addView(
            text
        )

        rootContent.addView(
            card
        )
    }

    /*
     * =========================================================
     * 항목별 현황
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

        for (
            type in inspectionOrder
        ) {

            val itemRecords =
                records.filter {

                    it.inspectionType.equals(
                        type,
                        ignoreCase = true
                    )
                }

            val card =
                createCard()

            val title =
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

            card.addView(
                title
            )

            if (
                itemRecords.isEmpty()
            ) {

                val empty =
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

                card.addView(
                    empty
                )

                rootContent.addView(
                    card
                )

                continue
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
                issueCount
                    .toDouble() /
                    itemRecords.size
                        .toDouble() *
                    100.0

            val info =
                TextView(
                    this
                ).apply {

                    text =
                        String.format(
                            Locale.getDefault(),
                            "검사 %d건  |  평균 %.1f  |  주의 이상 %d건 (%.1f%%)",
                            itemRecords.size,
                            average,
                            issueCount,
                            issueRate
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

            card.addView(
                info
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

            progress.layoutParams =
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(10)
                )

            card.addView(
                progress
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

        val refresh =
            actionButton(
                "Dashboard 새로고침"
            ) {

                renderDashboard()
            }

        val history =
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

        val back =
            actionButton(
                "메뉴로 돌아가기"
            ) {

                finish()
            }

        rootContent.addView(
            refresh
        )

        rootContent.addView(
            history
        )

        rootContent.addView(
            back
        )

        val note =
            TextView(
                this
            ).apply {

                text =
                    """
※ Dashboard는 저장된 영상 검사 결과를 요약한 관리용 화면입니다.
※ 현재 판정 알고리즘은 검사 보조 단계이며 실제 양산 OK/NG 기준과는 별도로 검증이 필요합니다.
                    """.trimIndent()

                textSize =
                    12f

                setTextColor(
                    Color.parseColor(
                        "#829AB1"
                    )
                )

                setPadding(
                    dp(2),
                    dp(12),
                    dp(2),
                    dp(4)
                )
            }

        rootContent.addView(
            note
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

    private fun buildPeriodCaption(
        period: Period
    ): String {

        val dateFormat =
            SimpleDateFormat(
                "yyyy-MM-dd",
                Locale.getDefault()
            )

        val start =
            dateFormat.format(
                Date(
                    periodStartTime(
                        period
                    )
                )
            )

        val today =
            dateFormat.format(
                Date()
            )

        return when (
            period
        ) {

            Period.TODAY ->
                "일간 : $today"

            Period.WEEK ->
                "주간 : $start ~ $today"

            Period.MONTH ->
                "월간 : $start ~ $today"
        }
    }

    /*
     * =========================================================
     * 판정 Severity
     * =========================================================
     */

    private fun judgmentSeverity(
        judgment: String
    ): Int {

        return when {

            judgment.contains(
                "불량"
            ) ->
                4

            judgment.contains(
                "한계"
            ) ->
                3

            judgment.contains(
                "주의"
            ) ->
                2

            judgment.contains(
                "정상"
            ) ->
                1

            else ->
                0
        }
    }

    /*
     * =========================================================
     * UI Helper
     * =========================================================
     */

    private fun sectionTitle(
        text: String
    ): TextView {

        return TextView(
            this
        ).apply {

            this.text =
                text

            textSize =
                20f

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
                dp(10)
            )
        }
    }

    private fun createCard():
        LinearLayout {

        val card =
            LinearLayout(
                this
            ).apply {

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    dp(16),
                    dp(15),
                    dp(16),
                    dp(15)
                )

                background =
                    roundedBackground(
                        fillColor =
                            Color.WHITE,
                        strokeColor =
                            Color.parseColor(
                                "#D9E2EC"
                            ),
                        radiusDp =
                            10
                    )
            }

        val params =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )

        params.bottomMargin =
            dp(10)

        card.layoutParams =
            params

        return card
    }

    private fun countCard(
        title: String,
        count: Int,
        textColor: Int
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
                    dp(8),
                    dp(14),
                    dp(8),
                    dp(14)
                )

                background =
                    roundedBackground(
                        fillColor =
                            Color.WHITE,
                        strokeColor =
                            Color.parseColor(
                                "#D9E2EC"
                            ),
                        radiusDp =
                            9
                    )
            }

        val params =
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )

        params.setMargins(
            dp(4),
            dp(4),
            dp(4),
            dp(4)
        )

        card.layoutParams =
            params

        val countText =
            TextView(
                this
            ).apply {

                text =
                    count.toString()

                textSize =
                    28f

                setTypeface(
                    null,
                    Typeface.BOLD
                )

                setTextColor(
                    textColor
                )

                gravity =
                    Gravity.CENTER
            }

        val titleText =
            TextView(
                this
            ).apply {

                text =
                    title

                textSize =
                    13f

                setTextColor(
                    Color.parseColor(
                        "#486581"
                    )
                )

                gravity =
                    Gravity.CENTER
            }

        card.addView(
            countText
        )

        card.addView(
            titleText
        )

        return card
    }

    private fun periodButton(
        title: String,
        click: () -> Unit
    ): Button {

        val button =
            Button(
                this
            ).apply {

                text =
                    title

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
                            "#4E7697"
                        )
                    )

                setOnClickListener {

                    click()
                }
            }

        val params =
            LinearLayout.LayoutParams(
                0,
                dp(50),
                1f
            )

        params.setMargins(
            dp(4),
            0,
            dp(4),
            0
        )

        button.layoutParams =
            params

        return button
    }

    private fun actionButton(
        title: String,
        click: () -> Unit
    ): Button {

        val button =
            Button(
                this
            ).apply {

                text =
                    title

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

                    click()
                }
            }

        val params =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(54)
            )

        params.topMargin =
            dp(8)

        button.layoutParams =
            params

        return button
    }

    private fun roundedBackground(
        fillColor: Int,
        strokeColor: Int,
        radiusDp: Int
    ): GradientDrawable {

        return GradientDrawable().apply {

            shape =
                GradientDrawable.RECTANGLE

            setColor(
                fillColor
            )

            setStroke(
                dp(1),
                strokeColor
            )

            cornerRadius =
                dp(
                    radiusDp
                )
                    .toFloat()
        }
    }

    private fun scoreColor(
        score: Double
    ): Int {

        return when {

            score >=
                85.0 ->

                Color.parseColor(
                    "#2E7D32"
                )

            score >=
                70.0 ->

                Color.parseColor(
                    "#D89000"
                )

            score >=
                50.0 ->

                Color.parseColor(
                    "#EF6C00"
                )

            else ->

                Color.parseColor(
                    "#C62828"
                )
        }
    }

    private fun displayTypeName(
        type: String
    ): String {

        return when {

            type.equals(
                TYPE_BOTTOM,
                ignoreCase = true
            ) ->
                "Bottom Corner"

            type.equals(
                TYPE_SEAL,
                ignoreCase = true
            ) ->
                "Seal"

            type.equals(
                TYPE_FORMING,
                ignoreCase = true
            ) ->
                "Forming"

            type.equals(
                TYPE_TAB,
                ignoreCase = true
            ) ->
                "Tab"

            type.equals(
                TYPE_DISASSEMBLY,
                ignoreCase = true
            ) ->
                "분해검사"

            else ->
                type
        }
    }

    private fun dp(
        value: Int
    ): Int {

        return (
            value *
                resources
                    .displayMetrics
                    .density
            )
            .roundToInt()
    }

    private data class ItemSummary(

        val type: String,

        val count: Int,

        val averageScore: Double,

        val issueCount: Int,

        val issueRate: Double
    )
}
