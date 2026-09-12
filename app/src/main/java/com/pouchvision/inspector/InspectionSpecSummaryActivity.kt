package com.pouchvision.inspector

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
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
    private lateinit var tvCurrent: TextView
    private lateinit var tvHeader: TextView
    private lateinit var container: LinearLayout
    private var suppressSelection = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(buildScreen())
        setupSelectors()
        selectCurrentProduction()
        refreshSummary()
    }

    override fun onResume() {
        super.onResume()
        if (::container.isInitialized) refreshSummary()
    }

    private fun buildScreen(): View {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#F4F7FA"))
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(22), dp(20), dp(30))
        }

        root.addView(text("검사 기준 요약", 30f, true, "#102A43"))
        root.addView(text("Model / Line별 5개 Inspection Spec", 16f, false, "#486581").apply {
            setPadding(0, dp(4), 0, dp(16))
        })

        tvCurrent = text("", 14f, false, "#0B7285").apply {
            setBackgroundColor(Color.parseColor("#E3F8FF"))
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        root.addView(tvCurrent, matchWrap())
        gap(root, 14)

        val selector = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setBackgroundColor(Color.WHITE)
        }
        selector.addView(text("조회 조건", 20f, true, "#102A43"))
        gap(selector, 12)
        selector.addView(text("Model", 15f, true, "#334E68"))
        spinnerModel = Spinner(this)
        selector.addView(spinnerModel, matchHeight(56))
        gap(selector, 10)
        selector.addView(text("Line", 15f, true, "#334E68"))
        spinnerLine = Spinner(this)
        selector.addView(spinnerLine, matchHeight(56))
        gap(selector, 12)

        val currentButton = Button(this).apply {
            text = "현재 생산 Model / Line 보기"
            setOnClickListener {
                selectCurrentProduction()
                refreshSummary()
            }
        }
        selector.addView(currentButton, matchHeight(52))
        root.addView(selector, matchWrap())
        gap(root, 14)

        tvHeader = text("", 19f, true, "#102A43").apply {
            setPadding(0, 0, 0, dp(10))
        }
        root.addView(tvHeader)

        container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(container, matchWrap())

        val editButton = Button(this).apply {
            text = "검사 기준 수정"
            textSize = 16f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#12344D"))
            setOnClickListener {
                startActivity(Intent(this@InspectionSpecSummaryActivity, InspectionSpecSettingsActivity::class.java))
            }
        }
        root.addView(editButton, matchHeight(58))
        gap(root, 10)

        root.addView(Button(this).apply {
            text = "새로고침"
            setOnClickListener { refreshSummary() }
        }, matchHeight(54))
        gap(root, 10)

        root.addView(Button(this).apply {
            text = "메뉴로 돌아가기"
            setOnClickListener { finish() }
        }, matchHeight(54))
        gap(root, 16)

        root.addView(text(
            "※ 기준값은 Model / Line별로 독립 저장됩니다.\n" +
                "※ BOTTOM CORNER는 Wrinkle Score가 낮을수록 양호합니다.\n" +
                "※ 나머지 4개 검사는 Quality Score가 높을수록 양호합니다.\n" +
                "※ 실제 양산 적용 전 승인된 Master Sample / Spec과 비교 검증이 필요합니다.",
            13f, false, "#627D98"
        ).apply {
            setBackgroundColor(Color.parseColor("#FFF8E1"))
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }, matchWrap())

        scroll.addView(root)
        return scroll
    }

    private fun setupSelectors() {
        val models = ProductionContextStore.getModels(this)
        spinnerModel.adapter = adapter(models)

        spinnerModel.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (suppressSelection) return
                updateLines(null)
                refreshSummary()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        spinnerLine.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!suppressSelection) refreshSummary()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun selectCurrentProduction() {
        val current = ProductionContextStore.getCurrent(this)
        tvCurrent.text = "현재 검사 생산 조건 : ${current.model} / ${current.line}"

        val models = ProductionContextStore.getModels(this)
        suppressSelection = true
        spinnerModel.setSelection(models.indexOf(current.model).coerceAtLeast(0))
        updateLines(current.line)
        suppressSelection = false
    }

    private fun updateLines(preferredLine: String?) {
        val model = spinnerModel.selectedItem?.toString().orEmpty()
        val lines = ProductionContextStore.getLinesForModel(model)
        spinnerLine.adapter = adapter(lines)

        if (preferredLine != null) {
            val index = lines.indexOf(preferredLine)
            if (index >= 0) spinnerLine.setSelection(index)
        }
    }

    private fun refreshSummary() {
        if (!::container.isInitialized) return

        val model = spinnerModel.selectedItem?.toString()?.trim().orEmpty()
        val line = spinnerLine.selectedItem?.toString()?.trim().orEmpty()
        if (model.isBlank() || line.isBlank()) return

        tvHeader.text = "$model / $line 검사 기준"
        container.removeAllViews()

        InspectionSpecStore.InspectionType.values().forEach { type ->
            val spec = InspectionSpecStore.get(this, model, line, type)
            container.addView(specCard(spec), matchWrap().apply { bottomMargin = dp(10) })
        }
    }

    private fun specCard(spec: InspectionSpecStore.InspectionSpec): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            setBackgroundColor(Color.WHITE)
        }

        card.addView(text(spec.inspectionType.displayName, 18f, true, "#102A43"))

        val direction = when (spec.scoreDirection) {
            InspectionSpecStore.ScoreDirection.LOWER_IS_BETTER -> "Wrinkle Score · 낮을수록 양호"
            InspectionSpecStore.ScoreDirection.HIGHER_IS_BETTER -> "Quality Score · 높을수록 양호"
        }
        card.addView(text(direction, 13f, false, "#627D98").apply {
            setPadding(0, dp(4), 0, dp(8))
        })

        card.addView(text(
            "정상 기준 : ${format(spec.normalBoundary)}\n" +
                "주의 기준 : ${format(spec.warningBoundary)}\n" +
                "한계/불량 경계 : ${format(spec.limitBoundary)}",
            15f, false, "#334E68"
        ))

        card.addView(text("\n${spec.criteriaText()}", 13f, false, "#486581"))
        return card
    }

    private fun adapter(items: List<String>): ArrayAdapter<String> {
        return ArrayAdapter(this, android.R.layout.simple_spinner_item, items).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
    }

    private fun text(value: String, size: Float, bold: Boolean, color: String): TextView {
        return TextView(this).apply {
            text = value
            textSize = size
            setTextColor(Color.parseColor(color))
            if (bold) setTypeface(null, Typeface.BOLD)
        }
    }

    private fun format(value: Double): String {
        return if (value % 1.0 == 0.0) value.toInt().toString()
        else String.format(Locale.getDefault(), "%.1f", value)
    }

    private fun gap(parent: LinearLayout, heightDp: Int) {
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

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
