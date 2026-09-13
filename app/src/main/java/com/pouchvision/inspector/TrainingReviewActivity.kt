package com.pouchvision.inspector

import android.app.Dialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.view.Window
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
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min

class TrainingReviewActivity : AppCompatActivity() {

    companion object {

        private const val FILTER_ALL =
            "전체"

        private const val FILTER_UNLABELED =
            "미분류만"

        private const val FILTER_MISMATCH =
            "AI와 정답 불일치"
    }

    private lateinit var root: LinearLayout
    private lateinit var switchTraining: Switch
    private lateinit var spinnerType: Spinner
    private lateinit var spinnerViewMode: Spinner

    private lateinit var tvSummary: TextView
    private lateinit var tvPosition: TextView
    private lateinit var imageView: ImageView
    private lateinit var tvImageHint: TextView
    private lateinit var tvInfo: TextView
    private lateinit var tvDetails: TextView
    private lateinit var editNote: EditText

    private lateinit var bottomCornerGroundTruthBox:
        LinearLayout

    private lateinit var normalLabelBox:
        LinearLayout

    private var allRecords =
        emptyList<
            TrainingDataStore.TrainingRecord
        >()

    private var filteredRecords =
        emptyList<
            TrainingDataStore.TrainingRecord
        >()

    private var currentIndex =
        0

    private val typeItems =
        listOf(
            FILTER_ALL,
            "BOTTOM CORNER",
            "SEAL",
            "FORMING",
            "TAB",
            "DISASSEMBLY"
        )

    private val viewModes =
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

            if (
                uri == null
            ) {
                return@registerForActivityResult
            }

            try {

                contentResolver
                    .openOutputStream(
                        uri
                    )
                    ?.use { output ->

                        TrainingDataStore
                            .exportZip(
                                this,
                                output
                            )
                    }
                    ?: throw Exception(
                        "파일을 열 수 없습니다."
                    )

                Toast.makeText(
                    this,
                    "학습 데이터 ZIP 저장 완료",
                    Toast.LENGTH_LONG
                ).show()

            } catch (
                e: Exception
            ) {

                Toast.makeText(
                    this,
                    "Export 실패: ${
                        e.message ?: "알 수 없는 오류"
                    }",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(
            savedInstanceState
        )

        setContentView(
            createScreen()
        )

