package com.pouchvision.inspector

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

class InspectionSpecSummaryActivity : AppCompatActivity() {

    private lateinit var spinnerModel: Spinner
    private lateinit var spinnerLine: Spinner
    private lateinit var tvCurrentProduction: TextView
    private lateinit var summaryContainer: LinearLayout
    private var suppressSelectionEvent = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(createScreen())
        setupModelSpinner()
        restoreCurrentProductionSelection()
        refreshSummary()
    }

    override fun onResume() {
        super.onResume()
        if (::summaryContainer.isInitialized) {
            refreshSummary()
        }
    }

    private fun createScreen(): View {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#F4F7FA"))
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(22), dp(20), dp(30))
        }

        root.addView(TextView(this).apply {
            text = "Spec Summary"
            textSize = 30f
            setTextColor(Color.parseColor("#102A43"))
            setTypeface(null, android.graphics.Typeface.BOLD)
        })

        root.addView(TextView(this).apply {
            text = "Model / Line별 5개 검사 기준 요약"
            textSize = 17f
            setTextColor(Color.parseColor("#486581"))
            setPadding(0, dp(4), 0, dp(16))
        })

        tvCurrentProduction = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.parseColor("#0B7285"))
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setBackgroundColor(Color.parseColor("#E3F8FF"))
        }
        root.addView(tvCurrentProduction, matchWrap())

        addGap(root, 14)

        val selectionCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setBackgroundColor(Color.WHITE)
        }

        selectionCard.addView(TextView(this).apply {
            text = "조회 대상 선택"
            textSize = 20f
            setTextColor(Color.parseColor("#102A43"))
            setTypeface(null, android.graphics.Typeface.BOLD)
        })

        addGap(selectionCard, 14)
        addFieldLabel(selectionCard, "Model")
        spinnerModel = Spinner(this)
        selectionCard.addView(spinnerModel, matchHeight(56))

        addGap(selectionCard, 12)
        addFieldLabel(selectionCard, "Line")
        spinnerLine = Spinner(this)
        selectionCard.addView(spinnerLine, matchHeight(56))

        root.addView(selectionCard, matchWrap())
        addGap(root, 14)

        summaryContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(summaryContainer, matchWrap())

        addGap(root, 14)

        root.addView(Button(this).apply {
            text = "검사 기준 설정으로 이동"
            textSize = 16f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#12344D"))
            setOnClickListener {
                startActivity(
                    Intent(
                        this@InspectionSpecSummaryActivity,
                        InspectionSpecSettingsActivity::class.java
                    )
                )
            }
        }, matchHeight(58))

        addGap(root, 10)

        root.addView(Button(this).apply {
            text = "기준값 새로고침"
            textSize = 15f
            setOnClickListener { refreshSummary() }
        }, matchHeight(56))

        addGap(root, 10)

        root.addView(Button(this).apply {
            text = "메뉴로 돌아가기"
            textSize = 15f
            setOnClickListener { finish() }
        }, matchHeight(56))

        addGap(root, 18)

        root.addView(TextView(this).apply {
            text = "※ Summary는 현재 저장된 Model / Line별 판정 기준을 조회하는 화면입니다.\n" +
                "※ BOTTOM CORNER는 점수가 낮을수록 양호합니다.\n" +
                "※ SEAL / FORMING / TAB / DISASSEMBLY는 Quality Score가 높을수록 양호합니다.\n" +
                "※ 실제 양산 기준은 승인된 Master Sample / Spec 검증 후 확정해야 합니다."
            textSize = 13f
            setTextColor(Color.parseColor("#627D98"))
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundColor(Color.parseColor("#FFF8E1"))
        }, matchWrap())

        scroll.addView(root)
        return scroll
    }

    private fun setupModelSpinner() {
        val models = ProductionContextStore.getModels(this)

        spinnerModel.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            models
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        spinnerModel.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    if (suppressSelectionEvent) return
                    updateLineSpinner(null)
                    refreshSummary()
                }

                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }

        spinnerLine.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    if (suppressSelectionEvent) return
                    refreshSummary()
                }

                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
    }

    private fun restoreCurrentProductionSelection() {
        val current = ProductionContextStore.getCurrent(this)
        tvCurrentProduction.text =
            "현재 검사 생산 조건 : ${current.model} / ${current.line}"

        val models = ProductionContextStore.getModels(this)
        val modelIndex = models.indexOf(current.model).coerceAtLeast(0)

        suppressSelectionEvent = true
        spinnerModel.setSelection(modelIndex)
        updateLineSpinner(current.line)
        suppressSelectionEvent = false
    }

    private fun updateLineSpinner(preferredLine: String?) {
        val model = selectedModel()
        val lines = ProductionContextStore.getLinesForModel(model)

        spinnerLine.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            lines
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        if (preferredLine != null) {
            val index = lines.indexOf(preferredLine)
            if (index >= 0) spinnerLine.setSelection(index)
        }
    }

    private fun refreshSummary() {
        if (!::summaryContainer.isInitialized) return

        val model = selectedModel()
        val line = selectedLine()
        if (model.isBlank() || line.isBlank()) return

        summaryContainer.removeAllViews()

        summaryContainer.addView(TextView(this).apply {
            text = "$model / $line"
            textSize = 22f
            setTextColor(Color.parseColor("#102A43"))
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(dp(2), 0, 0, dp(10))
        })

        InspectionSpecStore.InspectionType.values().forEach { type ->
            val spec = InspectionSpecStore.get(
                context = this,
                model = model,
                line = line,
                inspectionType = type
            )
            addSpecCard(type, spec)
        }
    }

    private fun addSpecCard(
        type: InspectionSpecStore.InspectionType,
        spec: InspectionSpecStore.InspectionSpec
    ) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setBackgroundColor(Color.WHITE)
        }

        card.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = dp(10)
        }

        card.addView(TextView(this).apply {
            text = type.displayName
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.parseColor("#102A43"))
        })

        card.addView(TextView(this).apply {
            text = when (spec.scoreDirection) {
                InspectionSpecStore.ScoreDirection.LOWER_IS_BETTER ->
                    "Score 방향 : 낮을수록 양호"
                InspectionSpecStore.ScoreDirection.HIGHER_IS_BETTER ->
                    "Score 방향 : 높을수록 양호"
            }
            textSize = 13f
            setTextColor(Color.parseColor("#627D98"))
            setPadding(0, dp(4), 0, dp(8))
        })

        card.addView(TextView(this).apply {
            text = spec.criteriaText()
            textSize = 15f
            setTextColor(Color.parseColor("#334E68"))
            setLineSpacing(0f, 1.15f)
        })

        card.addView(TextView(this).apply {
            text = "기준값 : ${format(spec.normalBoundary)} / " +
                "${format(spec.warningBoundary)} / ${format(spec.limitBoundary)}"
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.parseColor("#0B7285"))
            setPadding(0, dp(10), 0, 0)
        })

        summaryContainer.addView(card)
    }

    private fun selectedModel(): String =
        spinnerModel.selectedItem?.toString()?.trim().orEmpty()

    private fun selectedLine(): String =
        spinnerLine.selectedItem?.toString()?.trim().orEmpty()

    private fun format(value: Double): String =
        if (value % 1.0 == 0.0) {
            value.toInt().toString()
        } else {
            String.format(Locale.getDefault(), "%.1f", value)
        }

    private fun addFieldLabel(parent: LinearLayout, text: String) {
        parent.addView(TextView(this).apply {
            this.text = text
            textSize = 15f
            setTextColor(Color.parseColor("#334E68"))
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, dp(6))
        })
    }

    private fun addGap(parent: LinearLayout, heightDp: Int) {
        parent.addView(
            View(this),
            LinearLayout.LayoutParams(1, dp(heightDp))
        )
    }

    private fun matchWrap() =
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )

    private fun matchHeight(heightDp: Int) =
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(heightDp)
        )

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
