package com.pouchvision.inspector

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.pouchvision.inspector.databinding.ActivityTotalInspectionBinding
import java.util.Locale

class TotalInspectionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTotalInspectionBinding

    /*
     * 현재 종합검사 세션 시작 시각
     */
    private var sessionStartTime: Long = 0L

    /*
     * 자동 순차검사 진행 여부
     */
    private var autoSequenceActive = false

    /*
     * 직전에 실행한 검사
     */
    private var launchedInspectionType: String? = null

    /*
     * 해당 검사를 실행한 시각
     *
     * 이 시각 이후에 저장된 결과가 있어야
     * "검사를 완료했다"고 판단합니다.
     */
    private var launchedInspectionTime: Long = 0L

    /*
     * 검사 화면으로 실제 이동했는지 확인
     */
    private var waitingForInspectionResult = false

    companion object {

        private const val STATE_SESSION_START =
            "total_inspection_session_start"

        private const val STATE_AUTO_SEQUENCE =
            "total_inspection_auto_sequence"

        private const val STATE_LAUNCHED_TYPE =
            "total_inspection_launched_type"

        private const val STATE_LAUNCHED_TIME =
            "total_inspection_launched_time"

        private const val STATE_WAITING_RESULT =
            "total_inspection_waiting_result"

        private const val TYPE_BOTTOM =
            "BOTTOM CORNER"

        private const val TYPE_SEAL =
            "SEAL"

        private const val TYPE_FORMING =
            "FORMING"

        private const val TYPE_TAB =
            "TAB"

        private const val TYPE_DISASSEMBLY =
            "DISASSEMBLY"
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
         * 기존 세션 복원
         */
        if (savedInstanceState != null) {

            sessionStartTime =
                savedInstanceState.getLong(
                    STATE_SESSION_START,
                    System.currentTimeMillis()
                )

            autoSequenceActive =
                savedInstanceState.getBoolean(
                    STATE_AUTO_SEQUENCE,
                    false
                )

            launchedInspectionType =
                savedInstanceState.getString(
                    STATE_LAUNCHED_TYPE
                )

            launchedInspectionTime =
                savedInstanceState.getLong(
                    STATE_LAUNCHED_TIME,
                    0L
                )

            waitingForInspectionResult =
                savedInstanceState.getBoolean(
                    STATE_WAITING_RESULT,
                    false
                )

        } else {

            /*
             * 새로운 종합검사 세션
             */
            sessionStartTime =
                System.currentTimeMillis()
        }

        setupButtons()

        refreshInspectionResults()
    }

    override fun onResume() {

        super.onResume()

        refreshInspectionResults()

        /*
         * 검사 화면에서 돌아왔을 때만 확인
         */
        if (
            waitingForInspectionResult &&
            launchedInspectionType != null
        ) {

            checkReturnedInspection()
        }
    }

    override fun onSaveInstanceState(
        outState: Bundle
    ) {

        outState.putLong(
            STATE_SESSION_START,
            sessionStartTime
        )

        outState.putBoolean(
            STATE_AUTO_SEQUENCE,
            autoSequenceActive
        )

        outState.putString(
            STATE_LAUNCHED_TYPE,
            launchedInspectionType
        )

        outState.putLong(
            STATE_LAUNCHED_TIME,
            launchedInspectionTime
        )

        outState.putBoolean(
            STATE_WAITING_RESULT,
            waitingForInspectionResult
        )

        super.onSaveInstanceState(
            outState
        )
    }

    /*
     * =========================================================
     * 버튼
     * =========================================================
     */

    private fun setupButtons() {

        /*
         * Bottom Corner
         *
         * 첫 번째 검사를 누르면
         * 자동 순차검사 모드를 시작합니다.
         */
        binding.btnTotalBottom
            .setOnClickListener {

                startAutomaticSequence(
                    TYPE_BOTTOM
                )
            }

        /*
         * Seal
         */
        binding.btnTotalSeal
            .setOnClickListener {

                startAutomaticSequence(
                    TYPE_SEAL
                )
            }

        /*
         * Forming
         */
        binding.btnTotalForming
            .setOnClickListener {

                startAutomaticSequence(
                    TYPE_FORMING
                )
            }

        /*
         * Tab
         */
        binding.btnTotalTab
            .setOnClickListener {

                startAutomaticSequence(
                    TYPE_TAB
                )
            }

        /*
         * 분해검사
         */
        binding.btnTotalDisassembly
            .setOnClickListener {

                startAutomaticSequence(
                    TYPE_DISASSEMBLY
                )
            }

        /*
         * 새로고침
         */
        binding.btnTotalRefresh
            .setOnClickListener {

                refreshInspectionResults()

                Toast.makeText(
                    this,
                    "현재 종합검사 결과를 새로고침했습니다.",
                    Toast.LENGTH_SHORT
                ).show()
            }

        /*
         * 검사 이력
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
         * 메뉴로
         */
        binding.btnTotalBack
            .setOnClickListener {

                finish()
            }
    }

    /*
     * =========================================================
     * 자동 순차검사 시작
     * =========================================================
     */

    private fun startAutomaticSequence(
        inspectionType: String
    ) {

        autoSequenceActive =
            true

        launchInspection(
            inspectionType
        )
    }

    /*
     * =========================================================
     * 검사 화면 실행
     * =========================================================
     */

    private fun launchInspection(
        inspectionType: String
    ) {

        val intent =
            when (inspectionType) {

                TYPE_BOTTOM ->
                    Intent(
                        this,
                        MainActivity::class.java
                    )

                TYPE_SEAL ->
                    Intent(
                        this,
                        SealActivity::class.java
                    )

                TYPE_FORMING ->
                    Intent(
                        this,
                        FormingActivity::class.java
                    )

                TYPE_TAB ->
                    Intent(
                        this,
                        TabActivity::class.java
                    )

                TYPE_DISASSEMBLY ->
                    Intent(
                        this,
                        DisassemblyActivity::class.java
                    )

                else ->
                    return
            }

        launchedInspectionType =
            inspectionType

        launchedInspectionTime =
            System.currentTimeMillis()

        waitingForInspectionResult =
            true

        startActivity(
            intent
        )
    }

    /*
     * =========================================================
     * 검사 화면에서 돌아온 후 확인
     * =========================================================
     */

    private fun checkReturnedInspection() {

        val type =
            launchedInspectionType
                ?: return

        /*
         * 동일 검사의 가장 최근 저장 결과 확인
         */
        val latestRecord =
            InspectionHistoryStore
                .load(this)
                .filter {

                    it.inspectionType.equals(
                        type,
                        ignoreCase = true
                    )

                }
                .maxByOrNull {

                    it.id
                }

        /*
         * 검사 화면을 연 이후 새 결과가 저장됐는지 확인
         */
        val successfullySaved =
            latestRecord != null &&
                latestRecord.id >=
                launchedInspectionTime

        waitingForInspectionResult =
            false

        if (!successfullySaved) {

            /*
             * 저장하지 않고 뒤로 나온 경우
             */
            autoSequenceActive =
                false

            launchedInspectionType =
                null

            launchedInspectionTime =
                0L

            refreshInspectionResults()

            Toast.makeText(
                this,
                "검사 결과가 저장되지 않아 자동 순차검사를 일시 중지했습니다.",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        /*
         * 정상 저장 완료
         */
        launchedInspectionType =
            null

        launchedInspectionTime =
            0L

        refreshInspectionResults()

        if (!autoSequenceActive) {

            return
        }

        /*
         * 다음 미검사 항목 확인
         */
        val nextType =
            findNextIncompleteInspection()

        if (nextType == null) {

            /*
             * 5개 검사 모두 완료
             */
            autoSequenceActive =
                false

            Toast.makeText(
                this,
                "5개 종합검사가 모두 완료되었습니다.",
                Toast.LENGTH_LONG
            ).show()

            refreshInspectionResults()

            return
        }

        /*
         * 결과 화면을 잠시 보여준 뒤
         * 다음 검사로 자동 이동
         */
        Handler(
            Looper.getMainLooper()
        ).postDelayed({

            if (
                !isFinishing &&
                !isDestroyed
            ) {

                Toast.makeText(
                    this,
                    "다음 검사 : ${displayTypeName(nextType)}",
                    Toast.LENGTH_SHORT
                ).show()

                launchInspection(
                    nextType
                )
            }

        }, 700L)
    }

    /*
     * =========================================================
     * 다음 미완료 검사 찾기
     * =========================================================
     */

    private fun findNextIncompleteInspection():
        String? {

        val records =
            getCurrentSessionHistory()

        val order =
            listOf(
                TYPE_BOTTOM,
                TYPE_SEAL,
                TYPE_FORMING,
                TYPE_TAB,
                TYPE_DISASSEMBLY
            )

        for (
            type in order
        ) {

            val completed =
                records.any {

                    it.inspectionType.equals(
                        type,
                        ignoreCase = true
                    )
                }

            if (!completed) {

                return type
            }
        }

        return null
    }

    /*
     * =========================================================
     * 현재 세션 기록
     * =========================================================
     */

    private fun getCurrentSessionHistory():
        List<InspectionHistoryStore.InspectionRecord> {

        return InspectionHistoryStore
            .load(this)
            .filter {

                it.id >=
                    sessionStartTime
            }
    }

    /*
     * =========================================================
     * 화면 결과 갱신
     * =========================================================
     */

    private fun refreshInspectionResults() {

        val sessionHistory =
            getCurrentSessionHistory()

        val bottomRecord =
            findLatestRecord(
                sessionHistory,
                TYPE_BOTTOM
            )

        val sealRecord =
            findLatestRecord(
                sessionHistory,
                TYPE_SEAL
            )

        val formingRecord =
            findLatestRecord(
                sessionHistory,
                TYPE_FORMING
            )

        val tabRecord =
            findLatestRecord(
                sessionHistory,
                TYPE_TAB
            )

        val disassemblyRecord =
            findLatestRecord(
                sessionHistory,
                TYPE_DISASSEMBLY
            )

        /*
         * 검사별 상태
         */
        updateStatusView(
            TYPE_BOTTOM,
            bottomRecord
        )

        updateStatusView(
            TYPE_SEAL,
            sealRecord
        )

        updateStatusView(
            TYPE_FORMING,
            formingRecord
        )

        updateStatusView(
            TYPE_TAB,
            tabRecord
        )

        updateStatusView(
            TYPE_DISASSEMBLY,
            disassemblyRecord
        )

        /*
         * 완료 검사
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
     * 최신 결과 찾기
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
     * 각 검사 상태
     * =========================================================
     */

    private fun updateStatusView(
        type: String,
        record:
        InspectionHistoryStore.InspectionRecord?
    ) {

        val target =
            when (type) {

                TYPE_BOTTOM ->
                    binding.tvTotalBottomStatus

                TYPE_SEAL ->
                    binding.tvTotalSealStatus

                TYPE_FORMING ->
                    binding.tvTotalFormingStatus

                TYPE_TAB ->
                    binding.tvTotalTabStatus

                TYPE_DISASSEMBLY ->
                    binding.tvTotalDisassemblyStatus

                else ->
                    return
            }

        if (record == null) {

            target.text =
                "미검사"

            target.setTextColor(
                Color.parseColor(
                    "#829AB1"
                )
            )

        } else {

            target.text =
                String.format(
                    Locale.getDefault(),
                    "완료  |  %.1f점  |  %s",
                    record.score,
                    record.judgment
                )

            target.setTextColor(
                judgmentColor(
                    record.judgment
                )
            )
        }
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
         * 아직 시작 전
         */
        if (completedCount == 0) {

            binding.tvTotalSummary.text =
                """
종합검사 준비

진행 상태 : 0 / 5

1. Bottom Corner
2. Seal
3. Forming
4. Tab
5. 분해검사

1번 Bottom Corner의
'검사' 버튼을 누르면 종합검사를 시작할 수 있습니다.

각 검사 화면에서 분석 후
반드시 '결과 저장'을 눌러주세요.

저장 후 뒤로 돌아오면
다음 검사가 자동으로 시작됩니다.
                """.trimIndent()

            binding.tvTotalSummary.setTextColor(
                Color.parseColor(
                    "#334E68"
                )
            )

            return
        }

        val averageScore =
            completedRecords
                .map {

                    it.score
                }
                .average()

        val lowestRecord =
            completedRecords
                .minByOrNull {

                    it.score
                }

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

        val bottomText =
            buildSummaryLine(
                TYPE_BOTTOM,
                completedRecords
            )

        val sealText =
            buildSummaryLine(
                TYPE_SEAL,
                completedRecords
            )

        val formingText =
            buildSummaryLine(
                TYPE_FORMING,
                completedRecords
            )

        val tabText =
            buildSummaryLine(
                TYPE_TAB,
                completedRecords
            )

        val disassemblyText =
            buildSummaryLine(
                TYPE_DISASSEMBLY,
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

진행 상태 : 5 / 5

평균 Score : %.1f / 100
종합 판정 : %s

────────────────

%s
%s
%s
%s
%s

────────────────

최저 Score
%s

종합 판정은 5개 항목 중
가장 주의가 필요한 판정을 기준으로 합니다.

※ 현재 판정은 영상 기반 검사 보조 결과입니다.
※ 실제 양산 OK/NG 판정에는
   Spec, Master Sample 및 불량품 검증이 필요합니다.
                    """.trimIndent(),

                    averageScore,
                    finalJudgment,

                    bottomText,
                    sealText,
                    formingText,
                    tabText,
                    disassemblyText,

                    if (
                        lowestRecord != null
                    ) {

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
             * 일부 완료
             */
            val nextType =
                findNextIncompleteInspection()

            binding.tvTotalSummary.text =
                String.format(
                    Locale.getDefault(),

                    """
종합검사 진행 중

진행 상태 : %d / 5
현재 평균 Score : %.1f / 100
현재 판정 : %s

다음 검사 : %s

────────────────

%s
%s
%s
%s
%s

────────────────

각 검사 후 반드시
'결과 저장'을 눌러주세요.

정상 저장 후 뒤로 돌아오면
다음 검사가 자동으로 시작됩니다.
                    """.trimIndent(),

                    completedCount,
                    averageScore,
                    finalJudgment,

                    if (
                        nextType != null
                    ) {

                        displayTypeName(
                            nextType
                        )

                    } else {

                        "-"
                    },

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
     * 검사 결과 한 줄
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
     * 화면용 검사명
     * =========================================================
     */

    private fun displayTypeName(
        type: String
    ): String {

        return when {

            type.equals(
                TYPE_BOTTOM,
                ignoreCase = true
            ) ->
                "Bottom Corner"

            type.equals(
                TYPE_SEAL,
                ignoreCase = true
            ) ->
                "Seal"

            type.equals(
                TYPE_FORMING,
                ignoreCase = true
            ) ->
                "Forming"

            type.equals(
                TYPE_TAB,
                ignoreCase = true
            ) ->
                "Tab"

            type.equals(
                TYPE_DISASSEMBLY,
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
     * 판정 색상
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
