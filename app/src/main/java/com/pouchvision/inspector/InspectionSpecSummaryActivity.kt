package com.pouchvision.inspector

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
        restoreCurrentSelection()
        refreshSummary()
    }

    private fun createScreen(): View {
        val scroll = ScrollView(this)
        scroll.setBackgroundColor(Color.parseColor("#F4F7FA"))
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
            setPadding(0, dp(4), 0, dp(18))
        })

        tvCurrentProduction = TextView(this).apply {
            textSize = 15f
            setTextColor(Color.parseColor("#0B7285"))
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setBackgroundColor(Color.parseColor("#E3F8FF"))
        }
        root.addView(tvCurrentProduction, matchWrap())
        addGap(root, 14)

        val selectorCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setBackgroundColor(Color.WHITE)
        }
        addFieldLabel(selectorCard, "Model")
        spinnerModel = Spinner(this)
        selectorCard.addView(spinnerModel, matchHeight(56))
        addGap(selectorCard, 12)
        addFieldLabel(selectorCard, "Line")
        spinnerLine = Spinner(this)
        selectorCard.addView(spinnerLine, matchHeight(56))
        root.addView(selectorCard, matchWrap())

        addGap(root, 14)
        summaryContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(summaryContainer, matchWrap())
        addGap(root, 14)

        root.addView(Button(this).apply {
            text = "선택 Model / Line 기준 수정"
            textSize = 16f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#12344D"))
            setOnClickListener {
                startActivity(android.content.Intent(this@InspectionSpecSummaryActivity, InspectionSpecSettingsActivity::class.java))
            }
        }, matchHeight(58))

        addGap(root, 10)
        root.addView(Button(this).apply {
            text = "메뉴로 돌아가기"
            textSize = 15f
            setOnClickListener { finish() }
        }, matchHeight(56))

        addGap(root, 18)
        root.addView(TextView(this).apply {
            text = "※ 이 화면은 현재 저장된 기준값을 한눈에 확인하는 요약 화면입니다.\n※ 기준 변경은 [검사 기준 설정]에서 수행합니다.\n※ 실제 양산 적용 전 승인된 Master Sample / Spec과 비교 검증이 필요합니다."
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
        spinnerModel.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, models).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinnerModel.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (suppressSelectionEvent) return
                updateLineSpinner(null)
                refreshSummary()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        spinnerLine.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (suppressSelectionEvent) return
                refreshSummary()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun restoreCurrentSelection() {
        val current = ProductionContextStore.getCurrent(this)
        tvCurrentProduction.text = "현재 검사 생산 조건 : ${current.model} / ${current.line}"
        val models = ProductionContextStore.getModels(this)
        val modelIndex = models.indexOf(current.model).coerceAtLeast(0)
        suppressSelectionEvent = true
        spinnerModel.setSelection(modelIndex)
        updateLineSpinner(current.line)
        suppressSelectionEvent = false
    }

    private fun updateLineSpinner(preferredLine: String?) {
        val lines = ProductionContextStore.getLinesForModel(selectedModel())
        spinnerLine.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, lines).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        preferredLine?.let {
            val index = lines.indexOf(it)
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
            text = "$model / $line 검사 기준"
            textSize = 21f
            setTextColor(Color.parseColor("#102A43"))
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, dp(10))
        })

        InspectionSpecStore.InspectionType.values().forEach { type ->
            val spec = InspectionSpecStore.get(this, model, line, type)
            summaryContainer.addView(createSpecCard(type, spec))
            addGap(summaryContainer, 10)
        }
    }

    private fun createSpecCard(type: InspectionSpecStore.InspectionType, spec: InspectionSpecStore.InspectionSpec): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            setBackgroundColor(Color.WHITE)
            addView(TextView(this@InspectionSpecSummaryActivity).apply {
                text = type.displayName
                textSize = 18f
                setTextColor(Color.parseColor("#102A43"))
                setTypeface(null, android.graphics.Typeface.BOLD)
            })
            addView(TextView(this@InspectionSpecSummaryActivity).apply {
                val scoreName = if (type == InspectionSpecStore.InspectionType.BOTTOM_CORNER) {
                    "Wrinkle Score"
                } else {
                    "Quality Score"
                }

                val directionText = when (spec.scoreDirection) {
                    InspectionSpecStore.ScoreDirection.LOWER_IS_BETTER -> "낮을수록 양호"
                    InspectionSpecStore.ScoreDirection.HIGHER_IS_BETTER -> "높을수록 양호"
                }

                text = "판정 점수 : $scoreName  ·  $directionText"
                textSize = 13f
                setTextColor(Color.parseColor("#0B7285"))
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(0, dp(4), 0, dp(8))
            })
            addView(TextView(this@InspectionSpecSummaryActivity).apply {
                text = spec.criteriaText()
                textSize = 15f
                setTextColor(Color.parseColor("#334E68"))
                setLineSpacing(0f, 1.15f)
            })
        }
    }

    private fun selectedModel() = spinnerModel.selectedItem?.toString()?.trim().orEmpty()
    private fun selectedLine() = spinnerLine.selectedItem?.toString()?.trim().orEmpty()

    private fun addFieldLabel(parent: LinearLayout, textValue: String) {
        parent.addView(TextView(this).apply {
            text = textValue
            textSize = 15f
            setTextColor(Color.parseColor("#334E68"))
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, dp(6))
        })
    }

    private fun addGap(parent: LinearLayout, heightDp: Int) {
        parent.addView(View(this), LinearLayout.LayoutParams(1, dp(heightDp)))
    }

    private fun matchWrap() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )

    private fun matchHeight(heightDp: Int) = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        dp(heightDp)
    )

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
