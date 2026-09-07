package com.pouchvision.inspector

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.pouchvision.inspector.databinding.ActivityTotalInspectionBinding
import java.util.Locale

class TotalInspectionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTotalInspectionBinding

    /*
     * 종합검사 화면을 연 시간을
     * 현재 검사 세션의 시작 시간으로 사용합니다.
     *
     * 따라서 이전에 저장된 과거 검사 결과는
     * 이번 종합검사 결과에 포함하지 않습니다.
     */
    private var sessionStartTime: Long = 0L

    companion object {

        private const val STATE_SESSION_START =
            "total_inspection_session_start"
    }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(savedInstanceState)

        binding =
            ActivityTotalInspectionBinding.inflate(
                layoutInflater
            )

        setContentView(binding.root)

        /*
         * 화면 회전/재생성 등이 발생해도
         * 현재 검사 세션 시작 시간을 유지합니다.
         */
        sessionStartTime =
            savedInstanceState?.getLong(
                STATE_SESSION_START
            )
                ?: System.currentTimeMillis()

        setupButtons()

        refreshInspectionResults()
    }

    override fun onResume() {

        super.onResume()

        /*
         * 개별 검사 화면에서 결과를 저장하고
         * 종합검사 화면으로 돌아오면 자동 갱신
         */
        refreshInspectionResults()
    }

    override fun onSaveInstanceState(
        outState: Bundle
    ) {

        outState.putLong(
            STATE_SESSION_START,
            sessionStartTime
        )

        super.onSaveInstanceState(
            outState
        )
    }

    /*
     * =========================================================
     * 버튼 연결
     * =========================================================
     */

    private fun setupButtons() {

        /*
         * 1. Bottom Corner
         */
        binding.btnTotalBottom
            .setOnClickListener {

                startActivity(
                    Intent(
                        this,
                        MainActivity::class.java
                    )
                )
            }

        /*
         * 2. Seal
         */
        binding.btnTotalSeal
            .setOnClickListener {

                startActivity(
                    Intent(
                        this,
                        SealActivity::class.java
                    )
                )
            }

        /*
         * 3. Forming
         */
        binding.btnTotalForming
            .setOnClickListener {

                startActivity(
                    Intent(
                        this,
                        FormingActivity::class.java
                    )
                )
            }

        /*
         * 4. Tab
         */
        binding.btnTotalTab
            .setOnClickListener {

                startActivity(
                    Intent(
                        this,
                        TabActivity::class.java
                    )
                )
            }

        /*
         * 5. Disassembly
         */
        binding.btnTotalDisassembly
            .setOnClickListener {

                startActivity(
                    Intent(
                        this,
                        DisassemblyActivity::class.java
                    )
                )
            }

        /*
         * 저장된 결과 새로고침
         */
        binding.btnTotalRefresh
            .setOnClickListener {

                refreshInspectionResults()
            }

        /*
         * 전체 검사 이력 보기
         */
        binding.btnTotalHistory
            .setOnClickListener {

                startActivity(
                    Intent(
                        this,
                        HistoryActivity::class.java
                    )
                )
            }

        /*
         * 메뉴로 돌아가기
         */
        binding.btnTotalBack
            .setOnClickListener {

                finish()
            }
    }

    /*
     * =========================================================
     * 종합검사 결과 갱신
     * =========================================================
     */

    private fun refreshInspectionResults() {

        val allHistory =
            InspectionHistoryStore.load(
                this
            )

        /*
         * 이번 종합검사를 시작한 이후의
         * 검사 결과만 사용합니다.
         */
        val sessionHistory =
            allHistory.filter {

                it.id >= sessionStartTime
            }

        /*
         * 각 검사 종류별 가장 최근 결과
         */
        val bottomRecord =
            findLatestRecord(
                sessionHistory,
                "BOTTOM CORNER"
            )

        val sealRecord =
            findLatestRecord(
                sessionHistory,
                "SEAL"
            )

        val formingRecord =
            findLatestRecord(
                sessionHistory,
                "FORMING"
            )

        val tabRecord =
            findLatestRecord(
                sessionHistory,
                "TAB"
            )

        val disassemblyRecord =
            findLatestRecord(
                sessionHistory,
                "DISASSEMBLY"
            )

        /*
         * 각 항목 상태 표시
         */
        updateStatusView(
            type = "BOTTOM CORNER",
            record = bottomRecord
        )

        updateStatusView(
            type = "SEAL",
            record = sealRecord
        )

        updateStatusView(
            type = "FORMING",
            record = formingRecord
        )

        updateStatusView(
            type = "TAB",
            record = tabRecord
        )

        updateStatusView(
            type = "DISASSEMBLY",
            record = disassemblyRecord
        )

        /*
         * 종합 결과 계산
         */
        val completedRecords =
            listOfNotNull(
                bottomRecord,
                sealRecord,
                formingRecord,
                tabRecord,
                disassemblyRecord
            )

        updateTotalSummary(
            completedRecords
        )
    }

    /*
     * =========================================================
     * 검사 종류별 최신 결과 찾기
     * =========================================================
     */

    private fun findLatestRecord(
        records:
        List<InspectionHistoryStore.InspectionRecord>,
        inspectionType: String
    ): InspectionHistoryStore.InspectionRecord? {

        return records
            .filter {

                it.inspectionType.equals(
                    inspectionType,
                    ignoreCase = true
                )
            }
            .maxByOrNull {

                it.id
            }
    }

    /*
     * =========================================================
     * 개별 검사 상태 표시
     * =========================================================
     */

    private fun updateStatusView(
        type: String,
        record:
        InspectionHistoryStore.InspectionRecord?
    ) {

        val targetView =
            when (type) {

                "BOTTOM CORNER" ->
                    binding.tvTotalBottomStatus

                "SEAL" ->
                    binding.tvTotalSealStatus

                "FORMING" ->
                    binding.tvTotalFormingStatus

                "TAB" ->
                    binding.tvTotalTabStatus

                "DISASSEMBLY" ->
                    binding.tvTotalDisassemblyStatus

                else ->
                    return
            }

        /*
         * 아직 이번 세션에서 검사하지 않은 경우
         */
        if (record == null) {

            targetView.text =
                "미검사"

            targetView.setTextColor(
                Color.parseColor(
                    "#829AB1"
                )
            )

            return
        }

        /*
         * 검사 완료
         */
        targetView.text =
            String.format(
                Locale.getDefault(),
                "완료  |  %.1f점  |  %s",
                record.score,
                record.judgment
            )

        targetView.setTextColor(
            judgmentColor(
                record.judgment
            )
        )
    }

    /*
     * =========================================================
     * 종합 결과
     * =========================================================
     */

    private fun updateTotalSummary(
        completedRecords:
        List<InspectionHistoryStore.InspectionRecord>
    ) {

        val completedCount =
            completedRecords.size

        /*
         * 아직 검사하지 않음
         */
        if (completedCount == 0) {

            binding.tvTotalSummary.text =
                """
현재 종합검사 진행 상황

완료 검사 : 0 / 5

아래 1번부터 순서대로 검사를 진행해주세요.

1. BOTTOM CORNER
2. SEAL
3. FORMING
4. TAB
5. 분해검사

각 검사 화면에서 반드시
'결과 저장' 버튼을 눌러주세요.
                """.trimIndent()

            binding.tvTotalSummary.setTextColor(
                Color.parseColor(
                    "#334E68"
                )
            )

            return
        }

        /*
         * 평균 점수
         */
        val averageScore =
            completedRecords
                .map {
                    it.score
                }
                .average()

        /*
         * 가장 낮은 점수
         */
        val lowestRecord =
            completedRecords
                .minByOrNull {
                    it.score
                }

        /*
         * 현재까지 가장 좋지 않은 판정
         */
        val worstRecord =
            completedRecords
                .maxByOrNull {

                    judgmentSeverity(
                        it.judgment
                    )
                }

        val finalJudgment =
            worstRecord?.judgment
                ?: "-"

        /*
         * 검사별 표시 문자열
         */
        val bottomText =
            buildSummaryLine(
                "BOTTOM CORNER",
                completedRecords
            )

        val sealText =
            buildSummaryLine(
                "SEAL",
                completedRecords
            )

        val formingText =
            buildSummaryLine(
                "FORMING",
                completedRecords
            )

        val tabText =
            buildSummaryLine(
                "TAB",
                completedRecords
            )

        val disassemblyText =
            buildSummaryLine(
                "DISASSEMBLY",
                completedRecords
            )

        /*
         * 5개 모두 완료
         */
        if (completedCount == 5) {

            binding.tvTotalSummary.text =
                String.format(
                    Locale.getDefault(),

                    """
종합검사 완료

완료 검사 : 5 / 5
평균 Score : %.1f / 100
종합 판정 : %s

──────────────

%s
%s
%s
%s
%s

──────────────

최저 Score
%s

※ 종합 판정은 5개 검사 중
가장 주의가 필요한 판정을 기준으로 표시합니다.

※ 현재 결과는 영상 기반 검사 보조지표이며,
최종 양산 OK/NG 기준으로 사용하려면
Master Sample 및 실제 불량품을 이용한 검증이 필요합니다.
                    """.trimIndent(),

                    averageScore,
                    finalJudgment,

                    bottomText,
                    sealText,
                    formingText,
                    tabText,
                    disassemblyText,

                    if (lowestRecord != null) {
                        String.format(
                            Locale.getDefault(),
                            "%s : %.1f점 / %s",
                            displayTypeName(
                                lowestRecord.inspectionType
                            ),
                            lowestRecord.score,
                            lowestRecord.judgment
                        )
                    } else {
                        "-"
                    }
                )

            binding.tvTotalSummary.setTextColor(
                judgmentColor(
                    finalJudgment
                )
            )

        } else {

            /*
             * 일부 검사만 완료
             */
            binding.tvTotalSummary.text =
                String.format(
                    Locale.getDefault(),

                    """
종합검사 진행 중

완료 검사 : %d / 5
현재 평균 Score : %.1f / 100
현재 판정 : %s

──────────────

%s
%s
%s
%s
%s

──────────────

남은 검사를 계속 진행해주세요.

각 검사 후 반드시
'결과 저장' 버튼을 눌러야
종합검사에 반영됩니다.
                    """.trimIndent(),

                    completedCount,
                    averageScore,
                    finalJudgment,

                    bottomText,
                    sealText,
                    formingText,
                    tabText,
                    disassemblyText
                )

            binding.tvTotalSummary.setTextColor(
                Color.parseColor(
                    "#334E68"
                )
            )
        }
    }

    /*
     * =========================================================
     * 종합 결과 한 줄 생성
     * =========================================================
     */

    private fun buildSummaryLine(
        type: String,
        records:
        List<InspectionHistoryStore.InspectionRecord>
    ): String {

        val record =
            records
                .filter {

                    it.inspectionType.equals(
                        type,
                        ignoreCase = true
                    )
                }
                .maxByOrNull {

                    it.id
                }

        return if (
            record == null
        ) {

            "${displayTypeName(type)} : 미검사"

        } else {

            String.format(
                Locale.getDefault(),
                "%s : %.1f점 / %s",
                displayTypeName(
                    type
                ),
                record.score,
                record.judgment
            )
        }
    }

    /*
     * =========================================================
     * 화면 표시용 이름
     * =========================================================
     */

    private fun displayTypeName(
        type: String
    ): String {

        return when {

            type.equals(
                "BOTTOM CORNER",
                ignoreCase = true
            ) ->
                "Bottom Corner"

            type.equals(
                "SEAL",
                ignoreCase = true
            ) ->
                "Seal"

            type.equals(
                "FORMING",
                ignoreCase = true
            ) ->
                "Forming"

            type.equals(
                "TAB",
                ignoreCase = true
            ) ->
                "Tab"

            type.equals(
                "DISASSEMBLY",
                ignoreCase = true
            ) ->
                "분해검사"

            else ->
                type
        }
    }

    /*
     * =========================================================
     * 판정 위험도
     *
     * 숫자가 클수록 더 주의가 필요한 판정
     * =========================================================
     */

    private fun judgmentSeverity(
        judgment: String
    ): Int {

        val text =
            judgment.lowercase(
                Locale.getDefault()
            )

        return when {

            text.contains(
                "불량"
            ) ->
                4

            text.contains(
                "한계"
            ) ->
                3

            text.contains(
                "주의"
            ) ->
                2

            text.contains(
                "정상"
            ) ->
                1

            else ->
                0
        }
    }

    /*
     * =========================================================
     * 판정별 문자색
     * =========================================================
     */

    private fun judgmentColor(
        judgment: String
    ): Int {

        val text =
            judgment.lowercase(
                Locale.getDefault()
            )

        return when {

            text.contains(
                "불량"
            ) ->
                Color.parseColor(
                    "#C62828"
                )

            text.contains(
                "한계"
            ) ->
                Color.parseColor(
                    "#EF6C00"
                )

            text.contains(
                "주의"
            ) ->
                Color.parseColor(
                    "#F9A825"
                )

            text.contains(
                "정상"
            ) ->
                Color.parseColor(
                    "#2E7D32"
                )

            else ->
                Color.parseColor(
                    "#334E68"
                )
        }
    }
}
