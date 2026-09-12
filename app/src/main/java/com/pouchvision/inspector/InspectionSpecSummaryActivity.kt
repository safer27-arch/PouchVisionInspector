package com.pouchvision.inspector

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/*
 * =============================================================
 * Model / Line별 검사 기준 Summary 화면 - 안정화 버전
 * =============================================================
 *
 * 수정 핵심
 * - Spinner 초기화 중 onItemSelected가 먼저 실행되어도 Crash가 나지 않도록
 *   초기 선택 완료 후 Listener를 연결합니다.
 * - Model / Line 목록이 비어 있거나 예외가 발생해도 앱을 종료하지 않고
 *   화면에 오류를 표시합니다.
 * - Spec 조회도 항목별로 안전하게 처리합니다.
 * =============================================================
 */
class InspectionSpecSummaryActivity : AppCompatActivity() {

    private lateinit var spinnerModel: Spinner
    private lateinit var spinnerLine: Spinner
    private lateinit var tvSelectedContext: TextView
    private lateinit var summaryContainer: LinearLayout

    private var screenReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supportActionBar?.hide()

        try {
            setContentView(createScreen())
            initializeSelectionSafely()
            installSelectionListeners()
            screenReady = true
            refreshSummarySafely()
        } catch (e: Exception) {
            showFatalScreenError(e)
        }
    }

    /* =========================================================
     * 화면 구성
     * ========================================================= */
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
            setPadding(0, dp(4), 0, dp(18))
        })

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

        addGap(selectorCard, 14)
        tvSelectedContext = TextView(this).apply {
            text = "조회 기준을 불러오는 중입니다."
            textSize = 15f
            setTextColor(Color.parseColor("#0B7285"))
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackgroundColor(Color.parseColor("#E3F8FF"))
        }
        selectorCard.addView(tvSelectedContext)

        root.addView(selectorCard, matchWrap())
        addGap(root, 14)

        summaryContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(summaryContainer, matchWrap())

        addGap(root, 12)

        root.addView(Button(this).apply {
            text = "기준값 새로고침"
            textSize = 15f
            setOnClickListener { refreshSummarySafely() }
        }, matchHeight(56))

        addGap(root, 10)

        root.addView(Button(this).apply {
            text = "메뉴로 돌아가기"
            textSize = 15f
            setOnClickListener { finish() }
        }, matchHeight(56))

        addGap(root, 16)

        root.addView(TextView(this).apply {
            text = """
※ 이 화면은 현재 저장된 Model / Line별 검사 기준을 한눈에 확인하는 Summary 화면입니다.

※ 실제 검사 판정은 각 검사 화면에서 현재 선택된 Model / Line 기준값을 사용합니다.

※ 기준 변경은 [검사 기준 설정] 화면에서 진행합니다.
            """.trimIndent()
            textSize = 13f
            setTextColor(Color.parseColor("#627D98"))
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundColor(Color.parseColor("#FFF8E1"))
        }, matchWrap())

        scroll.addView(root)
        return scroll
    }

    /* =========================================================
     * 초기 선택 - Listener 연결 전에 완료
     * ========================================================= */
    private fun initializeSelectionSafely() {
        val models = ProductionContextStore.getModels(this)

        if (models.isEmpty()) {
            throw IllegalStateException("등록된 Model이 없습니다.")
        }

        spinnerModel.adapter =
            createVisibleSpinnerAdapter(
                models
            )

        val current = ProductionContextStore.getCurrent(this)
        val modelIndex = models.indexOf(current.model).let { if (it >= 0) it else 0 }
        spinnerModel.setSelection(modelIndex, false)

        updateLineSpinnerSafely(current.line)
    }

    private fun updateLineSpinnerSafely(preferredLine: String?) {
        val model = selectedModel()
        val lines = ProductionContextStore.getLinesForModel(model)

        val safeLines = if (lines.isNotEmpty()) {
            lines
        } else {
            listOf("-")
        }

        spinnerLine.adapter =
            createVisibleSpinnerAdapter(
                safeLines
            )

        val preferredIndex = preferredLine
            ?.let { safeLines.indexOf(it) }
            ?.takeIf { it >= 0 }
            ?: 0

        spinnerLine.setSelection(preferredIndex, false)
    }

    /* =========================================================
     * Listener - 초기화 완료 후 연결
     * ========================================================= */
    private fun installSelectionListeners() {
        spinnerModel.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                if (!screenReady) return

                try {
                    updateLineSpinnerSafely(null)
                    refreshSummarySafely()
                } catch (e: Exception) {
                    showSummaryError(e)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        spinnerLine.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                if (!screenReady) return
                refreshSummarySafely()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    /* =========================================================
     * Summary
     * ========================================================= */
    private fun refreshSummarySafely() {
        try {
            val model = selectedModel()
            val line = selectedLine()

            if (model.isBlank() || line.isBlank() || line == "-") {
                tvSelectedContext.text = "조회 가능한 Model / Line 정보가 없습니다."
                summaryContainer.removeAllViews()
                return
            }

            tvSelectedContext.text = "조회 기준 : $model / $line"
            summaryContainer.removeAllViews()

            InspectionSpecStore.InspectionType.values().forEach { type ->
                try {
                    val spec = InspectionSpecStore.get(
                        context = this,
                        model = model,
                        line = line,
                        inspectionType = type
                    )

                    summaryContainer.addView(createSpecCard(spec), matchWrap())
                    addGap(summaryContainer, 10)
                } catch (e: Exception) {
                    summaryContainer.addView(createSpecErrorCard(type, e), matchWrap())
                    addGap(summaryContainer, 10)
                }
            }
        } catch (e: Exception) {
            showSummaryError(e)
        }
    }

    private fun createSpecCard(spec: InspectionSpecStore.InspectionSpec): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setBackgroundColor(Color.WHITE)
        }

        card.addView(TextView(this).apply {
            text = spec.inspectionType.displayName
            textSize = 18f
            setTextColor(Color.parseColor("#102A43"))
            setTypeface(null, android.graphics.Typeface.BOLD)
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
            setPadding(0, dp(4), 0, dp(10))
        })

        card.addView(TextView(this).apply {
            text = spec.criteriaText()
            textSize = 14f
            setTextColor(Color.parseColor("#334E68"))
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackgroundColor(
                if (spec.inspectionType == InspectionSpecStore.InspectionType.BOTTOM_CORNER) {
                    Color.parseColor("#FFF8E1")
                } else {
                    Color.parseColor("#F0F4F8")
                }
            )
        })

        return card
    }

    private fun createSpecErrorCard(
        type: InspectionSpecStore.InspectionType,
        error: Exception
    ): View {
        return TextView(this).apply {
            text = "${type.displayName}\n기준값을 불러오지 못했습니다.\n${error.message ?: "알 수 없는 오류"}"
            textSize = 14f
            setTextColor(Color.parseColor("#C62828"))
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setBackgroundColor(Color.parseColor("#FFEBEE"))
        }
    }

    private fun showSummaryError(error: Exception) {
        if (::tvSelectedContext.isInitialized) {
            tvSelectedContext.text = "Spec Summary 오류가 발생했습니다."
        }

        if (::summaryContainer.isInitialized) {
            summaryContainer.removeAllViews()
            summaryContainer.addView(TextView(this).apply {
                text = "화면을 종료하지 않고 오류를 표시합니다.\n${error.javaClass.simpleName}: ${error.message ?: "알 수 없는 오류"}"
                textSize = 14f
                setTextColor(Color.parseColor("#C62828"))
                setPadding(dp(16), dp(16), dp(16), dp(16))
                setBackgroundColor(Color.parseColor("#FFEBEE"))
            }, matchWrap())
        }
    }

    private fun showFatalScreenError(error: Exception) {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(30), dp(20), dp(30))
            setBackgroundColor(Color.parseColor("#F4F7FA"))
        }

        root.addView(TextView(this).apply {
            text = "Spec Summary 화면 오류"
            textSize = 24f
            setTextColor(Color.parseColor("#C62828"))
            setTypeface(null, android.graphics.Typeface.BOLD)
        })

        root.addView(TextView(this).apply {
            text = "\n앱이 종료되지 않도록 오류를 표시했습니다.\n\n${error.javaClass.simpleName}\n${error.message ?: "알 수 없는 오류"}"
            textSize = 15f
            setTextColor(Color.parseColor("#334E68"))
        })

        root.addView(Button(this).apply {
            text = "메뉴로 돌아가기"
            setOnClickListener { finish() }
        }, matchHeight(56))

        setContentView(root)
    }

    /* =========================================================
     * Helpers
     * ========================================================= */
    /* =========================================================
     * Spinner 글자 가시성
     * =========================================================
     *
     * 일부 Samsung / Dark Mode 조합에서는 Android 기본 Spinner가
     * 흰색 글자를 사용해 흰색 배경에서 Model / Line 값이 보이지
     * 않는 경우가 있습니다. 선택값과 드롭다운 글자색을 앱에서
     * 직접 지정해 항상 읽을 수 있도록 합니다.
     * ========================================================= */
    private fun createVisibleSpinnerAdapter(
        items: List<String>
    ): ArrayAdapter<String> {

        return object : ArrayAdapter<String>(
            this,
            android.R.layout.simple_spinner_item,
            items
        ) {

            override fun getView(
                position: Int,
                convertView: View?,
                parent: ViewGroup
            ): View {

                val view = super.getView(
                    position,
                    convertView,
                    parent
                )

                (view as? TextView)?.apply {
                    setTextColor(Color.parseColor("#102A43"))
                    setBackgroundColor(Color.parseColor("#F8FAFC"))
                    textSize = 17f
                    setPadding(dp(14), 0, dp(14), 0)
                    gravity = android.view.Gravity.CENTER_VERTICAL
                }

                return view
            }

            override fun getDropDownView(
                position: Int,
                convertView: View?,
                parent: ViewGroup
            ): View {

                val view = super.getDropDownView(
                    position,
                    convertView,
                    parent
                )

                (view as? TextView)?.apply {
                    setTextColor(Color.parseColor("#102A43"))
                    setBackgroundColor(Color.WHITE)
                    textSize = 17f
                    setPadding(dp(16), dp(14), dp(16), dp(14))
                }

                return view
            }
        }.apply {
            setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
            )
        }
    }

    private fun selectedModel(): String {
        return if (::spinnerModel.isInitialized) {
            spinnerModel.selectedItem?.toString()?.trim().orEmpty()
        } else {
            ""
        }
    }

    private fun selectedLine(): String {
        return if (::spinnerLine.isInitialized) {
            spinnerLine.selectedItem?.toString()?.trim().orEmpty()
        } else {
            ""
        }
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

    private fun matchWrap() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )

    private fun matchHeight(heightDp: Int) = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        dp(heightDp)
    )

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
