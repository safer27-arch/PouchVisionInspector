package com.pouchvision.inspector

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.pouchvision.inspector.databinding.ActivityHistoryBinding

class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding

    private val inspectionTypes = listOf(
        "전체 검사",
        "BOTTOM CORNER",
        "SEAL",
        "FORMING",
        "TAB"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding =
            ActivityHistoryBinding.inflate(layoutInflater)

        setContentView(binding.root)

        setupSpinner()

        loadHistory()

        binding.btnHistoryRefresh.setOnClickListener {
            loadHistory()
        }

        binding.btnHistoryBack.setOnClickListener {
            finish()
        }
    }


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

        binding.spinnerInspectionType.setSelection(0)

        binding.spinnerInspectionType.onItemSelectedListener =
            object :
                android.widget.AdapterView.OnItemSelectedListener {

                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {

                    loadHistory()
                }

                override fun onNothingSelected(
                    parent: android.widget.AdapterView<*>?
                ) {
                }
            }
    }


    private fun loadHistory() {

        val selectedType =
            binding.spinnerInspectionType
                .selectedItem
                ?.toString()
                ?: "전체 검사"

        /*
         * 현재 단계에서는 저장 기능 연결 전입니다.
         *
         * 다음 단계에서
         * Bottom Corner / Seal / Forming / Tab 검사 결과를
         * SharedPreferences 또는 내부 파일로 저장하고
         * 이 화면에서 불러오도록 연결합니다.
         */

        binding.historyContainer.removeAllViews()

        binding.tvHistorySummary.text =
            """
현재 필터 : $selectedType

저장된 검사 결과 : 0건

정상 : 0건
주의 : 0건
한계정상 : 0건
불량 : 0건
            """.trimIndent()


        val emptyText =
            TextView(this)

        emptyText.text =
            """
아직 저장된 검사 결과가 없습니다.

다음 단계에서 각 검사 결과 저장 기능을 연결합니다.
            """.trimIndent()

        emptyText.textSize = 14f

        emptyText.setTextColor(
            android.graphics.Color.parseColor(
                "#829AB1"
            )
        )

        emptyText.gravity =
            android.view.Gravity.CENTER

        emptyText.setPadding(
            20,
            40,
            20,
            40
        )

        binding.historyContainer.addView(
            emptyText
        )
    }
}
