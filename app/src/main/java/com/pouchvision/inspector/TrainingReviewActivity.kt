package com.pouchvision.inspector

import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class TrainingReviewActivity : AppCompatActivity() {

    companion object {
        private const val FILTER_ALL = "전체"
        private const val FILTER_UNLABELED = "미분류만"
        private const val FILTER_MISMATCH = "AI와 정답 불일치"
    }

    private lateinit var root: LinearLayout
    private lateinit var switchTraining: Switch
    private lateinit var spinnerType: Spinner
    private lateinit var spinnerMode: Spinner
    private lateinit var tvSummary: TextView
    private lateinit var tvPosition: TextView
    private lateinit var imageView: ImageView
    private lateinit var tvInfo: TextView
    private lateinit var tvDetails: TextView
    private lateinit var editNote: EditText

    private var allRecords =
        emptyList<TrainingDataStore.TrainingRecord>()

    private var filteredRecords =
        emptyList<TrainingDataStore.TrainingRecord>()

    private var currentIndex = 0

    private val typeItems =
        listOf(
            FILTER_ALL,
            "BOTTOM CORNER",
            "SEAL",
            "FORMING",
            "TAB",
            "DISASSEMBLY"
        )

    private val modeItems =
        listOf(
            "전체 데이터",
            FILTER_UNLABELED,
            FILTER_MISMATCH
        )

    private val exportLauncher =
        registerForActivityResult(
            ActivityResultContracts.CreateDocument(
                "application/zip"
            )
        ) { uri ->

            if (uri == null) {
                return@registerForActivityResult
            }

            try {
                contentResolver
                    .openOutputStream(uri)
                    ?.use { output ->

                        TrainingDataStore.exportZip(
                            this,
                            output
                        )
                    }
                    ?: throw Exception(
                        "저장 파일을 열 수 없습니다."
                    )

                Toast.makeText(
                    this,
                    "학습 데이터 ZIP 저장 완료",
                    Toast.LENGTH_LONG
                ).show()

            } catch (e: Exception) {

                Toast.makeText(
                    this,
                    "Export 실패 : ${e.message ?: "알 수 없는 오류"}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        setContentView(
            createScreen()
        )

        ViewCompat.setOnApplyWindowInsetsListener(
            root
        ) { view, insets ->

            val bars =
                insets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                )

            view.updatePadding(
                left = bars.left,
                top = bars.top,
                right = bars.right,
                bottom = bars.bottom
            )

            insets
        }

        ViewCompat.requestApplyInsets(
            root
        )

        setupSpinners()

        switchTraining.isChecked =
            TrainingDataStore.isEnabled(
                this
            )

        switchTraining.setOnCheckedChangeListener {
                _,
                checked ->

            TrainingDataStore.setEnabled(
                this,
                checked
            )

            if (checked) {
                syncNow(
                    showToast = true
                )
            } else {
                reload()
            }
        }

        if (
            TrainingDataStore.isEnabled(
                this
            )
        ) {
            syncNow(
                showToast = false
            )
        } else {
            reload()
        }
    }

    override fun onResume() {
        super.onResume()

        if (
            ::root.isInitialized &&
            TrainingDataStore.isEnabled(
                this
            )
        ) {
            syncNow(
                showToast = false
            )
        }
    }

    private fun createScreen(): View {

        val scroll =
            ScrollView(this).apply {

                isFillViewport = true

                setBackgroundColor(
                    Color.parseColor(
                        "#F4F6F8"
                    )
                )
            }

        root =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.VERTICAL
            }

        root.addView(
            createHeader()
        )

        val content =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    dp(14),
                    dp(14),
                    dp(14),
                    dp(30)
                )
            }

        /*
         * =====================================================
         * 학습 모드
         * =====================================================
         */
        switchTraining =
            Switch(this).apply {

                text =
                    "학습 데이터 수집 ON"

                textSize =
                    16f

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

        val trainingBox =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                addView(
                    switchTraining
                )

                addView(
                    infoText().apply {

                        text =
                            "검사 결과 사진을 학습용으로 별도 보관합니다.\n" +
                                "사진을 보고 실제 정답을 직접 지정해주세요."

                        setPadding(
                            0,
                            dp(8),
                            0,
                            dp(4)
                        )
                    }
                )

                addView(
                    fullButton(
                        "현재 검사이력 가져오기",
                        "#123E63"
                    ) {
                        syncNow(
                            showToast = true
                        )
                    }
                )
            }

        content.addView(
            card(
                "학습 모드",
                trainingBox
            )
        )

        /*
         * =====================================================
         * 필터
         * =====================================================
         */
        spinnerType =
            Spinner(this)

        spinnerMode =
            Spinner(this)

        val filterBox =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                addView(
                    labelText(
                        "검사 항목"
                    )
                )

                addView(
                    spinnerType,
                    spinnerLayoutParams()
                )

                addView(
                    labelText(
                        "보기 방식"
                    )
                )

                addView(
                    spinnerMode,
                    spinnerLayoutParams()
                )
            }

        content.addView(
            card(
                "학습 데이터 필터",
                filterBox
            )
        )

        /*
         * =====================================================
         * 학습 현황
         * =====================================================
         */
        tvSummary =
            infoText()

        content.addView(
            card(
                "학습 현황",
                tvSummary
            )
        )

        /*
         * =====================================================
         * 학습 사진
         * =====================================================
         */
        tvPosition =
            TextView(this).apply {

                gravity =
                    Gravity.CENTER

                textSize =
                    15f

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

        imageView =
            ImageView(this).apply {

                scaleType =
                    ImageView.ScaleType.FIT_CENTER

                adjustViewBounds =
                    true

                setBackgroundColor(
                    Color.parseColor(
                        "#E9EEF3"
                    )
                )
            }

        tvInfo =
            infoText()

        tvDetails =
            infoText().apply {

                textSize =
                    12f

                maxLines =
                    8

                setTextColor(
                    Color.parseColor(
                        "#627D98"
                    )
                )
            }

        editNote =
            EditText(this).apply {

                hint =
                    "라벨 메모 (선택)"

                textSize =
                    14f

                minHeight =
                    dp(50)

                setTextColor(
                    Color.parseColor(
                        "#102A43"
                    )
                )

                setHintTextColor(
                    Color.parseColor(
                        "#829AB1"
                    )
                )

                background =
                    roundedBackground(
                        "#F4F6F8"
                    )

                setPadding(
                    dp(12),
                    0,
                    dp(12),
                    0
                )
            }

        val reviewBox =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                addView(
                    tvPosition
                )

                addView(
                    imageView,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(300)
                    ).apply {
                        topMargin =
                            dp(8)
                    }
                )

                addView(
                    tvInfo,
                    marginParams(
                        10
                    )
                )

                addView(
                    tvDetails,
                    marginParams(
                        8
                    )
                )

                addView(
                    editNote,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(52)
                    ).apply {
                        topMargin =
                            dp(10)
                    }
                )

                addView(
                    createLabelButtons()
                )

                addView(
                    createNavigationButtons()
                )

                addView(
                    fullButton(
                        "현재 정답 라벨 지우기",
                        "#7B8794"
                    ) {
                        clearCurrentLabel()
                    }
                )
            }

        content.addView(
            card(
                "정답 라벨 지정",
                reviewBox
            )
        )

        /*
         * =====================================================
         * Export
         * =====================================================
         */
        content.addView(
            fullButton(
                "학습 데이터 ZIP Export",
                "#0F6B50"
            ) {
                exportZip()
            }
        )

        content.addView(
            fullButton(
                "뒤로",
                "#486581"
            ) {
                finish()
            }
        )

        root.addView(
            content
        )

        scroll.addView(
            root
        )

        return scroll
    }

    private fun setupSpinners() {

        spinnerType.adapter =
            createSpinnerAdapter(
                typeItems
            )

        spinnerMode.adapter =
            createSpinnerAdapter(
                modeItems
            )

        val listener =
            object :
                AdapterView.OnItemSelectedListener {

                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    applyFilter()
                }

                override fun onNothingSelected(
                    parent: AdapterView<*>?
                ) {
                }
            }

        spinnerType.onItemSelectedListener =
            listener

        spinnerMode.onItemSelectedListener =
            listener
    }

    private fun syncNow(
        showToast: Boolean = true
    ) {

        val added =
            TrainingDataStore
                .syncFromInspectionHistory(
                    this
                )

        reload()

        if (showToast) {

            val message =
                if (added > 0) {

                    "학습 데이터 ${added}건 추가"

                } else {

                    "새로 가져올 검사 이미지가 없습니다."
                }

            Toast.makeText(
                this,
                message,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun reload() {

        val previousId =
            filteredRecords
                .getOrNull(
                    currentIndex
                )
                ?.sourceId

        allRecords =
            TrainingDataStore.load(
                this
            )

        applyFilter(
            previousId
        )
    }

    private fun applyFilter(
        preferredId: Long? = null
    ) {

        if (
            !::spinnerType.isInitialized ||
            !::spinnerMode.isInitialized
        ) {
            return
        }

        val selectedType =
            spinnerType
                .selectedItem
                ?.toString()
                ?: FILTER_ALL

        val selectedMode =
            spinnerMode
                .selectedItem
                ?.toString()
                ?: "전체 데이터"

        filteredRecords =
            allRecords
                .filter {

                    selectedType ==
                        FILTER_ALL ||

                        it.inspectionType.equals(
                            selectedType,
                            ignoreCase = true
                        )
                }
                .filter {

                    when (
                        selectedMode
                    ) {

                        FILTER_UNLABELED ->
                            !it.isLabeled

                        FILTER_MISMATCH ->
                            it.isMismatch

                        else ->
                            true
                    }
                }

        currentIndex =
            if (
                preferredId != null
            ) {

                val found =
                    filteredRecords
                        .indexOfFirst {
                            it.sourceId ==
                                preferredId
                        }

                if (
                    found >= 0
                ) {
                    found
                } else {
                    0
                }

            } else {
                0
            }

        renderSummary()
        renderCurrent()
    }

    private fun renderSummary() {

        val total =
            allRecords.size

        val labeled =
            allRecords.count {
                it.isLabeled
            }

        val mismatch =
            allRecords.count {
                it.isMismatch
            }

        val selectedType =
            spinnerType
                .selectedItem
                ?.toString()

        val inspectionType =
            if (
                selectedType == null ||
                selectedType == FILTER_ALL
            ) {
                null
            } else {
                selectedType
            }

        val counts =
            TrainingDataStore
                .countByLabel(
                    this,
                    inspectionType
                )

        tvSummary.text =
            buildString {

                append(
                    "수집 : ${total}건"
                )

                append(
                    "  |  라벨완료 : ${labeled}건"
                )

                append(
                    "\n미분류 : ${total - labeled}건"
                )

                append(
                    "  |  AI/정답 불일치 : ${mismatch}건"
                )

                append(
                    "\n\n정상 : ${
                        counts[
                            TrainingDataStore.LABEL_NORMAL
                        ] ?: 0
                    }"
                )

                append(
                    "  |  주의 : ${
                        counts[
                            TrainingDataStore.LABEL_WARNING
                        ] ?: 0
                    }"
                )

                append(
                    "\n한계정상 : ${
                        counts[
                            TrainingDataStore.LABEL_LIMIT
                        ] ?: 0
                    }"
                )

                append(
                    "  |  불량 : ${
                        counts[
                            TrainingDataStore.LABEL_NG
                        ] ?: 0
                    }"
                )
            }
    }

    private fun renderCurrent() {

        if (
            filteredRecords.isEmpty()
        ) {

            tvPosition.text =
                "표시할 학습 데이터가 없습니다."

            imageView.setImageDrawable(
                null
            )

            tvInfo.text =
                "검사 결과를 먼저 저장한 뒤\n" +
                    "'현재 검사이력 가져오기'를 눌러주세요."

            tvDetails.text =
                ""

            editNote.setText(
                ""
            )

            return
        }

        currentIndex =
            currentIndex.coerceIn(
                0,
                filteredRecords.lastIndex
            )

        val record =
            filteredRecords[
                currentIndex
            ]

        tvPosition.text =
            "${currentIndex + 1} / ${filteredRecords.size}"

        val imageFile =
            File(
                record.imagePath
            )

        if (
            imageFile.exists()
        ) {

            imageView.setImageBitmap(
                BitmapFactory.decodeFile(
                    imageFile.absolutePath
                )
            )

        } else {

            imageView.setImageDrawable(
                null
            )
        }

        val comparison =
            when {

                !record.isLabeled ->
                    "정답 라벨 미지정"

                record.isMismatch ->
                    "⚠ AI 판정과 실제 정답이 다릅니다."

                else ->
                    "✅ AI 판정과 실제 정답이 일치합니다."
            }

        tvInfo.text =
            buildString {

                append(
                    "${record.inspectionType}\n"
                )

                append(
                    "Model : ${
                        record.model.ifBlank {
                            "-"
                        }
                    }"
                )

                append(
                    "  |  Line : ${
                        record.line.ifBlank {
                            "-"
                        }
                    }\n"
                )

                append(
                    String.format(
                        Locale.getDefault(),
                        "Score : %.1f  |  민감도 : %d%%\n",
                        record.score,
                        record.sensitivity
                    )
                )

                append(
                    "AI 판정 : ${
                        record.aiJudgment.ifBlank {
                            "-"
                        }
                    }\n"
                )

                append(
                    "실제 정답 : ${
                        record.trueLabel.ifBlank {
                            "미지정"
                        }
                    }\n"
                )

                append(
                    comparison
                )
            }

        tvDetails.text =
            "검사시간 : ${record.dateTime}\n" +
                record.details

        editNote.setText(
            record.note
        )
    }

    private fun setCurrentLabel(
        label: String
    ) {

        val record =
            filteredRecords
                .getOrNull(
                    currentIndex
                )
                ?: return

        val success =
            TrainingDataStore
                .setLabel(
                    context = this,
                    sourceId = record.sourceId,
                    label = label,
                    note =
                        editNote.text
                            ?.toString()
                            .orEmpty()
                )

        if (!success) {

            Toast.makeText(
                this,
                "라벨 저장 실패",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        Toast.makeText(
            this,
            "실제 정답 : $label",
            Toast.LENGTH_SHORT
        ).show()

        val id =
            record.sourceId

        allRecords =
            TrainingDataStore.load(
                this
            )

        applyFilter(
            id
        )
    }

    private fun clearCurrentLabel() {

        val record =
            filteredRecords
                .getOrNull(
                    currentIndex
                )
                ?: return

        TrainingDataStore
            .clearLabel(
                this,
                record.sourceId
            )

        allRecords =
            TrainingDataStore.load(
                this
            )

        applyFilter(
            record.sourceId
        )
    }

    private fun move(
        delta: Int
    ) {

        if (
            filteredRecords.isEmpty()
        ) {
            return
        }

        currentIndex =
            (
                currentIndex +
                    delta
                )
                .coerceIn(
                    0,
                    filteredRecords.lastIndex
                )

        renderCurrent()
    }

    private fun createLabelButtons():
        LinearLayout {

        val container =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    0,
                    dp(8),
                    0,
                    0
                )
            }

        val row1 =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.HORIZONTAL
            }

        row1.addView(
            smallButton(
                "정상",
                "#2E7D32"
            ) {
                setCurrentLabel(
                    TrainingDataStore.LABEL_NORMAL
                )
            },
            weightedParams()
        )

        row1.addView(
            smallButton(
                "주의",
                "#C47A00"
            ) {
                setCurrentLabel(
                    TrainingDataStore.LABEL_WARNING
                )
            },
            weightedParams(
                leftDp = 6
            )
        )

        val row2 =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.HORIZONTAL
            }

        row2.addView(
            smallButton(
                "한계정상",
                "#B45309"
            ) {
                setCurrentLabel(
                    TrainingDataStore.LABEL_LIMIT
                )
            },
            weightedParams()
        )

        row2.addView(
            smallButton(
                "불량",
                "#B42318"
            ) {
                setCurrentLabel(
                    TrainingDataStore.LABEL_NG
                )
            },
            weightedParams(
                leftDp = 6
            )
        )

        container.addView(
            row1
        )

        container.addView(
            row2
        )

        return container
    }

    private fun createNavigationButtons():
        LinearLayout {

        return LinearLayout(this).apply {

            orientation =
                LinearLayout.HORIZONTAL

            addView(
                smallButton(
                    "← 이전",
                    "#486581"
                ) {
                    move(
                        -1
                    )
                },
                weightedParams()
            )

            addView(
                smallButton(
                    "다음 →",
                    "#486581"
                ) {
                    move(
                        1
                    )
                },
                weightedParams(
                    leftDp = 6
                )
            )
        }
    }

    private fun exportZip() {

        if (
            allRecords.isEmpty()
        ) {

            Toast.makeText(
                this,
                "Export할 학습 데이터가 없습니다.",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        val time =
            SimpleDateFormat(
                "yyyyMMdd_HHmmss",
                Locale.getDefault()
            ).format(
                Date()
            )

        exportLauncher.launch(
            "PouchTraining_$time.zip"
        )
    }

    private fun createHeader(): View {

        return LinearLayout(this).apply {

            orientation =
                LinearLayout.VERTICAL

            setBackgroundColor(
                Color.parseColor(
                    "#102A43"
                )
            )

            setPadding(
                dp(20),
                dp(18),
                dp(20),
                dp(18)
            )

            addView(
                TextView(
                    this@TrainingReviewActivity
                ).apply {

                    text =
                        "불량 기준 학습센터"

                    textSize =
                        25f

                    setTypeface(
                        null,
                        Typeface.BOLD
                    )

                    setTextColor(
                        Color.WHITE
                    )
                }
            )

            addView(
                TextView(
                    this@TrainingReviewActivity
                ).apply {

                    text =
                        "AI Judgment ↔ Operator Ground Truth"

                    textSize =
                        13f

                    setTextColor(
                        Color.parseColor(
                            "#D9E2EC"
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
        }
    }

    private fun card(
        title: String,
        child: View
    ): LinearLayout {

        val view =
            LinearLayout(this).apply {

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
                        "#FFFFFF"
                    )

                addView(
                    TextView(
                        this@TrainingReviewActivity
                    ).apply {

                        text =
                            title

                        textSize =
                            18f

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
                            0,
                            0,
                            0,
                            dp(10)
                        )
                    }
                )

                addView(
                    child
                )
            }

        view.layoutParams =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {

                bottomMargin =
                    dp(12)
            }

        return view
    }

    private fun fullButton(
        textValue: String,
        colorValue: String,
        action: () -> Unit
    ): Button {

        val button =
            Button(this).apply {

                text =
                    textValue

                textSize =
                    15f

                minHeight =
                    dp(52)

                setTextColor(
                    Color.WHITE
                )

                backgroundTintList =
                    ColorStateList.valueOf(
                        Color.parseColor(
                            colorValue
                        )
                    )

                setOnClickListener {
                    action()
                }
            }

        button.layoutParams =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {

                topMargin =
                    dp(8)
            }

        return button
    }

    private fun smallButton(
        textValue: String,
        colorValue: String,
        action: () -> Unit
    ): Button {

        return Button(this).apply {

            text =
                textValue

            textSize =
                14f

            minHeight =
                dp(50)

            setTextColor(
                Color.WHITE
            )

            backgroundTintList =
                ColorStateList.valueOf(
                    Color.parseColor(
                        colorValue
                    )
                )

            setOnClickListener {
                action()
            }
        }
    }

    private fun infoText():
        TextView {

        return TextView(this).apply {

            textSize =
                14f

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
    }

    private fun labelText(
        value: String
    ): TextView {

        return TextView(this).apply {

            text =
                value

            textSize =
                13f

            setTypeface(
                null,
                Typeface.BOLD
            )

            setTextColor(
                Color.parseColor(
                    "#334E68"
                )
            )

            setPadding(
                0,
                dp(8),
                0,
                dp(4)
            )
        }
    }

    private fun createSpinnerAdapter(
        items: List<String>
    ): ArrayAdapter<String> {

        return ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            items
        ).apply {

            setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
            )
        }
    }

    private fun spinnerLayoutParams():
        LinearLayout.LayoutParams {

        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(52)
        )
    }

    private fun marginParams(
        top: Int
    ): LinearLayout.LayoutParams {

        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {

            topMargin =
                dp(top)
        }
    }

    private fun weightedParams(
        leftDp: Int = 0
    ): LinearLayout.LayoutParams {

        return LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1f
        ).apply {

            leftMargin =
                dp(
                    leftDp
                )

            topMargin =
                dp(6)
        }
    }

    private fun roundedBackground(
        colorValue: String
    ): GradientDrawable {

        return GradientDrawable().apply {

            setColor(
                Color.parseColor(
                    colorValue
                )
            )

            cornerRadius =
                dp(12)
                    .toFloat()
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
}
