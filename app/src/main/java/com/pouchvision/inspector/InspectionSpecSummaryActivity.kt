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
    private lateinit var tvCurrent: TextView
    private lateinit var tvHeader: TextView
    private lateinit var container: LinearLayout
    private var suppress = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(createScreen())
        setupModelSpinner()
        restoreCurrentSelection()
        refreshSummary()
    }

    override fun onResume() {
        super.onResume()
        if (::container.isInitialized) refreshSummary()
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
            text = "검사 기준 요약"
            textSize = 30f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.parseColor("#102A43"))
        })

        root.addView(TextView(this).apply {
            text = "Model / Line별 5개 검사 Spec Summary"
            textSize = 17f
            setTextColor(Color.parseColor("#486581"))
            setPadding(0, dp(4), 0, dp(16))
        })

        tvCurrent = TextView(this).apply {
            textSize = 15f
            setTextColor(Color.parseColor("#0B7285"))
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setBackgroundColor(Color.parseColor("#E3F8FF"))
        }
        root.addView(tvCurrent, matchWrap())
        gap(root, 14)

        val selector = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setBackgroundColor(Color.WHITE)
        }

        selector.addView(TextView(this).apply {
            text = "조회 조건"
            textSize = 20f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.parseColor("#102A43"))
        })

        gap(selector, 12)
        label(selector, "Model")
        spinnerModel = Spinner(this)
        selector.addView(spinnerModel, matchHeight(56))
        gap(selector, 10)
        label(selector, "Line")
        spinnerLine = Spinner(this)
        selector.addView(spinnerLine, matchHeight(56))
        root.addView(selector, matchWrap())

        gap(root, 14)

        tvHeader = TextView(this).apply {
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.parseColor("#102A43"))
        }
        root.addView(tvHeader)
        gap(root, 10)

        container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(container, matchWrap())

        gap(root, 14)

        root.addView(Button(this).apply {
            text = "기준 다시 읽기"
            textSize = 15f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#12344D"))
            setOnClickListener { refreshSummary() }
        }, matchHeight(56))

        gap(root, 10)

        root.addView(Button(this).apply {
            text = "메뉴로 돌아가기"
            textSize = 15f
            setOnClickListener { finish() }
        }, matchHeight(56))

        gap(root, 16)

        root.addView(TextView(this).apply {
            text = "※ 기준값 요약 화면입니다.\n• 각 Model / Line별 저장된 판정 기준을 한 번에 확인할 수 있습니다.\n• 기준 변경은 [검사 기준 설정] 화면에서 수행합니다.\n• 실제 양산 적용 전 승인된 Master Sample / Spec과 비교 검증이 필요합니다."
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

        spinnerModel.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (suppress) return
                updateLineSpinner(null)
                refreshSummary()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        spinnerLine.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!suppress) refreshSummary()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun restoreCurrentSelection() {
        val current = ProductionContextStore.getCurrent(this)
        tvCurrent.text = "현재 검사 생산 조건 : ${current.model} / ${current.line}"

        val models = ProductionContextStore.getModels(this)
        val modelIndex = models.indexOf(current.model).coerceAtLeast(0)

        suppress = true
        spinnerModel.setSelection(modelIndex)
        updateLineSpinner(current.line)
        suppress = false
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
        if (!::container.isInitialized || !::spinnerModel.isInitialized || !::spinnerLine.isInitialized) return

        val model = selectedModel()
        val line = selectedLine()
        if (model.isBlank() || line.isBlank()) return

        tvHeader.text = "$model / $line 검사 기준"
        container.removeAllViews()

        InspectionSpecStore.InspectionType.values().forEach { type ->
            val spec = InspectionSpecStore.get(
                context = this,
                model = model,
                line = line,
                inspectionType = type
            )
            val defaultSpec = InspectionSpecStore.defaultSpec(type)
            container.addView(
                createSpecCard(spec, sameSpec(spec, defaultSpec)),
                matchWrapBottom(10)
            )
        }
    }

    private fun createSpecCard(
        spec: InspectionSpecStore.InspectionSpec,
        isDefault: Boolean
    ): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            setBackgroundColor(
                Color.parseColor(if (isDefault) "#FFFFFF" else "#E8F5E9")
            )
        }

        card.addView(TextView(this).apply {
            text = spec.inspectionType.displayName
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.parseColor("#102A43"))
        })

        card.addView(TextView(this).apply {
            text = if (isDefault) {
                "기준 상태 : 기본값"
            } else {
                "기준 상태 : Model / Line 사용자 기준"
            }
            textSize = 13f
            setTextColor(Color.parseColor(if (isDefault) "#627D98" else "#2E7D32"))
            setPadding(0, dp(4), 0, dp(8))
        })

        card.addView(TextView(this).apply {
            text = when (spec.scoreDirection) {
                InspectionSpecStore.ScoreDirection.LOWER_IS_BETTER -> "점수 방향 : 낮을수록 양호"
                InspectionSpecStore.ScoreDirection.HIGHER_IS_BETTER -> "점수 방향 : 높을수록 양호"
            }
            textSize = 13f
            setTextColor(Color.parseColor("#486581"))
        })

        card.addView(TextView(this).apply {
            text = spec.criteriaText()
            textSize = 14f
            setTextColor(Color.parseColor("#243B53"))
            setPadding(0, dp(8), 0, 0)
        })

        return card
    }

    private fun sameSpec(
        a: InspectionSpecStore.InspectionSpec,
        b: InspectionSpecStore.InspectionSpec
    ): Boolean {
        return a.inspectionType == b.inspectionType &&
            a.scoreDirection == b.scoreDirection &&
            kotlin.math.abs(a.normalBoundary - b.normalBoundary) < 0.0001 &&
            kotlin.math.abs(a.warningBoundary - b.warningBoundary) < 0.0001 &&
            kotlin.math.abs(a.limitBoundary - b.limitBoundary) < 0.0001
    }

    private fun selectedModel(): String =
        spinnerModel.selectedItem?.toString()?.trim().orEmpty()

    private fun selectedLine(): String =
        spinnerLine.selectedItem?.toString()?.trim().orEmpty()

    private fun label(parent: LinearLayout, textValue: String) {
        parent.addView(TextView(this).apply {
            text = textValue
            textSize = 15f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.parseColor("#334E68"))
            setPadding(0, 0, 0, dp(6))
        })
    }

    private fun gap(parent: LinearLayout, heightDp: Int) {
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

    private fun matchWrapBottom(bottomDp: Int) =
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = dp(bottomDp)
        }

    private fun matchHeight(heightDp: Int) =
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(heightDp)
        )

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
