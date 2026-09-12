package com.pouchvision.inspector

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
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
 *
 * 중요
 * - 이 화면은 기준값을 수정하지 않습니다.
 * - InspectionSpecChangeHistoryStore에 저장된 이력만 읽어서 표시합니다.
 */
class InspectionSpecChangeHistoryActivity : AppCompatActivity() {

    private lateinit var historyContainer: LinearLayout
    private lateinit var summaryText: TextView

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
        val records =
            try {
                InspectionSpecChangeHistoryStore.getAll(this)
            } catch (_: Exception) {
                emptyList()
            }

        historyContainer.removeAllViews()

        summaryText.text =
            if (records.isEmpty()) {
                "저장된 기준 변경 이력이 없습니다."
            } else {
                "총 ${records.size}건의 기준 변경 이력"
            }

        if (records.isEmpty()) {
            historyContainer.addView(createEmptyCard())
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

    private fun createEmptyCard(): View {
        return TextView(this).apply {
            text =
                "아직 변경 기록이 없습니다.\n\n검사 기준 설정에서 기준값을 저장하거나 기본값으로 복원하면 이곳에 자동 기록됩니다."
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
