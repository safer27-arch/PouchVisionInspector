package com.pouchvision.inspector

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.pouchvision.inspector.databinding.ActivityHistoryBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding

    /* CSV 저장 화면을 열기 전에 생성해 둔 내용 */
    private var pendingCsvContent: String? = null

    /*
     * Android 저장 위치 선택 화면.
     * 별도 저장소 권한 없이 사용자가 선택한 위치에 CSV를 저장합니다.
     */
    private val csvCreateLauncher =
        registerForActivityResult(
            ActivityResultContracts.CreateDocument(
                "text/csv"
            )
        ) { uri ->

            if (uri == null) {
                pendingCsvContent = null
                return@registerForActivityResult
            }

            val csvText = pendingCsvContent

            if (csvText == null) {
                Toast.makeText(
                    this,
                    "내보낼 CSV 데이터가 없습니다.",
                    Toast.LENGTH_LONG
                ).show()
                return@registerForActivityResult
            }

            try {
                val outputStream =
                    contentResolver.openOutputStream(uri)
                        ?: throw Exception("파일을 열 수 없습니다.")

                outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                    writer.write("\uFEFF")
                    writer.write(csvText)
                }

                Toast.makeText(
                    this,
                    "CSV 파일 저장 완료",
                    Toast.LENGTH_LONG
                ).show()

            } catch (e: Exception) {
                Toast.makeText(
                    this,
                    "CSV 저장 실패: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                pendingCsvContent = null
            }
        }

    companion object {

        private const val TYPE_TOTAL_SESSION =
            "TOTAL SESSION"

        private const val FILTER_ALL =
            "전체 검사"

        private const val FILTER_TOTAL_SESSION =
            "종합검사 묶음"

        private const val FILTER_ALL_MODELS =
            "전체 Model"

        private const val FILTER_ALL_LINES =
            "전체 Line"
    }

    private val inspectionTypes =
        listOf(
            FILTER_ALL,
            FILTER_TOTAL_SESSION,
            "BOTTOM CORNER",
            "SEAL",
            "FORMING",
            "TAB",
            "DISASSEMBLY"
        )

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(savedInstanceState)

        binding =
            ActivityHistoryBinding.inflate(
                layoutInflater
            )

        setContentView(
            binding.root
        )

        setupFilters()

        binding.btnHistoryRefresh
            .setOnClickListener {

                loadHistory()

                Toast.makeText(
                    this,
                    "검사 이력을 새로고침했습니다.",
                    Toast.LENGTH_SHORT
                ).show()
            }

        binding.btnHistoryBack
            .setOnClickListener {

                finish()
            }

        loadHistory()
    }

    override fun onResume() {

        super.onResume()

        if (
            ::binding.isInitialized
        ) {

            loadHistory()
        }
    }

    /*
     * =========================================================
     * 검사 항목 필터
     * =========================================================
     */

    private fun setupFilters() {

        /*
         * =====================================================
         * 검사 항목
         * =====================================================
         */
        val typeAdapter =
            ArrayAdapter(
                this,
                android.R.layout.simple_spinner_item,
                inspectionTypes
            )

        typeAdapter.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item
        )

        binding.spinnerInspectionType.adapter =
            typeAdapter

        binding.spinnerInspectionType.setSelection(
            0
        )

        /*
         * =====================================================
         * Model
         * =====================================================
         */
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

        val modelAdapter =
            ArrayAdapter(
                this,
                android.R.layout.simple_spinner_item,
                modelItems
            )

        modelAdapter.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item
        )

        binding.spinnerHistoryModel.adapter =
            modelAdapter

        binding.spinnerHistoryModel.setSelection(
            0
        )

        /*
         * 전체 Model 상태에서는 모든 등록 Line을 보여줍니다.
         */
        updateHistoryLineSpinner(
            model = FILTER_ALL_MODELS,
            preferredLine = FILTER_ALL_LINES
        )

        /*
         * =====================================================
         * Listener
         * =====================================================
         */
        binding.spinnerHistoryModel
            .onItemSelectedListener =

            object :
                AdapterView.OnItemSelectedListener {

                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {

                    val selectedModel =
                        binding.spinnerHistoryModel
                            .selectedItem
                            ?.toString()
                            ?: FILTER_ALL_MODELS

                    updateHistoryLineSpinner(
                        model = selectedModel,
                        preferredLine = FILTER_ALL_LINES
                    )

                    loadHistory()
                }

                override fun onNothingSelected(
                    parent: AdapterView<*>?
                ) {
                }
            }

        binding.spinnerHistoryLine
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
     * History용 Model별 Line Spinner 갱신
     * =========================================================
     */

    private fun updateHistoryLineSpinner(
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

        val adapter =
            ArrayAdapter(
                this,
                android.R.layout.simple_spinner_item,
                items
            )

        adapter.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item
        )

        binding.spinnerHistoryLine.adapter =
            adapter

        val preferredPosition =
            items.indexOf(
                preferredLine
            )

        binding.spinnerHistoryLine.setSelection(
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
     * 현재 Model / Line 필터 적용
     * =========================================================
     */

    private fun applyProductionFilter(
        records:
        List<
            InspectionHistoryStore
                .InspectionRecord
            >
    ):
        List<
            InspectionHistoryStore
                .InspectionRecord
            > {

        val selectedModel =
            binding.spinnerHistoryModel
                .selectedItem
                ?.toString()
                ?: FILTER_ALL_MODELS

        val selectedLine =
            binding.spinnerHistoryLine
                .selectedItem
                ?.toString()
                ?: FILTER_ALL_LINES

        return records.filter { record ->

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

            modelMatches &&
                lineMatches
        }
    }

    /*
     * =========================================================
     * 화면 표시용 생산 조건 필터명
     * =========================================================
     */

    private fun currentProductionFilterText(): String {

        val selectedModel =
            binding.spinnerHistoryModel
                .selectedItem
                ?.toString()
                ?: FILTER_ALL_MODELS

        val selectedLine =
            binding.spinnerHistoryLine
                .selectedItem
                ?.toString()
                ?: FILTER_ALL_LINES

        return "$selectedModel  |  $selectedLine"
    }

    /*
     * =========================================================
     * 검사 이력 불러오기
     * =========================================================
     */

    private fun loadHistory() {

        val storedRecords =
            InspectionHistoryStore.load(
                this
            )

        val allRecords =
            applyProductionFilter(
                storedRecords
            )

        val selectedType =
            binding.spinnerInspectionType
                .selectedItem
                ?.toString()
                ?: FILTER_ALL

        /*
         * TOTAL SESSION 안에 들어간 5개 검사 record ID
         *
         * "전체 검사" 화면에서는 이 5개를 다시 개별 카드로
         * 중복 표시하지 않고 종합검사 카드 안에서만 보여줍니다.
         *
         * 특정 검사 필터(BOTTOM CORNER 등)를 선택하면
         * 종합검사에 포함된 결과도 포함해서 모두 볼 수 있습니다.
         */
        val groupedChildIds =
            allRecords
                .filter {

                    it.inspectionType.equals(
                        TYPE_TOTAL_SESSION,
                        ignoreCase = true
                    )
                }
                .flatMap {

                    parseSessionChildIds(
                        it.details
                    )
                }
                .toSet()

        val filteredRecords =
            when (
                selectedType
            ) {

                FILTER_ALL -> {

                    allRecords.filter {

                        it.inspectionType.equals(
                            TYPE_TOTAL_SESSION,
                            ignoreCase = true
                        ) ||
                            it.id !in
                            groupedChildIds
                    }
                }

                FILTER_TOTAL_SESSION -> {

                    allRecords.filter {

                        it.inspectionType.equals(
                            TYPE_TOTAL_SESSION,
                            ignoreCase = true
                        )
                    }
                }

                else -> {

                    allRecords.filter {

                        it.inspectionType.equals(
                            selectedType,
                            ignoreCase = true
                        )
                    }
                }
            }

        updateSummary(
            selectedType = selectedType,
            visibleRecords = filteredRecords,
            allRecords = allRecords
        )

        showHistoryItems(
            records = filteredRecords,
            allRecords = allRecords
        )
    }

    /*
     * =========================================================
     * 검사 요약
     * =========================================================
     */

    private fun updateSummary(
        selectedType: String,
        visibleRecords:
        List<
            InspectionHistoryStore
                .InspectionRecord
            >,
        allRecords:
        List<
            InspectionHistoryStore
                .InspectionRecord
            >
    ) {

        if (
            selectedType ==
            FILTER_TOTAL_SESSION
        ) {

            updateTotalSessionSummary(
                visibleRecords
            )

            return
        }

        val ordinaryRecords =
            visibleRecords.filter {

                !it.inspectionType.equals(
                    TYPE_TOTAL_SESSION,
                    ignoreCase = true
                )
            }

        val totalSessionCount =
            if (
                selectedType ==
                FILTER_ALL
            ) {

                allRecords.count {

                    it.inspectionType.equals(
                        TYPE_TOTAL_SESSION,
                        ignoreCase = true
                    )
                }

            } else {

                0
            }

        var normalCount =
            0

        var warningCount =
            0

        var limitCount =
            0

        var ngCount =
            0

        var imageCount =
            0

        for (
            record in ordinaryRecords
        ) {

            when {

                record.judgment
                    .contains(
                        "불량"
                    ) -> {

                    ngCount++
                }

                record.judgment
                    .contains(
                        "한계"
                    ) -> {

                    limitCount++
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

            if (
                InspectionHistoryStore
                    .getImageFile(
                        record
                    ) != null
            ) {

                imageCount++
            }
        }

        val averageScore =
            if (
                ordinaryRecords.isNotEmpty()
            ) {

                ordinaryRecords
                    .map {

                        it.score
                    }
                    .average()

            } else {

                0.0
            }

        if (
            selectedType ==
            FILTER_ALL
        ) {

            binding.tvHistorySummary.text =
                String.format(
                    Locale.getDefault(),

                    """
현재 필터 : 전체 검사
생산 조건 : %s

종합검사 Session : %d회
단독 검사 결과 : %d건
단독 결과 사진 있음 : %d건

정상 : %d건
주의 : %d건
한계정상 : %d건
불량 : %d건

단독검사 평균 Quality Score : %.1f / 100

※ 종합검사에 포함된 5개 결과는
   중복을 피하기 위해 종합검사 카드 안에서 묶어 표시합니다.
                    """.trimIndent(),

                    currentProductionFilterText(),
                    totalSessionCount,
                    ordinaryRecords.size,
                    imageCount,
                    normalCount,
                    warningCount,
                    limitCount,
                    ngCount,
                    averageScore
                )

        } else {

            binding.tvHistorySummary.text =
                String.format(
                    Locale.getDefault(),

                    """
현재 필터 : %s
생산 조건 : %s
저장된 검사 결과 : %d건
결과 사진 있음 : %d건

정상 : %d건
주의 : %d건
한계정상 : %d건
불량 : %d건

평균 Quality Score : %.1f / 100
                    """.trimIndent(),

                    selectedType,
                    currentProductionFilterText(),
                    ordinaryRecords.size,
                    imageCount,
                    normalCount,
                    warningCount,
                    limitCount,
                    ngCount,
                    averageScore
                )
        }
    }

    /*
     * =========================================================
     * 종합검사 묶음 요약
     * =========================================================
     */

    private fun updateTotalSessionSummary(
        sessions:
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
            record in sessions
        ) {

            when {

                record.judgment
                    .contains(
                        "불량"
                    ) -> {

                    ngCount++
                }

                record.judgment
                    .contains(
                        "한계"
                    ) -> {

                    limitCount++
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
                sessions.isNotEmpty()
            ) {

                sessions
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
현재 필터 : 종합검사 묶음
생산 조건 : %s

종합검사 Session : %d회

종합 정상 : %d회
종합 주의 : %d회
종합 한계정상 : %d회
종합 불량 : %d회

Session 평균 Quality Score : %.1f / 100

각 Session 카드에서
5개 검사 결과와 저장 사진을 함께 확인할 수 있습니다.
                """.trimIndent(),

                currentProductionFilterText(),
                sessions.size,
                normalCount,
                warningCount,
                limitCount,
                ngCount,
                averageScore
            )
    }

    /*
     * =========================================================
     * 이력 카드 출력
     * =========================================================
     */

    private fun showHistoryItems(
        records:
        List<
            InspectionHistoryStore
                .InspectionRecord
            >,
        allRecords:
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

검사 화면에서 분석 후
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
                dp(16),
                dp(28),
                dp(16),
                dp(28)
            )

            binding.historyContainer
                .addView(
                    emptyText
                )

            return
        }

        addTrendButton(
            allRecords
        )

        addCsvExportButton(
            allRecords
        )

        for (
            record in records
        ) {

            if (
                record.inspectionType.equals(
                    TYPE_TOTAL_SESSION,
                    ignoreCase = true
                )
            ) {

                addTotalSessionCard(
                    sessionRecord = record,
                    allRecords = allRecords
                )

            } else {

                addHistoryCard(
                    record
                )
            }
        }
    }

    /*
     * =========================================================
     * 종합검사 1회 카드
     * =========================================================
     */

    private fun addTotalSessionCard(
        sessionRecord:
        InspectionHistoryStore
            .InspectionRecord,
        allRecords:
        List<
            InspectionHistoryStore
                .InspectionRecord
            >
    ) {

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

        val cardParams =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )

        cardParams.bottomMargin =
            dp(14)

        card.layoutParams =
            cardParams

        card.setBackgroundColor(
            Color.parseColor(
                "#EEF5FA"
            )
        )

        /*
         * 제목
         */
        val title =
            TextView(
                this
            )

        title.text =
            "종합검사 1회"

        title.textSize =
            20f

        title.setTypeface(
            null,
            Typeface.BOLD
        )

        title.setTextColor(
            Color.parseColor(
                "#102A43"
            )
        )

        /*
         * 종합 판정
         */
        val judgment =
            TextView(
                this
            )

        judgment.text =
            "종합 판정 : ${sessionRecord.judgment}"

        judgment.textSize =
            18f

        judgment.setTypeface(
            null,
            Typeface.BOLD
        )

        judgment.setTextColor(
            judgmentColor(
                sessionRecord.judgment
            )
        )

        judgment.setPadding(
            0,
            dp(6),
            0,
            0
        )

        /*
         * 날짜 / 평균
         */
        val info =
            TextView(
                this
            )

        info.text =
            String.format(
                Locale.getDefault(),

                """
검사 일시 : %s
Model : %s
Line : %s
평균 Quality Score : %.1f / 100
                """.trimIndent(),

                sessionRecord.dateTime,
                sessionRecord.model.ifBlank { "-" },
                sessionRecord.line.ifBlank { "-" },
                sessionRecord.score
            )

        info.textSize =
            14f

        info.setTextColor(
            Color.parseColor(
                "#486581"
            )
        )

        info.setPadding(
            0,
            dp(8),
            0,
            dp(12)
        )

        card.addView(
            title
        )

        card.addView(
            judgment
        )

        card.addView(
            info
        )

        /*
         * TotalInspectionActivity가 저장해 둔
         * 각 검사 record ID를 읽습니다.
         */
        val childSpecs =
            listOf(
                Triple(
                    "1. BOTTOM CORNER",
                    "BOTTOM_ID",
                    "BOTTOM CORNER"
                ),
                Triple(
                    "2. SEAL",
                    "SEAL_ID",
                    "SEAL"
                ),
                Triple(
                    "3. FORMING",
                    "FORMING_ID",
                    "FORMING"
                ),
                Triple(
                    "4. TAB",
                    "TAB_ID",
                    "TAB"
                ),
                Triple(
                    "5. 분해검사",
                    "DISASSEMBLY_ID",
                    "DISASSEMBLY"
                )
            )

        for (
            spec in childSpecs
        ) {

            val recordId =
                parseRecordId(
                    sessionRecord.details,
                    spec.second
                )

            val childRecord =
                if (
                    recordId != null
                ) {

                    allRecords.firstOrNull {

                        it.id ==
                            recordId
                    }

                } else {

                    null
                }

            addSessionInspectionRow(
                parent = card,
                displayName = spec.first,
                expectedType = spec.third,
                record = childRecord
            )
        }

        /*
         * 안내
         */
        val note =
            TextView(
                this
            )

        note.text =
            """
※ 종합 판정은 5개 검사 중 가장 주의가 필요한 판정을 기준으로 합니다.
※ 각 '사진 보기' 버튼을 누르면 해당 검사 당시의 후보 표시 사진을 확인할 수 있습니다.
            """.trimIndent()

        note.textSize =
            12f

        note.setTextColor(
            Color.parseColor(
                "#627D98"
            )
        )

        note.setPadding(
            0,
            dp(12),
            0,
            0
        )

        card.addView(
            note
        )

        binding.historyContainer
            .addView(
                card
            )
    }

    /*
     * =========================================================
     * 종합검사 카드 내부 1개 항목
     * =========================================================
     */

    private fun addSessionInspectionRow(
        parent: LinearLayout,
        displayName: String,
        expectedType: String,
        record:
        InspectionHistoryStore
            .InspectionRecord?
    ) {

        val container =
            LinearLayout(
                this
            )

        container.orientation =
            LinearLayout.VERTICAL

        container.setPadding(
            dp(12),
            dp(10),
            dp(12),
            dp(10)
        )

        val containerParams =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )

        containerParams.bottomMargin =
            dp(7)

        container.layoutParams =
            containerParams

        container.setBackgroundColor(
            Color.WHITE
        )

        val headerRow =
            LinearLayout(
                this
            )

        headerRow.orientation =
            LinearLayout.HORIZONTAL

        headerRow.gravity =
            Gravity.CENTER_VERTICAL

        val resultText =
            TextView(
                this
            )

        val resultTextParams =
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )

        resultText.layoutParams =
            resultTextParams

        if (
            record != null &&
            record.inspectionType.equals(
                expectedType,
                ignoreCase = true
            )
        ) {

            resultText.text =
                String.format(
                    Locale.getDefault(),
                    "%s\n%.1f / 100  ·  %s",
                    displayName,
                    record.score,
                    record.judgment
                )

            resultText.setTextColor(
                judgmentColor(
                    record.judgment
                )
            )

        } else {

            resultText.text =
                "$displayName\n연결된 검사 결과를 찾을 수 없습니다."

            resultText.setTextColor(
                Color.parseColor(
                    "#829AB1"
                )
            )
        }

        resultText.textSize =
            14f

        resultText.setTypeface(
            null,
            Typeface.BOLD
        )

        headerRow.addView(
            resultText
        )

        if (
            record != null &&
            InspectionHistoryStore
                .getImageFile(
                    record
                ) != null
        ) {

            val imageButton =
                Button(
                    this
                )

            imageButton.text =
                "사진 보기"

            imageButton.textSize =
                12f

            imageButton.isAllCaps =
                false

            imageButton.setTextColor(
                Color.WHITE
            )

            imageButton.backgroundTintList =
                ColorStateList.valueOf(
                    Color.parseColor(
                        "#102A43"
                    )
                )

            val buttonParams =
                LinearLayout.LayoutParams(
                    dp(96),
                    dp(46)
                )

            buttonParams.marginStart =
                dp(8)

            imageButton.layoutParams =
                buttonParams

            imageButton.setOnClickListener {

                showResultImage(
                    record
                )
            }

            headerRow.addView(
                imageButton
            )

        } else {

            val noImage =
                TextView(
                    this
                )

            noImage.text =
                "사진 없음"

            noImage.textSize =
                12f

            noImage.setTextColor(
                Color.parseColor(
                    "#829AB1"
                )
            )

            noImage.setPadding(
                dp(8),
                0,
                0,
                0
            )

            headerRow.addView(
                noImage
            )
        }

        container.addView(
            headerRow
        )

        parent.addView(
            container
        )
    }

    /*
     * =========================================================
     * 일반 개별 검사 카드
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
            dp(14),
            dp(14),
            dp(14),
            dp(14)
        )

        val cardParams =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )

        cardParams.bottomMargin =
            dp(10)

        card.layoutParams =
            cardParams

        card.setBackgroundColor(
            Color.parseColor(
                "#F8FAFC"
            )
        )

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
            Typeface.BOLD
        )

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
            Typeface.BOLD
        )

        judgmentText.setTextColor(
            judgmentColor(
                record.judgment
            )
        )

        val infoText =
            TextView(
                this
            )

        infoText.text =
            String.format(
                Locale.getDefault(),

                """
검사 일시 : %s
Model : %s
Line : %s
Quality Score : %.1f / 100
민감도 : %d%%
                """.trimIndent(),

                record.dateTime,
                record.model.ifBlank { "-" },
                record.line.ifBlank { "-" },
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

        val imageFile =
            InspectionHistoryStore
                .getImageFile(
                    record
                )

        val imageViewControl:
            View =
            if (
                imageFile != null
            ) {

                createImageButton(
                    record
                )

            } else {

                createNoImageText()
            }

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
            dp(10),
            0,
            0
        )

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
            imageViewControl
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
     * 일반 결과 사진 보기 버튼
     * =========================================================
     */

    private fun createImageButton(
        record:
        InspectionHistoryStore
            .InspectionRecord
    ): Button {

        val button =
            Button(
                this
            )

        button.text =
            "결과 사진 보기"

        button.textSize =
            14f

        button.isAllCaps =
            false

        button.setTextColor(
            Color.WHITE
        )

        button.backgroundTintList =
            ColorStateList.valueOf(
                Color.parseColor(
                    "#102A43"
                )
            )

        val params =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48)
            )

        params.topMargin =
            dp(10)

        button.layoutParams =
            params

        button.setOnClickListener {

            showResultImage(
                record
            )
        }

        return button
    }

    /*
     * =========================================================
     * 사진이 없는 이력
     * =========================================================
     */

    private fun createNoImageText():
        TextView {

        val textView =
            TextView(
                this
            )

        textView.text =
            "결과 사진 : 없음"

        textView.textSize =
            13f

        textView.setTextColor(
            Color.parseColor(
                "#829AB1"
            )
        )

        textView.setPadding(
            0,
            dp(10),
            0,
            0
        )

        return textView
    }

    /*
     * =========================================================
     * 결과 사진 크게 보기
     * =========================================================
     */

    private fun showResultImage(
        record:
        InspectionHistoryStore
            .InspectionRecord
    ) {

        val imageFile =
            InspectionHistoryStore
                .getImageFile(
                    record
                )

        if (
            imageFile == null
        ) {

            Toast.makeText(
                this,
                "저장된 결과 사진을 찾을 수 없습니다.",
                Toast.LENGTH_LONG
            ).show()

            loadHistory()

            return
        }

        val bitmap =
            decodeBitmapForDisplay(
                imageFile
            )

        if (
            bitmap == null
        ) {

            Toast.makeText(
                this,
                "결과 사진을 불러올 수 없습니다.",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        val imageView =
            ImageView(
                this
            )

        imageView.adjustViewBounds =
            true

        imageView.scaleType =
            ImageView.ScaleType.FIT_CENTER

        imageView.setBackgroundColor(
            Color.BLACK
        )

        imageView.setPadding(
            dp(4),
            dp(4),
            dp(4),
            dp(4)
        )

        imageView.setImageBitmap(
            bitmap
        )

        val dialog =
            AlertDialog.Builder(
                this
            )
                .setTitle(
                    "${record.inspectionType} · ${record.judgment}"
                )
                .setMessage(
                    """
${record.dateTime}
Model : ${record.model.ifBlank { "-" }}
Line : ${record.line.ifBlank { "-" }}
Quality Score : ${String.format(Locale.getDefault(), "%.1f", record.score)} / 100
                    """.trimIndent()
                )
                .setView(
                    imageView
                )
                .setPositiveButton(
                    "닫기",
                    null
                )
                .create()

        dialog.setOnDismissListener {

            imageView.setImageDrawable(
                null
            )

            if (
                !bitmap.isRecycled
            ) {

                bitmap.recycle()
            }
        }

        dialog.show()
    }

    /*
     * =========================================================
     * 사진 Decode
     * =========================================================
     */

    private fun decodeBitmapForDisplay(
        file: File
    ): Bitmap? {

        return try {

            val bounds =
                BitmapFactory.Options().apply {

                    inJustDecodeBounds =
                        true
                }

            BitmapFactory.decodeFile(
                file.absolutePath,
                bounds
            )

            if (
                bounds.outWidth <=
                0 ||
                bounds.outHeight <=
                0
            ) {

                return null
            }

            val longestSide =
                maxOf(
                    bounds.outWidth,
                    bounds.outHeight
                )

            var sampleSize =
                1

            while (
                longestSide /
                    sampleSize >
                1600
            ) {

                sampleSize *=
                    2
            }

            val options =
                BitmapFactory.Options().apply {

                    inSampleSize =
                        sampleSize
                }

            BitmapFactory.decodeFile(
                file.absolutePath,
                options
            )

        } catch (
            e: Exception
        ) {

            null
        }
    }

    /*
     * =========================================================
     * CSV 내보내기 버튼
     * =========================================================
     */

    private fun addCsvExportButton(
        allRecords:
        List<
            InspectionHistoryStore
                .InspectionRecord
            >
    ) {

        val button = Button(this)

        button.text =
            "CSV 내보내기 (현재 필터)"

        button.textSize = 15f
        button.isAllCaps = false
        button.setTextColor(Color.WHITE)
        button.backgroundTintList =
            ColorStateList.valueOf(
                Color.parseColor("#102A43")
            )

        val params =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(52)
            )

        params.topMargin = dp(8)
        params.bottomMargin = dp(14)
        button.layoutParams = params

        button.setOnClickListener {
            prepareCsvExport(allRecords)
        }

        binding.historyContainer.addView(button)
    }

    /*
     * =========================================================
     * 현재 History 필터 기준 CSV 생성
     * =========================================================
     */

    private fun prepareCsvExport(
        allRecords:
        List<
            InspectionHistoryStore
                .InspectionRecord
            >
    ) {

        val selectedType =
            binding.spinnerInspectionType
                .selectedItem
                ?.toString()
                ?: FILTER_ALL

        val productionRecords =
            applyProductionFilter(
                allRecords
            )

        val exportRecords =
            when (selectedType) {

                FILTER_ALL -> {
                    /* PC 분석용 원본 데이터이므로 전체 행을 내보냅니다. */
                    productionRecords
                }

                FILTER_TOTAL_SESSION -> {
                    productionRecords.filter {
                        it.inspectionType.equals(
                            TYPE_TOTAL_SESSION,
                            ignoreCase = true
                        )
                    }
                }

                else -> {
                    productionRecords.filter {
                        it.inspectionType.equals(
                            selectedType,
                            ignoreCase = true
                        )
                    }
                }
            }

        if (exportRecords.isEmpty()) {
            Toast.makeText(
                this,
                "현재 필터에 내보낼 검사 이력이 없습니다.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        pendingCsvContent =
            buildCsvText(
                records = exportRecords,
                allRecords = allRecords
            )

        val timestamp =
            SimpleDateFormat(
                "yyyyMMdd_HHmmss",
                Locale.getDefault()
            ).format(Date())

        val filterName =
            sanitizeFileName(
                "${currentProductionFilterText()}_${selectedType}"
            )

        csvCreateLauncher.launch(
            "PouchVision_${filterName}_$timestamp.csv"
        )
    }

    /*
     * =========================================================
     * CSV 본문 생성
     * =========================================================
     */

    private fun buildCsvText(
        records:
        List<
            InspectionHistoryStore
                .InspectionRecord
            >,
        allRecords:
        List<
            InspectionHistoryStore
                .InspectionRecord
            >
    ): String {

        val childSessionLabel =
            mutableMapOf<Long, String>()

        allRecords
            .filter {
                it.inspectionType.equals(
                    TYPE_TOTAL_SESSION,
                    ignoreCase = true
                )
            }
            .forEach { session ->
                parseSessionChildIds(session.details)
                    .forEach { childId ->
                        childSessionLabel[childId] = session.dateTime
                    }
            }

        return buildString {

            val header =
                listOf(
                    "Record ID",
                    "검사일시",
                    "Model",
                    "Line",
                    "구분",
                    "검사항목",
                    "Quality Score",
                    "판정",
                    "민감도(%)",
                    "결과사진",
                    "사진파일명",
                    "종합검사 Session",
                    "상세결과"
                )

            append(
                header.joinToString(",") {
                    csvEscape(it)
                }
            )
            append("\r\n")

            records.sortedBy { it.id }.forEach { record ->

                val isTotalSession =
                    record.inspectionType.equals(
                        TYPE_TOTAL_SESSION,
                        ignoreCase = true
                    )

                val imageFile =
                    InspectionHistoryStore.getImageFile(record)

                val sessionLabel =
                    if (isTotalSession) {
                        record.dateTime
                    } else {
                        childSessionLabel[record.id] ?: ""
                    }

                val row =
                    listOf(
                        record.id.toString(),
                        record.dateTime,
                        record.model,
                        record.line,
                        if (isTotalSession) "종합검사 요약" else "개별검사",
                        if (isTotalSession) "종합검사" else record.inspectionType,
                        String.format(Locale.US, "%.1f", record.score),
                        record.judgment,
                        if (isTotalSession) "" else record.sensitivity.toString(),
                        if (imageFile != null) "Y" else "N",
                        imageFile?.name ?: "",
                        sessionLabel,
                        record.details
                    )

                append(
                    row.joinToString(",") {
                        csvEscape(it)
                    }
                )
                append("\r\n")
            }
        }
    }

    private fun csvEscape(
        value: String
    ): String {
        return "\"" +
            value.replace("\"", "\"\"") +
            "\""
    }

    private fun sanitizeFileName(
        value: String
    ): String {
        return value
            .replace(
                Regex("[^0-9A-Za-z가-힣_-]"),
                "_"
            )
            .take(40)
            .ifBlank { "History" }
    }

    /*
     * =========================================================
     * 기간별 Trend / 통계 버튼
     *
     * XML을 추가로 수정하지 않고 History 화면 안에
     * 동적으로 버튼을 생성합니다.
     * =========================================================
     */

    private fun addTrendButton(
        allRecords:
        List<
            InspectionHistoryStore
                .InspectionRecord
            >
    ) {

        val button =
            Button(
                this
            )

        button.text =
            "기간별 Trend / 통계 보기"

        button.textSize =
            15f

        button.isAllCaps =
            false

        button.setTextColor(
            Color.WHITE
        )

        button.backgroundTintList =
            ColorStateList.valueOf(
                Color.parseColor(
                    "#102A43"
                )
            )

        val params =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(52)
            )

        params.bottomMargin =
            dp(12)

        button.layoutParams =
            params

        button.setOnClickListener {

            showTrendDialog(
                allRecords
            )
        }

        binding.historyContainer
            .addView(
                button
            )
    }

    /*
     * =========================================================
     * Trend Dialog
     *
     * 7일 / 30일 / 90일 기준으로
     * 선택한 검사 필터의 Quality Score 변화를 표시합니다.
     * =========================================================
     */

    private fun showTrendDialog(
        allRecords:
        List<
            InspectionHistoryStore
                .InspectionRecord
            >
    ) {

        val selectedType =
            binding.spinnerInspectionType
                .selectedItem
                ?.toString()
                ?: FILTER_ALL

        val productionRecords =
            applyProductionFilter(
                allRecords
            )

        val sourceRecords =
            trendSourceRecords(
                allRecords = productionRecords,
                selectedType = selectedType
            )

        if (
            sourceRecords.isEmpty()
        ) {

            Toast.makeText(
                this,
                "Trend를 표시할 검사 결과가 없습니다.",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        val root =
            LinearLayout(
                this
            )

        root.orientation =
            LinearLayout.VERTICAL

        root.setPadding(
            dp(16),
            dp(8),
            dp(16),
            dp(8)
        )

        val periodRow =
            LinearLayout(
                this
            )

        periodRow.orientation =
            LinearLayout.HORIZONTAL

        periodRow.gravity =
            Gravity.CENTER

        val summaryText =
            TextView(
                this
            )

        summaryText.textSize =
            14f

        summaryText.setTextColor(
            Color.parseColor(
                "#334E68"
            )
        )

        summaryText.setPadding(
            0,
            dp(12),
            0,
            dp(10)
        )

        val chart =
            ScoreTrendView(
                this
            )

        chart.layoutParams =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(270)
            )

        val btn7 =
            createPeriodButton(
                "최근 7일"
            )

        val btn30 =
            createPeriodButton(
                "최근 30일"
            )

        val btn90 =
            createPeriodButton(
                "최근 90일"
            )

        periodRow.addView(
            btn7
        )

        periodRow.addView(
            btn30
        )

        periodRow.addView(
            btn90
        )

        root.addView(
            periodRow
        )

        root.addView(
            summaryText
        )

        root.addView(
            chart
        )

        fun renderPeriod(
            days: Int
        ) {

            val records =
                filterTrendPeriod(
                    records = sourceRecords,
                    days = days
                )

            chart.setRecords(
                records
            )

            summaryText.text =
                buildTrendSummary(
                    records = records,
                    selectedType = selectedType,
                    days = days
                )
        }

        btn7.setOnClickListener {
            renderPeriod(7)
        }

        btn30.setOnClickListener {
            renderPeriod(30)
        }

        btn90.setOnClickListener {
            renderPeriod(90)
        }

        /*
         * 기본 화면은 최근 30일
         */
        renderPeriod(
            30
        )

        AlertDialog.Builder(
            this
        )
            .setTitle(
                "Quality Score Trend · ${trendDisplayName(selectedType)}\n${currentProductionFilterText()}"
            )
            .setView(
                root
            )
            .setPositiveButton(
                "닫기",
                null
            )
            .show()
    }

    /*
     * =========================================================
     * Trend용 기간 버튼
     * =========================================================
     */

    private fun createPeriodButton(
        title: String
    ): Button {

        val button =
            Button(
                this
            )

        button.text =
            title

        button.textSize =
            12f

        button.isAllCaps =
            false

        button.setTextColor(
            Color.WHITE
        )

        button.backgroundTintList =
            ColorStateList.valueOf(
                Color.parseColor(
                    "#486581"
                )
            )

        val params =
            LinearLayout.LayoutParams(
                0,
                dp(46),
                1f
            )

        params.marginStart =
            dp(3)

        params.marginEnd =
            dp(3)

        button.layoutParams =
            params

        return button
    }

    /*
     * =========================================================
     * 선택한 History 필터에 맞는 Trend 원본 데이터
     * =========================================================
     */

    private fun trendSourceRecords(
        allRecords:
        List<
            InspectionHistoryStore
                .InspectionRecord
            >,
        selectedType: String
    ):
        List<
            InspectionHistoryStore
                .InspectionRecord
            > {

        val filtered =
            when (
                selectedType
            ) {

                FILTER_TOTAL_SESSION -> {

                    allRecords.filter {

                        it.inspectionType.equals(
                            TYPE_TOTAL_SESSION,
                            ignoreCase = true
                        )
                    }
                }

                FILTER_ALL -> {

                    /*
                     * 전체 검사는 개별 검사 Quality Score를 모두 표시합니다.
                     * TOTAL SESSION 요약값은 중복 계산을 피하기 위해 제외합니다.
                     */
                    allRecords.filter {

                        !it.inspectionType.equals(
                            TYPE_TOTAL_SESSION,
                            ignoreCase = true
                        )
                    }
                }

                else -> {

                    allRecords.filter {

                        it.inspectionType.equals(
                            selectedType,
                            ignoreCase = true
                        )
                    }
                }
            }

        return filtered
            .sortedBy {

                it.id
            }
    }

    /*
     * =========================================================
     * 기간 필터
     * =========================================================
     */

    private fun filterTrendPeriod(
        records:
        List<
            InspectionHistoryStore
                .InspectionRecord
            >,
        days: Int
    ):
        List<
            InspectionHistoryStore
                .InspectionRecord
            > {

        val millisPerDay =
            24L *
                60L *
                60L *
                1000L

        val startTime =
            System.currentTimeMillis() -
                days.toLong() *
                millisPerDay

        return records.filter {

            it.id >=
                startTime
        }
    }

    /*
     * =========================================================
     * Trend 통계 Summary
     * =========================================================
     */

    private fun buildTrendSummary(
        records:
        List<
            InspectionHistoryStore
                .InspectionRecord
            >,
        selectedType: String,
        days: Int
    ): String {

        if (
            records.isEmpty()
        ) {

            return """
기간 : 최근 ${days}일
검사 항목 : ${trendDisplayName(selectedType)}

해당 기간에 저장된 검사 결과가 없습니다.
            """.trimIndent()
        }

        val average =
            records
                .map {

                    it.score
                }
                .average()

        val minRecord =
            records.minByOrNull {

                it.score
            }

        val maxRecord =
            records.maxByOrNull {

                it.score
            }

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

                record.judgment.contains(
                    "불량"
                ) ->
                    ngCount++

                record.judgment.contains(
                    "한계"
                ) ->
                    limitCount++

                record.judgment.contains(
                    "주의"
                ) ->
                    warningCount++

                record.judgment.contains(
                    "정상"
                ) ->
                    normalCount++
            }
        }

        return String.format(
            Locale.getDefault(),

            """
기간 : 최근 %d일
검사 항목 : %s
데이터 : %d건

평균 Score : %.1f / 100
최고 Score : %.1f / 100
최저 Score : %.1f / 100

정상 %d  ·  주의 %d  ·  한계정상 %d  ·  불량 %d

※ 그래프는 저장된 Quality Score의 시간 순 변화를 보여줍니다.
            """.trimIndent(),

            days,
            trendDisplayName(
                selectedType
            ),
            records.size,
            average,
            maxRecord?.score ?: 0.0,
            minRecord?.score ?: 0.0,
            normalCount,
            warningCount,
            limitCount,
            ngCount
        )
    }

    private fun trendDisplayName(
        selectedType: String
    ): String {

        return when (
            selectedType
        ) {

            FILTER_ALL ->
                "전체 개별검사"

            FILTER_TOTAL_SESSION ->
                "종합검사 평균"

            else ->
                selectedType
        }
    }

    /*
     * =========================================================
     * 간단한 Quality Score Line Chart
     *
     * 외부 Chart Library를 추가하지 않고 Android Canvas만 사용합니다.
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
                    resources.displayMetrics.density
            }

        private val linePaint =
            Paint(
                Paint.ANTI_ALIAS_FLAG
            ).apply {

                color =
                    Color.parseColor(
                        "#1565C0"
                    )

                style =
                    Paint.Style.STROKE

                strokeWidth =
                    2.5f *
                        resources.displayMetrics.density
            }

        private val pointPaint =
            Paint(
                Paint.ANTI_ALIAS_FLAG
            ).apply {

                color =
                    Color.parseColor(
                        "#102A43"
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
                    11f *
                        resources.displayMetrics.scaledDensity
            }

        private val dateFormat =
            SimpleDateFormat(
                "MM/dd",
                Locale.getDefault()
            )

        fun setRecords(
            newRecords:
            List<
                InspectionHistoryStore
                    .InspectionRecord
                >
        ) {

            records =
                newRecords
                    .sortedBy {

                        it.id
                    }

            invalidate()
        }

        override fun onDraw(
            canvas: Canvas
        ) {

            super.onDraw(
                canvas
            )

            canvas.drawColor(
                Color.WHITE
            )

            val density =
                resources.displayMetrics.density

            val left =
                42f *
                    density

            val right =
                width.toFloat() -
                    14f *
                    density

            val top =
                18f *
                    density

            val bottom =
                height.toFloat() -
                    34f *
                    density

            if (
                right <=
                left ||
                bottom <=
                top
            ) {

                return
            }

            /*
             * 0 / 25 / 50 / 75 / 100 Grid
             */
            for (
                score in listOf(
                    0,
                    25,
                    50,
                    75,
                    100
                )
            ) {

                val y =
                    bottom -
                        score / 100f *
                        (
                            bottom -
                                top
                            )

                canvas.drawLine(
                    left,
                    y,
                    right,
                    y,
                    gridPaint
                )

                canvas.drawText(
                    score.toString(),
                    4f *
                        density,
                    y +
                        4f *
                        density,
                    textPaint
                )
            }

            if (
                records.isEmpty()
            ) {

                canvas.drawText(
                    "해당 기간의 데이터가 없습니다.",
                    left,
                    (
                        top +
                            bottom
                        ) /
                        2f,
                    textPaint
                )

                return
            }

            val path =
                Path()

            val count =
                records.size

            for (
                index in records.indices
            ) {

                val record =
                    records[index]

                val x =
                    if (
                        count <=
                        1
                    ) {

                        (
                            left +
                                right
                            ) /
                            2f

                    } else {

                        left +
                            index.toFloat() /
                            (
                                count -
                                    1
                                ).toFloat() *
                            (
                                right -
                                    left
                                )
                    }

                val score =
                    record.score
                        .coerceIn(
                            0.0,
                            100.0
                        )

                val y =
                    bottom -
                        score.toFloat() /
                        100f *
                        (
                            bottom -
                                top
                            )

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

                canvas.drawCircle(
                    x,
                    y,
                    3.5f *
                        density,
                    pointPaint
                )
            }

            if (
                records.size >
                1
            ) {

                canvas.drawPath(
                    path,
                    linePaint
                )
            }

            /*
             * 시작 / 마지막 날짜
             */
            val firstDate =
                dateFormat.format(
                    Date(
                        records.first().id
                    )
                )

            val lastDate =
                dateFormat.format(
                    Date(
                        records.last().id
                    )
                )

            canvas.drawText(
                firstDate,
                left,
                height.toFloat() -
                    9f *
                    density,
                textPaint
            )

            val lastWidth =
                textPaint.measureText(
                    lastDate
                )

            canvas.drawText(
                lastDate,
                right -
                    lastWidth,
                height.toFloat() -
                    9f *
                    density,
                textPaint
            )
        }
    }

    /*
     * =========================================================
     * TOTAL SESSION 내부 record ID 파싱
     * =========================================================
     */

    private fun parseSessionChildIds(
        details: String
    ): List<Long> {

        return listOf(
            "BOTTOM_ID",
            "SEAL_ID",
            "FORMING_ID",
            "TAB_ID",
            "DISASSEMBLY_ID"
        )
            .mapNotNull {

                parseRecordId(
                    details,
                    it
                )
            }
    }

    private fun parseRecordId(
        details: String,
        key: String
    ): Long? {

        val prefix =
            "$key="

        val line =
            details
                .lineSequence()
                .firstOrNull {

                    it.trim()
                        .startsWith(
                            prefix
                        )
                }
                ?: return null

        return line
            .trim()
            .removePrefix(
                prefix
            )
            .trim()
            .toLongOrNull()
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
