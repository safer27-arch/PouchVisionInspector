package com.pouchvision.inspector

import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.pouchvision.inspector.databinding.ActivityHistoryBinding
import java.io.File
import java.util.Locale

class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding

    companion object {

        private const val TYPE_TOTAL_SESSION =
            "TOTAL SESSION"

        private const val FILTER_ALL =
            "전체 검사"

        private const val FILTER_TOTAL_SESSION =
            "종합검사 묶음"
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

        setupSpinner()

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

        binding.spinnerInspectionType
            .setSelection(
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
     * 검사 이력 불러오기
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
저장된 검사 결과 : %d건
결과 사진 있음 : %d건

정상 : %d건
주의 : %d건
한계정상 : %d건
불량 : %d건

평균 Quality Score : %.1f / 100
                    """.trimIndent(),

                    selectedType,
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

종합검사 Session : %d회

종합 정상 : %d회
종합 주의 : %d회
종합 한계정상 : %d회
종합 불량 : %d회

Session 평균 Quality Score : %.1f / 100

각 Session 카드에서
5개 검사 결과와 저장 사진을 함께 확인할 수 있습니다.
                """.trimIndent(),

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
평균 Quality Score : %.1f / 100
                """.trimIndent(),

                sessionRecord.dateTime,
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
