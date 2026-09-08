package com.pouchvision.inspector

import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.pouchvision.inspector.databinding.ActivityHistoryBinding
import java.io.File
import java.util.Locale

class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding

    private val inspectionTypes =
        listOf(
            "전체 검사",
            "BOTTOM CORNER",
            "SEAL",
            "FORMING",
            "TAB",
            "DISASSEMBLY"
        )

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(savedInstanceState)

        binding =
            ActivityHistoryBinding.inflate(
                layoutInflater
            )

        setContentView(
            binding.root
        )

        setupSpinner()

        binding.btnHistoryRefresh
            .setOnClickListener {

                loadHistory()

                Toast.makeText(
                    this,
                    "검사 이력을 새로고침했습니다.",
                    Toast.LENGTH_SHORT
                ).show()
            }

        binding.btnHistoryBack
            .setOnClickListener {

                finish()
            }

        loadHistory()
    }

    override fun onResume() {

        super.onResume()

        if (
            ::binding.isInitialized
        ) {

            loadHistory()
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

        binding.spinnerInspectionType
            .setSelection(
                0
            )

        binding.spinnerInspectionType
            .onItemSelectedListener =

            object :
                AdapterView.OnItemSelectedListener {

                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {

                    loadHistory()
                }

                override fun onNothingSelected(
                    parent: AdapterView<*>?
                ) {
                }
            }
    }

    private fun loadHistory() {

        val allRecords =
            InspectionHistoryStore.load(
                this
            )

        val selectedType =
            binding.spinnerInspectionType
                .selectedItem
                ?.toString()
                ?: "전체 검사"

        val filteredRecords =
            if (
                selectedType ==
                "전체 검사"
            ) {

                allRecords

            } else {

                allRecords.filter {

                    it.inspectionType ==
                        selectedType
                }
            }

        updateSummary(
            selectedType,
            filteredRecords
        )

        showHistoryItems(
            filteredRecords
        )
    }

    private fun updateSummary(
        selectedType: String,
        records:
        List<
            InspectionHistoryStore
                .InspectionRecord
            >
    ) {

        var normalCount =
            0

        var warningCount =
            0

        var limitCount =
            0

        var ngCount =
            0

        var imageCount =
            0

        for (
            record in records
        ) {

            when {

                record.judgment
                    .contains(
                        "불량"
                    ) -> {

                    ngCount++
                }

                record.judgment
                    .contains(
                        "한계"
                    ) -> {

                    limitCount++
                }

                record.judgment
                    .contains(
                        "주의"
                    ) -> {

                    warningCount++
                }

                record.judgment
                    .contains(
                        "정상"
                    ) -> {

                    normalCount++
                }
            }

            if (
                InspectionHistoryStore
                    .getImageFile(
                        record
                    ) != null
            ) {

                imageCount++
            }
        }

        val averageScore =
            if (
                records.isNotEmpty()
            ) {

                records
                    .map {
                        it.score
                    }
                    .average()

            } else {

                0.0
            }

        binding.tvHistorySummary.text =
            String.format(
                Locale.getDefault(),

                """
현재 필터 : %s
저장된 검사 결과 : %d건
결과 사진 있음 : %d건

정상 : %d건
주의 : %d건
한계정상 : %d건
불량 : %d건

평균 Quality Score : %.1f / 100
                """.trimIndent(),

                selectedType,
                records.size,
                imageCount,
                normalCount,
                warningCount,
                limitCount,
                ngCount,
                averageScore
            )
    }

    private fun showHistoryItems(
        records:
        List<
            InspectionHistoryStore
                .InspectionRecord
            >
    ) {

        binding.historyContainer
            .removeAllViews()

        if (
            records.isEmpty()
        ) {

            val emptyText =
                TextView(
                    this
                )

            emptyText.text =
                """
아직 저장된 검사 결과가 없습니다.

검사 화면에서 ROI 검사를 실행한 뒤
'검사 결과 저장' 버튼을 눌러주세요.
                """.trimIndent()

            emptyText.textSize =
                14f

            emptyText.setTextColor(
                Color.parseColor(
                    "#829AB1"
                )
            )

            emptyText.gravity =
                Gravity.CENTER

            emptyText.setPadding(
                dp(16),
                dp(28),
                dp(16),
                dp(28)
            )

            binding.historyContainer
                .addView(
                    emptyText
                )

            return
        }

        for (
            record in records
        ) {

            addHistoryCard(
                record
            )
        }
    }

    private fun addHistoryCard(
        record:
        InspectionHistoryStore
            .InspectionRecord
    ) {

        val card =
            LinearLayout(
                this
            )

        card.orientation =
            LinearLayout.VERTICAL

        card.setPadding(
            dp(14),
            dp(14),
            dp(14),
            dp(14)
        )

        val cardParams =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )

        cardParams.bottomMargin =
            dp(10)

        card.layoutParams =
            cardParams

        card.setBackgroundColor(
            Color.parseColor(
                "#F8FAFC"
            )
        )

        val typeText =
            TextView(
                this
            )

        typeText.text =
            record.inspectionType

        typeText.textSize =
            17f

        typeText.setTextColor(
            Color.parseColor(
                "#102A43"
            )
        )

        typeText.setTypeface(
            null,
            Typeface.BOLD
        )

        val judgmentText =
            TextView(
                this
            )

        judgmentText.text =
            "판정 : ${record.judgment}"

        judgmentText.textSize =
            16f

        judgmentText.setTypeface(
            null,
            Typeface.BOLD
        )

        judgmentText.setTextColor(
            judgmentColor(
                record.judgment
            )
        )

        val infoText =
            TextView(
                this
            )

        infoText.text =
            String.format(
                Locale.getDefault(),

                """
검사 일시 : %s
Quality Score : %.1f / 100
민감도 : %d%%
                """.trimIndent(),

                record.dateTime,
                record.score,
                record.sensitivity
            )

        infoText.textSize =
            14f

        infoText.setTextColor(
            Color.parseColor(
                "#486581"
            )
        )

        infoText.setLineSpacing(
            0f,
            1.15f
        )

        val imageFile =
            InspectionHistoryStore
                .getImageFile(
                    record
                )

        val imageViewControl:
            View =
            if (
                imageFile != null
            ) {

                createImageButton(
                    record
                )

            } else {

                createNoImageText()
            }

        val detailText =
            TextView(
                this
            )

        detailText.text =
            record.details

        detailText.textSize =
            13f

        detailText.setTextColor(
            Color.parseColor(
                "#627D98"
            )
        )

        detailText.setPadding(
            0,
            dp(10),
            0,
            0
        )

        card.addView(
            typeText
        )

        card.addView(
            judgmentText
        )

        card.addView(
            infoText
        )

        card.addView(
            imageViewControl
        )

        card.addView(
            detailText
        )

        binding.historyContainer
            .addView(
                card
            )
    }

    private fun createImageButton(
        record:
        InspectionHistoryStore
            .InspectionRecord
    ): Button {

        val button =
            Button(
                this
            )

        button.text =
            "결과 사진 보기"

        button.textSize =
            14f

        button.isAllCaps =
            false

        button.setTextColor(
            Color.WHITE
        )

        button.backgroundTintList =
            ColorStateList.valueOf(
                Color.parseColor(
                    "#102A43"
                )
            )

        val params =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48)
            )

        params.topMargin =
            dp(10)

        button.layoutParams =
            params

        button.setOnClickListener {

            showResultImage(
                record
            )
        }

        return button
    }

    private fun createNoImageText():
        TextView {

        val textView =
            TextView(
                this
            )

        textView.text =
            "결과 사진 : 없음"

        textView.textSize =
            13f

        textView.setTextColor(
            Color.parseColor(
                "#829AB1"
            )
        )

        textView.setPadding(
            0,
            dp(10),
            0,
            0
        )

        return textView
    }

    private fun showResultImage(
        record:
        InspectionHistoryStore
            .InspectionRecord
    ) {

        val imageFile =
            InspectionHistoryStore
                .getImageFile(
                    record
                )

        if (
            imageFile == null
        ) {

            Toast.makeText(
                this,
                "저장된 결과 사진을 찾을 수 없습니다.",
                Toast.LENGTH_LONG
            ).show()

            loadHistory()

            return
        }

        val bitmap =
            decodeBitmapForDisplay(
                imageFile
            )

        if (
            bitmap == null
        ) {

            Toast.makeText(
                this,
                "결과 사진을 불러올 수 없습니다.",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        val imageView =
            ImageView(
                this
            )

        imageView.adjustViewBounds =
            true

        imageView.scaleType =
            ImageView.ScaleType.FIT_CENTER

        imageView.setBackgroundColor(
            Color.BLACK
        )

        imageView.setPadding(
            dp(4),
            dp(4),
            dp(4),
            dp(4)
        )

        imageView.setImageBitmap(
            bitmap
        )

        val dialog =
            AlertDialog.Builder(
                this
            )
                .setTitle(
                    "${record.inspectionType} · ${record.judgment}"
                )
                .setMessage(
                    """
${record.dateTime}
Quality Score : ${String.format(Locale.getDefault(), "%.1f", record.score)} / 100
                    """.trimIndent()
                )
                .setView(
                    imageView
                )
                .setPositiveButton(
                    "닫기",
                    null
                )
                .create()

        dialog.setOnDismissListener {

            imageView.setImageDrawable(
                null
            )

            if (
                !bitmap.isRecycled
            ) {

                bitmap.recycle()
            }
        }

        dialog.show()
    }

    private fun decodeBitmapForDisplay(
        file: File
    ): Bitmap? {

        return try {

            val bounds =
                BitmapFactory.Options().apply {

                    inJustDecodeBounds =
                        true
                }

            BitmapFactory.decodeFile(
                file.absolutePath,
                bounds
            )

            if (
                bounds.outWidth <= 0 ||
                bounds.outHeight <= 0
            ) {

                return null
            }

            val longestSide =
                maxOf(
                    bounds.outWidth,
                    bounds.outHeight
                )

            var sampleSize =
                1

            while (
                longestSide /
                    sampleSize >
                1600
            ) {

                sampleSize *=
                    2
            }

            val options =
                BitmapFactory.Options().apply {

                    inSampleSize =
                        sampleSize
                }

            BitmapFactory.decodeFile(
                file.absolutePath,
                options
            )

        } catch (
            e: Exception
        ) {

            null
        }
    }

    private fun judgmentColor(
        judgment: String
    ): Int {

        return when {

            judgment.contains(
                "불량"
            ) -> {

                Color.parseColor(
                    "#C62828"
                )
            }

            judgment.contains(
                "한계"
            ) -> {

                Color.parseColor(
                    "#EF6C00"
                )
            }

            judgment.contains(
                "주의"
            ) -> {

                Color.parseColor(
                    "#F9A825"
                )
            }

            else -> {

                Color.parseColor(
                    "#2E7D32"
                )
            }
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
            .toInt()
    }
}
