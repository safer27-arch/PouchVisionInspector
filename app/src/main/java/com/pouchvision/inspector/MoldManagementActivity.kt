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
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class MoldManagementActivity : AppCompatActivity() {

    private lateinit var root: LinearLayout

    private lateinit var tvContext: TextView
    private lateinit var tvShot: TextView
    private lateinit var tvClean: TextView
    private lateinit var tvReplace: TextView
    private lateinit var tvRisk: TextView
    private lateinit var tvHistory: TextView

    private lateinit var editMoldId: EditText
    private lateinit var editPunchId: EditText
    private lateinit var editCleanLimit: EditText
    private lateinit var editReplaceLimit: EditText
    private lateinit var editDirectShot: EditText
    private lateinit var editNote: EditText

    private val exportLauncher =
        registerForActivityResult(
            ActivityResultContracts.CreateDocument("text/csv")
        ) { uri ->
            if (uri == null) return@registerForActivityResult

            try {
                val current = ProductionContextStore.getCurrent(this)
                val csv = MoldMaintenanceStore.csvText(
                    this,
                    current.model,
                    current.line
                )

                contentResolver.openOutputStream(uri)?.use {
                    it.write(csv.toByteArray(Charsets.UTF_8))
                    it.flush()
                }

                Toast.makeText(
                    this,
                    "금형/Punch 이력을 CSV로 저장했습니다.",
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                Toast.makeText(
                    this,
                    "CSV 저장 실패: ${e.message ?: "알 수 없는 오류"}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(createScreen())

        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(
                left = bars.left,
                top = bars.top,
                right = bars.right,
                bottom = bars.bottom
            )
            insets
        }

        ViewCompat.requestApplyInsets(root)

        loadState()
    }

    override fun onResume() {
        super.onResume()
        if (::root.isInitialized) {
            loadState()
        }
    }

    private fun createScreen(): View {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#F4F6F8"))
            isFillViewport = true
        }

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        root.addView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(20), dp(18), dp(20), dp(18))
                setBackgroundColor(Color.parseColor("#102A43"))

                addView(TextView(this@MoldManagementActivity).apply {
                    text = "금형 / Punch 예방관리"
                    textSize = 24f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.WHITE)
                })

                addView(TextView(this@MoldManagementActivity).apply {
                    text = "Mold · Punch Shot / Cleaning / Replacement"
                    textSize = 13f
                    setTextColor(Color.parseColor("#D9E2EC"))
                    setPadding(0, dp(4), 0, 0)
                })
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(24))
        }

        tvContext = infoText()
        content.addView(card("현재 생산 조건", tvContext))

        editMoldId = input("예: M-01")
        editPunchId = input("예: P-01")
        editCleanLimit = numberInput("예: 1000")
        editReplaceLimit = numberInput("예: 50000")
        editDirectShot = numberInput("현재 누적 Shot 직접 입력")
        editNote = input("메모 (선택)")

        val settingBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(label("Mold ID"))
            addView(editMoldId)
            addView(label("Punch ID"))
            addView(editPunchId)
            addView(label("청소 기준 Shot"))
            addView(editCleanLimit)
            addView(label("교체 기준 Shot"))
            addView(editReplaceLimit)

            addView(actionButton("설정 저장", "#102A43") {
                saveSettings()
            })
        }
        content.addView(card("금형 / Punch 설정", settingBox))

        tvShot = bigStatusText()
        tvClean = infoText()
        tvReplace = infoText()
        tvRisk = infoText()

        val currentBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(tvShot)
            addView(tvClean)
            addView(tvReplace)
            addView(tvRisk)
        }
        content.addView(card("현재 상태", currentBox))

        val shotBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL

            val row = LinearLayout(this@MoldManagementActivity).apply {
                orientation = LinearLayout.HORIZONTAL

                addView(
                    actionButton("+100 Shot", "#123E63") {
                        addShot(100)
                    },
                    weightParams()
                )

                addView(
                    actionButton("+500 Shot", "#123E63") {
                        addShot(500)
                    },
                    weightParams(left = 6)
                )
            }

            addView(row)
            addView(editDirectShot)

            addView(
                actionButton("Shot 직접 입력 적용", "#486581") {
                    setDirectShot()
                }
            )

            addView(editNote)

            addView(
                actionButton("청소 완료", "#0F6B50") {
                    markCleaned()
                }
            )

            addView(
                actionButton("금형 / Punch 교체 완료", "#8A2D2D") {
                    markReplaced()
                }
            )
        }
        content.addView(card("Shot / Maintenance 입력", shotBox))

        tvHistory = infoText().apply {
            setTextIsSelectable(true)
        }

        val historyBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(tvHistory)

            addView(
                actionButton("CSV Export", "#334E68") {
                    exportCsv()
                }
            )
        }
        content.addView(card("최근 Maintenance 이력", historyBox))

        content.addView(
            actionButton("뒤로", "#486581") {
                finish()
            }
        )

        root.addView(content)

        scroll.addView(root)
        return scroll
    }

    private fun loadState() {
        val current = ProductionContextStore.getCurrent(this)
        val state = MoldMaintenanceStore.loadState(
            this,
            current.model,
            current.line
        )

        tvContext.text =
            "Model : ${current.model}\nLine : ${current.line}"

        editMoldId.setText(state.moldId)
        editPunchId.setText(state.punchId)
        editCleanLimit.setText(state.cleaningLimit.toString())
        editReplaceLimit.setText(state.replacementLimit.toString())

        tvShot.text =
            "현재 누적 Shot : ${state.totalShot}\n" +
                "청소 후 Shot : ${state.shotSinceClean}"

        val cleanRemain =
            (state.cleaningLimit - state.shotSinceClean)
                .coerceAtLeast(0)

        val replaceRemain =
            (state.replacementLimit - state.totalShot)
                .coerceAtLeast(0)

        tvClean.text =
            "청소 기준 : ${state.cleaningLimit} Shot\n" +
                "청소까지 잔여 : $cleanRemain Shot\n" +
                "최근 청소 : ${timeText(state.lastCleanTime)}"

        tvReplace.text =
            "교체 기준 : ${state.replacementLimit} Shot\n" +
                "교체까지 잔여 : $replaceRemain Shot\n" +
                "최근 교체 : ${timeText(state.lastReplaceTime)}"

        val cleanLevel =
            MoldMaintenanceStore.alertLevel(
                state.shotSinceClean,
                state.cleaningLimit
            )

        val replaceLevel =
            MoldMaintenanceStore.alertLevel(
                state.totalShot,
                state.replacementLimit
            )

        val risk =
            buildPreventiveQualityText(
                current.model,
                current.line
            )

        tvRisk.text =
            "금형 상태 : ${levelText(maxOf(cleanLevel, replaceLevel))}\n$risk"

        val recent =
            MoldMaintenanceStore.loadHistory(
                this,
                current.model,
                current.line
            )
                .take(10)

        tvHistory.text =
            if (recent.isEmpty()) {
                "아직 저장된 금형/Punch 이력이 없습니다."
            } else {
                recent.joinToString("\n\n") {
                    buildString {
                        append(it.dateTime)
                        append("\n")
                        append(it.action)
                        append(" | Shot ")
                        append(it.shotValue)
                        append("\n")
                        append("Mold ${it.moldId} | Punch ${it.punchId}")
                        if (it.note.isNotBlank()) {
                            append("\n메모 : ")
                            append(it.note)
                        }
                    }
                }
            }

        maybeSendPreventiveAlerts(state)
    }

    private fun saveSettings() {
        val current = ProductionContextStore.getCurrent(this)
        val old = MoldMaintenanceStore.loadState(
            this,
            current.model,
            current.line
        )

        val moldId = editMoldId.text.toString().trim()
            .ifBlank { "M-01" }

        val punchId = editPunchId.text.toString().trim()
            .ifBlank { "P-01" }

        val cleanLimit =
            editCleanLimit.text.toString()
                .toIntOrNull()
                ?.coerceAtLeast(1)
                ?: 1000

        val replaceLimit =
            editReplaceLimit.text.toString()
                .toIntOrNull()
                ?.coerceAtLeast(cleanLimit)
                ?: 50000

        MoldMaintenanceStore.saveSettings(
            this,
            old.copy(
                moldId = moldId,
                punchId = punchId,
                cleaningLimit = cleanLimit,
                replacementLimit = replaceLimit
            )
        )

        Toast.makeText(
            this,
            "금형/Punch 설정을 저장했습니다.",
            Toast.LENGTH_SHORT
        ).show()

        loadState()
    }

    private fun addShot(amount: Int) {
        val current = ProductionContextStore.getCurrent(this)

        MoldMaintenanceStore.addShot(
            context = this,
            model = current.model,
            line = current.line,
            amount = amount,
            note = editNote.text.toString()
        )

        editNote.text?.clear()
        loadState()
    }

    private fun setDirectShot() {
        val value =
            editDirectShot.text.toString()
                .trim()
                .toIntOrNull()

        if (value == null || value < 0) {
            Toast.makeText(
                this,
                "올바른 Shot 수를 입력해주세요.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val current = ProductionContextStore.getCurrent(this)

        MoldMaintenanceStore.setShot(
            context = this,
            model = current.model,
            line = current.line,
            totalShot = value,
            note = editNote.text.toString()
        )

        editDirectShot.text?.clear()
        editNote.text?.clear()
        loadState()
    }

    private fun markCleaned() {
        val current = ProductionContextStore.getCurrent(this)

        MoldMaintenanceStore.markCleaned(
            this,
            current.model,
            current.line,
            editNote.text.toString()
        )

        editNote.text?.clear()

        Toast.makeText(
            this,
            "청소 완료 이력을 저장했습니다.",
            Toast.LENGTH_SHORT
        ).show()

        loadState()
    }

    private fun markReplaced() {
        val current = ProductionContextStore.getCurrent(this)

        MoldMaintenanceStore.markReplaced(
            this,
            current.model,
            current.line,
            editNote.text.toString()
        )

        editNote.text?.clear()

        Toast.makeText(
            this,
            "교체 완료 이력을 저장하고 Shot을 초기화했습니다.",
            Toast.LENGTH_LONG
        ).show()

        loadState()
    }

    private fun maybeSendPreventiveAlerts(
        state: MoldMaintenanceStore.MoldState
    ) {
        val cleanLevel =
            MoldMaintenanceStore.alertLevel(
                state.shotSinceClean,
                state.cleaningLimit
            )

        val replaceLevel =
            MoldMaintenanceStore.alertLevel(
                state.totalShot,
                state.replacementLimit
            )

        var newCleanStored =
            state.cleanAlertLevel

        var newReplaceStored =
            state.replaceAlertLevel

        if (cleanLevel > state.cleanAlertLevel && cleanLevel > 0) {
            sendMaintenanceAlert(
                type = "CLEANING",
                state = state,
                level = cleanLevel
            )
            newCleanStored = cleanLevel
        }

        if (replaceLevel > state.replaceAlertLevel && replaceLevel > 0) {
            sendMaintenanceAlert(
                type = "REPLACEMENT",
                state = state,
                level = replaceLevel
            )
            newReplaceStored = replaceLevel
        }

        if (
            newCleanStored != state.cleanAlertLevel ||
            newReplaceStored != state.replaceAlertLevel
        ) {
            MoldMaintenanceStore.updateAlertLevels(
                this,
                state,
                newCleanStored,
                newReplaceStored
            )
        }
    }

    private fun sendMaintenanceAlert(
        type: String,
        state: MoldMaintenanceStore.MoldState,
        level: Int
    ) {
        if (!TelegramSettingsStore.isReady(this)) {
            return
        }

        val isClean =
            type == "CLEANING"

        val current =
            if (isClean) state.shotSinceClean
            else state.totalShot

        val limit =
            if (isClean) state.cleaningLimit
            else state.replacementLimit

        val title =
            if (isClean) "금형/Punch 청소"
            else "금형/Punch 교체"

        val message =
            buildString {
                append("⚠ Pouch 예방보전 알림")
                append("\nModel : ${state.model}")
                append("\nLine : ${state.line}")
                append("\nMold : ${state.moldId}")
                append("\nPunch : ${state.punchId}")
                append("\n항목 : $title")
                append("\n상태 : ${levelText(level)}")
                append("\n현재 Shot : $current")
                append("\n기준 Shot : $limit")
                append("\n진행률 : ${progressPercent(current, limit)}%")
            }

        TelegramSender.sendReportWithImages(
            context = this,
            message = message,
            images = emptyList(),
            callback = null
        )
    }

    private fun buildPreventiveQualityText(
        model: String,
        line: String
    ): String {
        val records =
            InspectionHistoryStore.load(this)
                .filter {
                    !it.inspectionType.equals(
                        "TOTAL SESSION",
                        ignoreCase = true
                    ) &&
                    it.model == model &&
                    it.line == line
                }
                .sortedBy { it.id }
                .takeLast(10)

        if (records.isEmpty()) {
            return "품질 추세 : 검사 데이터 없음"
        }

        val lastScores =
            records.takeLast(minOf(3, records.size))
                .map { it.score }

        val issueRate =
            records.count {
                val j = it.judgment
                j.contains("주의") ||
                    j.contains("한계") ||
                    j.contains("불량")
            }.toDouble() /
                records.size.toDouble() *
                100.0

        val decline =
            lastScores.size >= 3 &&
                lastScores[0] > lastScores[1] &&
                lastScores[1] > lastScores[2]

        val text =
            if (decline) {
                "⚠ 품질 추세 : 최근 3회 Score 연속 하락"
            } else {
                "품질 추세 : 최근 Score 연속 하락 없음"
            }

        return "$text\n최근 ${records.size}건 이상률 : ${
            String.format(
                Locale.getDefault(),
                "%.0f",
                issueRate
            )
        }%"
    }

    private fun exportCsv() {
        val current = ProductionContextStore.getCurrent(this)

        val safeModel =
            current.model
                .replace(" ", "_")
                .replace("/", "_")

        val safeLine =
            current.line
                .replace(" ", "_")
                .replace("/", "_")

        exportLauncher.launch(
            "mold_maintenance_${safeModel}_${safeLine}.csv"
        )
    }

    private fun levelText(level: Int): String {
        return when (level) {
            3 -> "🔴 기준 초과 / 즉시 조치"
            2 -> "🟠 임박 (95% 이상)"
            1 -> "🟡 사전주의 (80% 이상)"
            else -> "🟢 정상"
        }
    }

    private fun progressPercent(
        value: Int,
        limit: Int
    ): Int {
        if (limit <= 0) return 0

        return (
            value.toDouble() /
                limit.toDouble() *
                100.0
            )
            .roundToInt()
            .coerceAtLeast(0)
    }

    private fun timeText(value: Long): String {
        if (value <= 0L) return "-"

        return SimpleDateFormat(
            "yyyy-MM-dd HH:mm",
            Locale.getDefault()
        ).format(Date(value))
    }

    private fun card(
        title: String,
        child: View
    ): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = rounded("#FFFFFF", 14f)

            addView(
                TextView(this@MoldManagementActivity).apply {
                    text = title
                    textSize = 18f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.parseColor("#102A43"))
                    setPadding(0, 0, 0, dp(10))
                }
            )

            addView(child)
        }.also {
            it.layoutParams =
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dp(12)
                }
        }
    }

    private fun label(textValue: String): TextView {
        return TextView(this).apply {
            text = textValue
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#334E68"))
            setPadding(0, dp(8), 0, dp(4))
        }
    }

    private fun input(hintValue: String): EditText {
        return EditText(this).apply {
            hint = hintValue
            textSize = 15f
            setTextColor(Color.parseColor("#102A43"))
            setHintTextColor(Color.parseColor("#829AB1"))
            setPadding(dp(12), 0, dp(12), 0)
            background = rounded("#F4F6F8", 8f)
            minHeight = dp(50)
        }
    }

    private fun numberInput(hintValue: String): EditText {
        return input(hintValue).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
    }

    private fun infoText(): TextView {
        return TextView(this).apply {
            textSize = 14f
            setTextColor(Color.parseColor("#334E68"))
            setLineSpacing(0f, 1.18f)
        }
    }

    private fun bigStatusText(): TextView {
        return TextView(this).apply {
            textSize = 19f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#102A43"))
            setPadding(0, 0, 0, dp(10))
        }
    }

    private fun actionButton(
        textValue: String,
        color: String,
        onClick: () -> Unit
    ): Button {
        return Button(this).apply {
            text = textValue
            textSize = 15f
            setTextColor(Color.WHITE)
            backgroundTintList =
                ColorStateList.valueOf(
                    Color.parseColor(color)
                )
            minHeight = dp(52)
            setOnClickListener {
                onClick()
            }
        }.also {
            it.layoutParams =
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(8)
                }
        }
    }

    private fun weightParams(
        left: Int = 0
    ): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1f
        ).apply {
            leftMargin = dp(left)
        }
    }

    private fun rounded(
        color: String,
        radius: Float
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(Color.parseColor(color))
            cornerRadius = dp(radius.toInt()).toFloat()
        }
    }

    private fun dp(value: Int): Int {
        return (
            value *
                resources.displayMetrics.density
            )
            .roundToInt()
    }
}