        ViewCompat
            .setOnApplyWindowInsetsListener(
                root
            ) { view, insets ->

                val bars =
                    insets.getInsets(
                        WindowInsetsCompat
                            .Type
                            .systemBars()
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
            TrainingDataStore
                .isEnabled(
                    this
                )

        switchTraining
            .setOnCheckedChangeListener {
                    _,
                    checked ->

                TrainingDataStore
                    .setEnabled(
                        this,
                        checked
                    )

                if (
                    checked
                ) {

                    syncNow()

                } else {

                    renderSummary()
                }
            }

        if (
            TrainingDataStore
                .isEnabled(
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
            TrainingDataStore
                .isEnabled(
                    this
                )
        ) {

            syncNow(
                showToast = false
            )
        }
    }

    private fun createScreen():
        View {

        val scroll =
            ScrollView(
                this
            ).apply {

                isFillViewport =
                    true

                setBackgroundColor(
                    Color.parseColor(
                        "#F4F6F8"
                    )
                )
            }

        root =
            LinearLayout(
                this
            ).apply {

                orientation =
                    LinearLayout.VERTICAL
            }

        root.addView(
            header()
        )

        val content =
            LinearLayout(
                this
            ).apply {

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    dp(14),
                    dp(14),
                    dp(14),
                    dp(28)
                )
            }

        switchTraining =
            Switch(
                this
            ).apply {

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

        val syncButton =
            actionButton(
                text =
                    "현재 검사이력 가져오기",

                color =
                    "#123E63"
            ) {

                syncNow()
            }

        val trainingModeBox =
            LinearLayout(
                this
            ).apply {

                orientation =
                    LinearLayout.VERTICAL

                addView(
                    switchTraining
                )

                addView(
                    TextView(
                        this@TrainingReviewActivity
                    ).apply {

                        text =
                            "검사 결과 이미지를 학습용 폴더에 별도 보관합니다.\n" +
                                "Bottom Corner는 실제 주름 개수를 정답으로 지정합니다."

                        textSize =
                            13f

                        setTextColor(
                            Color.parseColor(
                                "#627D98"
                            )
                        )

                        setPadding(
                            0,
                            dp(8),
                            0,
                            dp(6)
                        )
                    }
                )

                addView(
                    syncButton
                )
            }

        content.addView(
            card(
                "학습 모드",
                trainingModeBox
            )
        )

        spinnerType =
            Spinner(
                this
            )

        spinnerViewMode =
            Spinner(
                this
            )

        val filterBox =
            LinearLayout(
                this
            ).apply {

                orientation =
                    LinearLayout.VERTICAL

                addView(
                    smallLabel(
                        "검사 항목"
                    )
                )

                addView(
                    spinnerType,
                    spinnerParams()
                )

                addView(
                    smallLabel(
                        "보기 방식"
                    )
                )

                addView(
                    spinnerViewMode,
                    spinnerParams()
                )
            }

        content.addView(
            card(
                "학습 데이터 필터",
                filterBox
            )
        )

        tvSummary =
            infoText()

        content.addView(
            card(
                "학습 현황",
                tvSummary
            )
        )

        tvPosition =
            TextView(
                this
            ).apply {

                textSize =
                    15f

                gravity =
                    Gravity.CENTER

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
            ImageView(
                this
            ).apply {

                adjustViewBounds =
                    true

                scaleType =
                    ImageView.ScaleType
                        .FIT_CENTER

                setBackgroundColor(
                    Color.parseColor(
                        "#E9EEF3"
                    )
                )

                minimumHeight =
                    dp(300)

                isClickable =
                    true

                isFocusable =
                    true

                setOnClickListener {

                    openCurrentImageFullscreen()
                }
            }

        tvImageHint =
            TextView(
                this
            ).apply {

                text =
                    "🔍 사진을 터치하면 전체화면으로 확대할 수 있습니다."

                textSize =
                    12f

                gravity =
                    Gravity.CENTER

                setTextColor(
                    Color.parseColor(
                        "#486581"
                    )
                )

                setPadding(
                    0,
                    dp(7),
                    0,
                    dp(2)
                )
            }

        tvInfo =
            infoText()

        tvDetails =
            infoText()
                .apply {

                    textSize =
                        12f

                    setTextColor(
                        Color.parseColor(
                            "#627D98"
                        )
                    )

                    maxLines =
                        14
                }

        editNote =
            EditText(
                this
            ).apply {

                hint =
                    "라벨 메모 (선택)"

                minHeight =
                    dp(50)

                textSize =
                    14f

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
                    rounded(
                        "#F4F6F8",
                        8
                    )

                setPadding(
                    dp(12),
                    0,
                    dp(12),
                    0
                )
            }

        bottomCornerGroundTruthBox =
            createBottomCornerGroundTruthBox()

        normalLabelBox =
            createNormalLabelBox()

        val reviewBox =
            LinearLayout(
                this
            ).apply {

                orientation =
                    LinearLayout.VERTICAL

                addView(
                    tvPosition
                )

                addView(
                    imageView,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(330)
                    ).apply {

                        topMargin =
                            dp(8)
                    }
                )

                addView(
                    tvImageHint
                )

                addView(
                    tvInfo,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {

                        topMargin =
                            dp(10)
                    }
                )

                addView(
                    tvDetails,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {

                        topMargin =
                            dp(8)
                    }
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
                    bottomCornerGroundTruthBox
                )

                addView(
                    normalLabelBox
                )

                addView(
                    navigationRow()
                )

                addView(
                    actionButton(
                        "현재 정답 지우기",
                        "#7B8794"
                    ) {

                        clearCurrentLabel()
                    }
                )
            }

        content.addView(
            card(
                "정답 Ground Truth 지정",
                reviewBox
            )
        )

        content.addView(
            actionButton(
                "학습 데이터 ZIP Export",
                "#0F6B50"
            ) {

                exportZip()
            }
        )

        content.addView(
            actionButton(
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

    /*
     * =========================================================
     * 전체화면 이미지 확대
     * =========================================================
     */

    private fun openCurrentImageFullscreen() {

        val record =
            filteredRecords
                .getOrNull(
                    currentIndex
                )
                ?: return

        val imageFile =
            File(
                record.imagePath
            )

        if (
            !imageFile.exists()
        ) {

            Toast.makeText(
                this,
                "학습 이미지를 찾을 수 없습니다.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        val bitmap =
            BitmapFactory.decodeFile(
                imageFile.absolutePath
            )
                ?: run {

                    Toast.makeText(
                        this,
                        "이미지를 불러올 수 없습니다.",
                        Toast.LENGTH_SHORT
                    ).show()

                    return
                }

        showFullscreenImageDialog(
            record,
            bitmap
        )
    }

    private fun showFullscreenImageDialog(
        record: TrainingDataStore.TrainingRecord,
        bitmap: Bitmap
    ) {

        val dialog =
            Dialog(
                this,
                android.R.style
                    .Theme_Black_NoTitleBar_Fullscreen
            )

        dialog.requestWindowFeature(
            Window.FEATURE_NO_TITLE
        )

        val main =
            LinearLayout(
                this
            ).apply {

                orientation =
                    LinearLayout.VERTICAL

                setBackgroundColor(
                    Color.BLACK
                )

                setPadding(
                    dp(8),
                    dp(8),
                    dp(8),
                    dp(8)
                )
            }

        /*
         * 상단
         */
        val topRow =
            LinearLayout(
                this
            ).apply {

                orientation =
                    LinearLayout.HORIZONTAL

                gravity =
                    Gravity.CENTER_VERTICAL
            }

        val title =
            TextView(
                this
            ).apply {

                text =
                    "Bottom Corner 확대 검사"

                textSize =
                    17f

                setTypeface(
                    null,
                    Typeface.BOLD
                )

                setTextColor(
                    Color.WHITE
                )

                gravity =
                    Gravity.CENTER_VERTICAL
            }

        topRow.addView(
            title,
            LinearLayout.LayoutParams(
                0,
                dp(48),
                1f
            )
        )

        val resetButton =
            Button(
                this
            ).apply {

                text =
                    "원본"

                isAllCaps =
                    false

                textSize =
                    12f

                setTextColor(
                    Color.WHITE
                )

                backgroundTintList =
                    android.content.res
                        .ColorStateList
                        .valueOf(
                            Color.parseColor(
                                "#486581"
                            )
                        )
            }

        topRow.addView(
            resetButton,
            LinearLayout.LayoutParams(
                dp(72),
                dp(44)
            )
        )

        main.addView(
            topRow
        )

        /*
         * 사용 안내
         */
        val guide =
            TextView(
                this
            ).apply {

                text =
                    "두 손가락 확대/축소 · 확대 후 드래그 이동 · 더블탭 확대/원상복귀"

                textSize =
                    11f

                gravity =
                    Gravity.CENTER

                setTextColor(
                    Color.parseColor(
                        "#D9E2EC"
                    )
                )

                setPadding(
                    0,
                    dp(2),
                    0,
                    dp(6)
                )
            }

        main.addView(
            guide
        )

        /*
         * 확대 이미지
         */
        val zoomView =
            ZoomableImageView(
                this
            ).apply {

                setBackgroundColor(
                    Color.BLACK
                )

                setImageBitmap(
                    bitmap
                )
            }

        main.addView(
            zoomView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        resetButton
            .setOnClickListener {

                zoomView.resetZoom()
            }

        /*
         * 현재 Ground Truth 표시
         */
        val gtText =
            TextView(
                this
            ).apply {

                textSize =
                    14f

                gravity =
                    Gravity.CENTER

                setTypeface(
                    null,
                    Typeface.BOLD
                )

                setTextColor(
                    Color.WHITE
                )

                setPadding(
                    0,
                    dp(8),
                    0,
                    dp(5)
                )
            }

        fun refreshGroundTruthText() {

            val refreshed =
                TrainingDataStore
                    .load(
                        this
                    )
                    .firstOrNull {

                        it.sourceId ==
                            record.sourceId
                    }

            val countText =
                refreshed
                    ?.wrinkleCountText
                    ?: "미지정"

            val labelText =
                refreshed
                    ?.trueLabel
                    ?.ifBlank {
                        "미지정"
                    }
                    ?: "미지정"

            gtText.text =
                "실제 주름 : $countText   |   정답 : $labelText"
        }

        refreshGroundTruthText()

        main.addView(
            gtText
        )

        /*
         * 확대 화면에서도 바로 Ground Truth 입력
         */
        if (
            record.isBottomCorner
        ) {

            val row1 =
                LinearLayout(
                    this
                ).apply {

                    orientation =
                        LinearLayout.HORIZONTAL
                }

            row1.addView(
                fullscreenGtButton(
                    "0개\n정상",
                    "#2E7D32"
                ) {

                    saveWrinkleFromFullscreen(
                        record.sourceId,
                        TrainingDataStore
                            .WRINKLE_COUNT_0
                    )

                    refreshGroundTruthText()
                },
                fullscreenWeight()
            )

            row1.addView(
                fullscreenGtButton(
                    "1개\n정상",
                    "#388E3C"
                ) {

                    saveWrinkleFromFullscreen(
                        record.sourceId,
                        TrainingDataStore
                            .WRINKLE_COUNT_1
                    )

                    refreshGroundTruthText()
                },
                fullscreenWeight(
                    dp(5)
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
                fullscreenGtButton(
                    "2개\n한계정상",
                    "#C47A00"
                ) {

                    saveWrinkleFromFullscreen(
                        record.sourceId,
                        TrainingDataStore
                            .WRINKLE_COUNT_2
                    )

                    refreshGroundTruthText()
                },
                fullscreenWeight()
            )

            row2.addView(
                fullscreenGtButton(
                    "3개 이상\n불량",
                    "#B42318"
                ) {

                    saveWrinkleFromFullscreen(
                        record.sourceId,
                        TrainingDataStore
                            .WRINKLE_COUNT_3_PLUS
                    )

                    refreshGroundTruthText()
                },
                fullscreenWeight(
                    dp(5)
                )
            )

            main.addView(
                row1
            )

            main.addView(
                row2
            )
        }

        /*
         * 닫기
         */
        val closeButton =
            Button(
                this
            ).apply {

                text =
                    "닫기"

                isAllCaps =
                    false

                textSize =
                    14f

                setTextColor(
                    Color.WHITE
                )

                backgroundTintList =
                    android.content.res
                        .ColorStateList
                        .valueOf(
                            Color.parseColor(
                                "#486581"
                            )
                        )

                setOnClickListener {

                    dialog.dismiss()
                }
            }

        main.addView(
            closeButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(50)
            ).apply {

                topMargin =
                    dp(7)
            }
        )

        dialog.setContentView(
            main
        )

        dialog.window
            ?.apply {

                setBackgroundDrawable(
                    ColorDrawable(
                        Color.BLACK
                    )
                )

                setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }

        dialog.setOnDismissListener {

            allRecords =
                TrainingDataStore
                    .load(
                        this
                    )

            applyFilter(
                preferredId =
                    record.sourceId
            )
        }

        dialog.show()

        dialog.window
            ?.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
    }

    private fun saveWrinkleFromFullscreen(
        sourceId: Long,
        countCode: Int
    ) {

        val ok =
            TrainingDataStore
                .setWrinkleCountGroundTruth(
                    context = this,
                    sourceId = sourceId,
                    countCode = countCode,
                    note =
                        editNote.text
                            ?.toString()
                            .orEmpty()
                )

        if (
            ok
        ) {

            val countText =
                TrainingDataStore
                    .wrinkleCountText(
                        countCode
                    )

            val label =
                TrainingDataStore
                    .labelFromWrinkleCount(
                        countCode
                    )

            Toast.makeText(
                this,
                "실제 주름 $countText → $label 저장",
                Toast.LENGTH_SHORT
            ).show()

            allRecords =
                TrainingDataStore
                    .load(
                        this
                    )

            renderSummary()

        } else {

            Toast.makeText(
                this,
                "Ground Truth 저장 실패",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun fullscreenGtButton(
        text: String,
        color: String,
        action: () -> Unit
    ): Button {

        return Button(
            this
        ).apply {

            this.text =
                text

            isAllCaps =
                false

            textSize =
                12f

            gravity =
                Gravity.CENTER

            setTextColor(
                Color.WHITE
            )

            backgroundTintList =
                android.content.res
                    .ColorStateList
                    .valueOf(
                        Color.parseColor(
                            color
                        )
                    )

            setOnClickListener {

                action()
            }
        }
    }

    private fun fullscreenWeight(
        leftMarginPx: Int = 0
    ): LinearLayout.LayoutParams {

        return LinearLayout.LayoutParams(
            0,
            dp(52),
            1f
        ).apply {

            leftMargin =
                leftMarginPx

            topMargin =
                dp(4)
        }
    }

    /*
     * =========================================================
     * Bottom Corner Ground Truth UI
     * =========================================================
     */

    private fun createBottomCornerGroundTruthBox():
        LinearLayout {

        val outer =
            LinearLayout(
                this
            ).apply {

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    0,
                    dp(14),
                    0,
                    dp(4)
                )
            }

        outer.addView(
            TextView(
                this
            ).apply {

                text =
                    "Bottom Corner 실제 주름 개수"

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
        )

        outer.addView(
            TextView(
                this
            ).apply {

                text =
                    "Master 기준\n" +
                        "0개 = 정상  |  1개 = 정상\n" +
                        "2개 = 한계정상  |  3개 이상 = 불량"

                textSize =
                    13f

                setTextColor(
                    Color.parseColor(
                        "#486581"
                    )
                )

                setPadding(
                    0,
                    dp(4),
                    0,
                    dp(8)
                )
            }
        )

        val row1 =
            LinearLayout(
                this
            ).apply {

                orientation =
                    LinearLayout.HORIZONTAL
            }

        row1.addView(
            compactButton(
                "주름 0개\n정상",
                "#2E7D32"
            ) {

                setCurrentWrinkleCount(
                    TrainingDataStore
                        .WRINKLE_COUNT_0
                )
            },
            weightParams()
        )

        row1.addView(
            compactButton(
                "주름 1개\n정상",
                "#388E3C"
            ) {

                setCurrentWrinkleCount(
                    TrainingDataStore
                        .WRINKLE_COUNT_1
                )
            },
            weightParams(
                6
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
            compactButton(
                "주름 2개\n한계정상",
                "#C47A00"
            ) {

                setCurrentWrinkleCount(
                    TrainingDataStore
                        .WRINKLE_COUNT_2
                )
            },
            weightParams()
        )

        row2.addView(
            compactButton(
                "주름 3개 이상\n불량",
                "#B42318"
            ) {

                setCurrentWrinkleCount(
                    TrainingDataStore
                        .WRINKLE_COUNT_3_PLUS
                )
            },
            weightParams(
                6
            )
        )

        outer.addView(
            row1
        )

        outer.addView(
            row2
        )

        return outer
    }

    /*
     * =========================================================
     * 일반 검사 정답 UI
     * =========================================================
     */

    private fun createNormalLabelBox():
        LinearLayout {

        val outer =
            LinearLayout(
                this
            ).apply {

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    0,
                    dp(12),
                    0,
                    dp(4)
                )
            }

        outer.addView(
            TextView(
                this
            ).apply {

                text =
                    "작업자 실제 정답"

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

                setPadding(
                    0,
                    0,
                    0,
                    dp(6)
                )
            }
        )

        val row1 =
            LinearLayout(
                this
            ).apply {

                orientation =
                    LinearLayout.HORIZONTAL
            }

        row1.addView(
            compactButton(
                "정상",
                "#2E7D32"
            ) {

                setCurrentLabel(
                    TrainingDataStore
                        .LABEL_NORMAL
                )
            },
            weightParams()
        )

        row1.addView(
            compactButton(
                "주의",
                "#C47A00"
            ) {

                setCurrentLabel(
                    TrainingDataStore
                        .LABEL_WARNING
                )
            },
            weightParams(
                6
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
            compactButton(
                "한계정상",
                "#B45309"
            ) {

                setCurrentLabel(
                    TrainingDataStore
                        .LABEL_LIMIT
                )
            },
            weightParams()
        )

        row2.addView(
            compactButton(
                "불량",
                "#B42318"
            ) {

                setCurrentLabel(
                    TrainingDataStore
                        .LABEL_NG
                )
            },
            weightParams(
                6
            )
        )

        outer.addView(
            row1
        )

        outer.addView(
            row2
        )

        return outer
    }

    private fun setupSpinners() {

        spinnerType.adapter =
            spinnerAdapter(
                typeItems
            )

        spinnerViewMode.adapter =
            spinnerAdapter(
                viewModes
            )

        spinnerType
            .onItemSelectedListener =
            simpleItemSelectedListener {

                applyFilter()
            }

        spinnerViewMode
            .onItemSelectedListener =
            simpleItemSelectedListener {

                applyFilter()
            }
    }

    private fun syncNow(
        showToast: Boolean =
            true
    ) {

        val added =
            TrainingDataStore
                .syncFromInspectionHistory(
                    this
                )

        reload()

        if (
            showToast
        ) {

            Toast.makeText(
                this,
                if (
                    added > 0
                ) {

                    "학습 데이터 ${added}건을 추가했습니다."

                } else {

                    "새로 가져올 검사 이미지가 없습니다."
                },
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun reload() {

        val keepId =
            filteredRecords
                .getOrNull(
                    currentIndex
                )
                ?.sourceId

        allRecords =
            TrainingDataStore
                .load(
                    this
                )

        applyFilter(
            preferredId =
                keepId
        )
    }

    private fun applyFilter(
        preferredId: Long? =
            null
    ) {

        if (
            !::spinnerType.isInitialized ||
            !::spinnerViewMode.isInitialized
        ) {
            return
        }

        val type =
            spinnerType
                .selectedItem
                ?.toString()
                ?: FILTER_ALL

        val mode =
            spinnerViewMode
                .selectedItem
                ?.toString()
                ?: "전체 데이터"

        filteredRecords =
            allRecords
                .filter {

                    type ==
                        FILTER_ALL ||

                        it.inspectionType
                            .equals(
                                type,
                                ignoreCase =
                                    true
                            )
                }
                .filter {

                    when (
                        mode
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

                filteredRecords
                    .indexOfFirst {

                        it.sourceId ==
                            preferredId
                    }
                    .takeIf {

                        it >= 0
                    }
                    ?: 0

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
                ?.takeIf {

                    it !=
                        FILTER_ALL
                }

        val counts =
            TrainingDataStore
                .countByLabel(
                    this,
                    selectedType
                )

        val wrinkleCounts =
            TrainingDataStore
                .countByWrinkleGroundTruth(
                    this
                )

        val bottomCornerRecords =
            allRecords.filter {

                it.isBottomCorner
            }

        val wrinkleGtDone =
            bottomCornerRecords.count {

                it.hasWrinkleCountGroundTruth
            }

        tvSummary.text =
            buildString {

                append(
                    "수집 : ${total}건  |  "
                )

                append(
                    "라벨완료 : ${labeled}건  |  "
                )

                append(
                    "미분류 : ${total - labeled}건\n"
                )

                append(
                    "AI/정답 불일치 : ${mismatch}건\n\n"
                )

                append(
                    "정상 ${
                        counts[
                            TrainingDataStore
                                .LABEL_NORMAL
                        ] ?: 0
                    }"
                )

                append(
                    "  |  주의 ${
                        counts[
                            TrainingDataStore
                                .LABEL_WARNING
                        ] ?: 0
                    }"
                )

                append(
                    "  |  한계정상 ${
                        counts[
                            TrainingDataStore
                                .LABEL_LIMIT
                        ] ?: 0
                    }"
                )

                append(
                    "  |  불량 ${
                        counts[
                            TrainingDataStore
                                .LABEL_NG
                        ] ?: 0
                    }"
                )

                if (
                    bottomCornerRecords.isNotEmpty()
                ) {

                    append(
                        "\n\n"
                    )

                    append(
                        "Bottom Corner 주름 개수 Ground Truth\n"
                    )

                    append(
                        "입력완료 : ${wrinkleGtDone}" +
                            " / ${bottomCornerRecords.size}건\n"
                    )

                    append(
                        "0개 ${
                            wrinkleCounts[
                                TrainingDataStore
                                    .WRINKLE_COUNT_0
                            ] ?: 0
                        }"
                    )

                    append(
                        "  |  1개 ${
                            wrinkleCounts[
                                TrainingDataStore
                                    .WRINKLE_COUNT_1
                            ] ?: 0
                        }"
                    )

                    append(
                        "  |  2개 ${
                            wrinkleCounts[
                                TrainingDataStore
                                    .WRINKLE_COUNT_2
                            ] ?: 0
                        }"
                    )

                    append(
                        "  |  3개+ ${
                            wrinkleCounts[
                                TrainingDataStore
                                    .WRINKLE_COUNT_3_PLUS
                            ] ?: 0
                        }"
                    )
                }
            }
    }

    private fun renderCurrent() {

        if (
            filteredRecords.isEmpty()
        ) {

            tvPosition.text =
                "표시할 학습 데이터가 없습니다."

            imageView
                .setImageDrawable(
                    null
                )

            tvInfo.text =
                "검사 후 결과를 저장하고\n" +
                    "'현재 검사이력 가져오기'를 눌러주세요."

            tvDetails.text =
                ""

            editNote.setText(
                ""
            )

            bottomCornerGroundTruthBox
                .visibility =
                View.GONE

            normalLabelBox
                .visibility =
                View.GONE

            return
        }

        currentIndex =
            currentIndex
                .coerceIn(
                    0,
                    filteredRecords.lastIndex
                )

        val record =
            filteredRecords[
                currentIndex
            ]

        tvPosition.text =
            "${currentIndex + 1} / ${filteredRecords.size}"

        val file =
            File(
                record.imagePath
            )

        if (
            file.exists()
        ) {

            imageView
                .setImageBitmap(
                    BitmapFactory
                        .decodeFile(
                            file.absolutePath
                        )
                )

        } else {

            imageView
                .setImageDrawable(
                    null
                )
        }

        if (
            record.isBottomCorner
        ) {

            bottomCornerGroundTruthBox
                .visibility =
                View.VISIBLE

            normalLabelBox
                .visibility =
                View.GONE

        } else {

            bottomCornerGroundTruthBox
                .visibility =
                View.GONE

            normalLabelBox
                .visibility =
                View.VISIBLE
        }

        val mismatchText =
            if (
                record.isMismatch
            ) {

                "⚠ AI 판정과 정답이 다릅니다."

            } else if (
                record.isLabeled
            ) {

                "✅ AI 판정과 정답이 일치합니다."

            } else {

                "정답 라벨 미지정"
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

                if (
                    record.isBottomCorner
                ) {

                    append(
                        "실제 주름 개수 : ${
                            record.wrinkleCountText
                        }\n"
                    )
                }

                append(
                    mismatchText
                )
            }

        tvDetails.text =
            buildString {

                append(
                    "검사시간 : ${
                        record.dateTime
                    }\n"
                )

                if (
                    record.isBottomCorner
                ) {

                    append(
                        "\n[Bottom Corner Master 기준]\n"
                    )

                    append(
                        "0개 = 정상\n"
                    )

                    append(
                        "1개 = 정상\n"
                    )

                    append(
                        "2개 = 한계정상\n"
                    )

                    append(
                        "3개 이상 = 불량\n\n"
                    )
                }

                append(
                    record.details
                )
            }

        editNote.setText(
            record.note
        )
    }

    private fun setCurrentWrinkleCount(
        countCode: Int
    ) {

        val record =
            filteredRecords
                .getOrNull(
                    currentIndex
                )
                ?: return

        if (
            !record.isBottomCorner
        ) {

            Toast.makeText(
                this,
                "Bottom Corner 데이터가 아닙니다.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        val ok =
            TrainingDataStore
                .setWrinkleCountGroundTruth(
                    context = this,
                    sourceId = record.sourceId,
                    countCode = countCode,
                    note =
                        editNote.text
                            ?.toString()
                            .orEmpty()
                )

        if (
            !ok
        ) {

            Toast.makeText(
                this,
                "주름 개수 저장 실패",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        val countText =
            TrainingDataStore
                .wrinkleCountText(
                    countCode
                )

        val label =
            TrainingDataStore
                .labelFromWrinkleCount(
                    countCode
                )

        Toast.makeText(
            this,
            "실제 주름 : $countText → $label",
            Toast.LENGTH_SHORT
        ).show()

        val currentId =
            record.sourceId

        allRecords =
            TrainingDataStore
                .load(
                    this
                )

        applyFilter(
            preferredId =
                currentId
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

        val ok =
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

        if (
            ok
        ) {

            Toast.makeText(
                this,
                "정답 라벨 : $label",
                Toast.LENGTH_SHORT
            ).show()

            val currentId =
                record.sourceId

            allRecords =
                TrainingDataStore
                    .load(
                        this
                    )

            applyFilter(
                preferredId =
                    currentId
            )
        }
    }

    private fun clearCurrentLabel() {

        val record =
            filteredRecords
                .getOrNull(
                    currentIndex
                )
                ?: return

        val ok =
            TrainingDataStore
                .clearLabel(
                    this,
                    record.sourceId
                )

        if (
            ok
        ) {

            Toast.makeText(
                this,
                "현재 정답을 지웠습니다.",
                Toast.LENGTH_SHORT
            ).show()
        }

        allRecords =
            TrainingDataStore
                .load(
                    this
                )

        applyFilter(
            preferredId =
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

    private fun navigationRow():
        LinearLayout {

        return LinearLayout(
            this
        ).apply {

            orientation =
                LinearLayout.HORIZONTAL

            addView(
                compactButton(
                    "← 이전",
                    "#486581"
                ) {

                    move(
                        -1
                    )
                },
                weightParams()
            )

            addView(
                compactButton(
                    "다음 →",
                    "#486581"
                ) {

                    move(
                        1
                    )
                },
                weightParams(
                    6
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

    private fun header():
        View {

        return LinearLayout(
            this
        ).apply {

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
                rounded(
                    "#FFFFFF",
                    14
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

            layoutParams =
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {

                    bottomMargin =
                        dp(12)
                }
        }
    }

    private fun infoText():
        TextView {

        return TextView(
            this
        ).apply {

            textSize =
                14f

            setTextColor(
                Color.parseColor(
                    "#334E68"
                )
            )

            setLineSpacing(
                0f,
                1.12f
            )
        }
    }

    private fun smallLabel(
        text: String
    ): TextView {

        return TextView(
            this
        ).apply {

            this.text =
                text

            textSize =
                13f

            setTypeface(
                null,
                Typeface.BOLD
            )

            setTextColor(
                Color.parseColor(
                    "#486581"
                )
            )

            setPadding(
                0,
                dp(6),
                0,
                dp(4)
            )
        }
    }

    private fun actionButton(
        text: String,
        color: String,
        action: () -> Unit
    ): Button {

        return Button(
            this
        ).apply {

            this.text =
                text

            isAllCaps =
                false

            textSize =
                14f

            setTextColor(
                Color.WHITE
            )

            backgroundTintList =
                android.content.res
                    .ColorStateList
                    .valueOf(
                        Color.parseColor(
                            color
                        )
                    )

            setOnClickListener {

                action()
            }

            layoutParams =
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(52)
                ).apply {

                    topMargin =
                        dp(8)
                }
        }
    }

    private fun compactButton(
        text: String,
        color: String,
        action: () -> Unit
    ): Button {

        return Button(
            this
        ).apply {

            this.text =
                text

            isAllCaps =
                false

            textSize =
                13f

            gravity =
                Gravity.CENTER

            setTextColor(
                Color.WHITE
            )

            backgroundTintList =
                android.content.res
                    .ColorStateList
                    .valueOf(
                        Color.parseColor(
                            color
                        )
                    )

            setOnClickListener {

                action()
            }
        }
    }

    private fun spinnerAdapter(
        items: List<String>
    ): ArrayAdapter<String> {

        return ArrayAdapter(
            this,
            android.R.layout
                .simple_spinner_item,
            items
        ).apply {

            setDropDownViewResource(
                android.R.layout
                    .simple_spinner_dropdown_item
            )
        }
    }

    private fun simpleItemSelectedListener(
        action: () -> Unit
    ): AdapterView.OnItemSelectedListener {

        return object :
            AdapterView.OnItemSelectedListener {

            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {

                action()
            }

            override fun onNothingSelected(
                parent: AdapterView<*>?
            ) {
            }
        }
    }

    private fun spinnerParams():
        LinearLayout.LayoutParams {

        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(52)
        ).apply {

            bottomMargin =
                dp(4)
        }
    }

    private fun weightParams(
        leftDp: Int =
            0
    ): LinearLayout.LayoutParams {

        return LinearLayout.LayoutParams(
            0,
            dp(60),
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

    private fun rounded(
        color: String,
        radiusDp: Int
    ): GradientDrawable {

        return GradientDrawable()
            .apply {

                shape =
                    GradientDrawable.RECTANGLE

                setColor(
                    Color.parseColor(
                        color
                    )
                )

                cornerRadius =
                    dp(
                        radiusDp
                    ).toFloat()
            }
    }

    private fun dp(
        value: Int
    ): Int {

        return (
            value *
                resources
                    .displayMetrics
                    .density +
                0.5f
            )
            .toInt()
    }

    /*
     * =========================================================
     * 확대 / 축소 ImageView
     * =========================================================
     *
     * - Pinch Zoom
     * - Drag
     * - Double Tap Zoom
     * - Reset
     *
     * 외부 Library를 사용하지 않으므로
     * build.gradle 변경이 필요 없습니다.
     * =========================================================
     */

    private class ZoomableImageView(
        context: Context
    ) : AppCompatImageView(
        context
    ) {

        private val drawMatrix =
            Matrix()

        private var relativeScale =
            1f

        private val minRelativeScale =
            1f

        private val maxRelativeScale =
            6f

        private var fittedScale =
            1f

        private var lastX =
            0f

        private var lastY =
            0f

        private var dragging =
            false

        private var initialized =
            false

        private val scaleDetector =
            ScaleGestureDetector(
                context,
                object :
                    ScaleGestureDetector
                        .SimpleOnScaleGestureListener() {

                    override fun onScale(
                        detector: ScaleGestureDetector
                    ): Boolean {

                        var factor =
                            detector.scaleFactor

                        val target =
                            relativeScale *
                                factor

                        if (
                            target <
                            minRelativeScale
                        ) {

                            factor =
                                minRelativeScale /
                                    relativeScale
                        }

                        if (
                            target >
                            maxRelativeScale
                        ) {

                            factor =
                                maxRelativeScale /
                                    relativeScale
                        }

                        relativeScale *=
                            factor

                        drawMatrix.postScale(
                            factor,
                            factor,
                            detector.focusX,
                            detector.focusY
                        )

                        fixTranslation()

                        imageMatrix =
                            drawMatrix

                        return true
                    }
                }
            )

        private val gestureDetector =
            GestureDetector(
                context,
                object :
                    GestureDetector
                        .SimpleOnGestureListener() {

                    override fun onDown(
                        e: MotionEvent
                    ): Boolean {

                        return true
                    }

                    override fun onDoubleTap(
                        e: MotionEvent
                    ): Boolean {

                        if (
                            relativeScale >
                            1.05f
                        ) {

                            resetZoom()

                        } else {

                            zoomTo(
                                2.5f,
                                e.x,
                                e.y
                            )
                        }

                        return true
                    }
                }
            )

        init {

            scaleType =
                ScaleType.MATRIX

            imageMatrix =
                drawMatrix

            setBackgroundColor(
                Color.BLACK
            )
        }

        override fun onSizeChanged(
            w: Int,
            h: Int,
            oldw: Int,
            oldh: Int
        ) {

            super.onSizeChanged(
                w,
                h,
                oldw,
                oldh
            )

            post {

                fitImageToView()
            }
        }

        override fun setImageBitmap(
            bm: Bitmap?
        ) {

            initialized =
                false

            super.setImageBitmap(
                bm
            )

            post {

                fitImageToView()
            }
        }

        fun resetZoom() {

            relativeScale =
                1f

            initialized =
                false

            fitImageToView()
        }

        private fun fitImageToView() {

            val d =
                drawable
                    ?: return

            if (
                width <= 0 ||
                height <= 0
            ) {
                return
            }

            val drawableWidth =
                d.intrinsicWidth
                    .toFloat()

            val drawableHeight =
                d.intrinsicHeight
                    .toFloat()

            if (
                drawableWidth <= 0f ||
                drawableHeight <= 0f
            ) {
                return
            }

            val scaleX =
                width.toFloat() /
                    drawableWidth

            val scaleY =
                height.toFloat() /
                    drawableHeight

            fittedScale =
                min(
                    scaleX,
                    scaleY
                )

            val scaledWidth =
                drawableWidth *
                    fittedScale

            val scaledHeight =
                drawableHeight *
                    fittedScale

            val dx =
                (
                    width -
                        scaledWidth
                    ) / 2f

            val dy =
                (
                    height -
                        scaledHeight
                    ) / 2f

            drawMatrix.reset()

            drawMatrix.postScale(
                fittedScale,
                fittedScale
            )

            drawMatrix.postTranslate(
                dx,
                dy
            )

            relativeScale =
                1f

            imageMatrix =
                drawMatrix

            initialized =
                true
        }

        private fun zoomTo(
            targetRelativeScale: Float,
            focusX: Float,
            focusY: Float
        ) {

            val safeTarget =
                targetRelativeScale
                    .coerceIn(
                        minRelativeScale,
                        maxRelativeScale
                    )

            val factor =
                safeTarget /
                    relativeScale

            relativeScale =
                safeTarget

            drawMatrix.postScale(
                factor,
                factor,
                focusX,
                focusY
            )

            fixTranslation()

            imageMatrix =
                drawMatrix
        }

        override fun onTouchEvent(
            event: MotionEvent
        ): Boolean {

            parent
                ?.requestDisallowInterceptTouchEvent(
                    true
                )

            gestureDetector
                .onTouchEvent(
                    event
                )

            scaleDetector
                .onTouchEvent(
                    event
                )

            when (
                event.actionMasked
            ) {

                MotionEvent.ACTION_DOWN -> {

                    lastX =
                        event.x

                    lastY =
                        event.y

                    dragging =
                        true
                }

                MotionEvent.ACTION_MOVE -> {

                    if (
                        dragging &&
                        !scaleDetector
                            .isInProgress
                    ) {

                        val dx =
                            event.x -
                                lastX

                        val dy =
                            event.y -
                                lastY

                        if (
                            relativeScale >
                            1.001f
                        ) {

                            drawMatrix
                                .postTranslate(
                                    dx,
                                    dy
                                )

                            fixTranslation()

                            imageMatrix =
                                drawMatrix
                        }

                        lastX =
                            event.x

                        lastY =
                            event.y
                    }
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {

                    dragging =
                        false
                }
            }

            return true
        }

        private fun fixTranslation() {

            val d =
                drawable
                    ?: return

            if (
                width <= 0 ||
                height <= 0
            ) {
                return
            }

            val rect =
                RectF(
                    0f,
                    0f,
                    d.intrinsicWidth
                        .toFloat(),
                    d.intrinsicHeight
                        .toFloat()
                )

            drawMatrix.mapRect(
                rect
            )

            var dx =
                0f

            var dy =
                0f

            if (
                rect.width() <=
                width
            ) {

                dx =
                    width / 2f -
                        rect.centerX()

            } else {

                if (
                    rect.left >
                    0f
                ) {

                    dx =
                        -rect.left

                } else if (
                    rect.right <
                    width
                ) {

                    dx =
                        width -
                            rect.right
                }
            }

            if (
                rect.height() <=
                height
            ) {

                dy =
                    height / 2f -
                        rect.centerY()

            } else {

                if (
                    rect.top >
                    0f
                ) {

                    dy =
                        -rect.top

                } else if (
                    rect.bottom <
                    height
                ) {

                    dy =
                        height -
                            rect.bottom
                }
            }

            drawMatrix.postTranslate(
                dx,
                dy
            )
        }
    }
}
