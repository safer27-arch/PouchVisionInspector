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
     * 현재 종합검사 1회의 시작 시각
     *
     * 기존 개별 검사 이력의 id가 저장 시각(System.currentTimeMillis)이므로
     * 이 시각 이후 저장된 5개 결과를 현재 종합검사 Session으로 봅니다.
     *
     * 이번 버전은 InspectionHistoryStore 구조를 다시 변경하지 않고
     * 현재 안정적으로 동작하는 저장 구조를 그대로 사용합니다.
     */
    private var sessionStartTime: Long = 0L

    /*
     * 자동 순차검사 진행 여부
     */
    private var autoSequenceActive = false

    /*
     * 직전에 실행한 검사 종류
     */
    private var launchedInspectionType: String? = null

    /*
     * 검사 화면을 연 시각
     *
     * 이 시각 이후에 같은 검사 결과가 새로 저장되어야
     * 해당 검사를 완료했다고 판단합니다.
     */
    private var launchedInspectionTime: Long = 0L

    /*
     * 검사 화면에서 결과 저장 후 복귀를 기다리는 상태
     */
    private var waitingForInspectionResult = false

    /*
     * 현재 종합검사 Session Summary가 이미 저장되었는지
     */
    private var totalSummarySaved = false

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

        private const val STATE_TOTAL_SUMMARY_SAVED =
            "total_inspection_summary_saved"

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

        /*
         * 종합검사 한 회를 대표하는 이력 타입
         *
         * 다음 단계에서 HistoryActivity가 이 기록을 읽어
         * 5개 검사 결과와 사진을 하나의 묶음으로 보여주게 됩니다.
         */
        private const val TYPE_TOTAL_SESSION =
            "TOTAL SESSION"
    }

    private val inspectionOrder =
        listOf(
            TYPE_BOTTOM,
            TYPE_SEAL,
            TYPE_FORMING,
            TYPE_TAB,
            TYPE_DISASSEMBLY
        )

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(savedInstanceState)

        binding =
            ActivityTotalInspectionBinding.inflate(
                layoutInflater
            )

        setContentView(
            binding.root
        )

        if (
            savedInstanceState != null
        ) {

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

            totalSummarySaved =
                savedInstanceState.getBoolean(
                    STATE_TOTAL_SUMMARY_SAVED,
                    false
                )

        } else {

            /*
             * 새로운 종합검사 Session
             */
            sessionStartTime =
                System.currentTimeMillis()

            totalSummarySaved =
                false
        }

        setupButtons()

        /*
         * 앱 재생성 등으로 Summary 저장 여부를 잃어도
         * 기존 이력에 같은 Session Summary가 있는지 다시 확인합니다.
         */
        totalSummarySaved =
            totalSummarySaved ||
                hasSavedTotalSessionSummary()

        refreshInspectionResults()
    }

    override fun onResume() {

        super.onResume()

        refreshInspectionResults()

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

        outState.putBoolean(
            STATE_TOTAL_SUMMARY_SAVED,
            totalSummarySaved
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

        binding.btnTotalBottom
            .setOnClickListener {

                startAutomaticSequence(
                    TYPE_BOTTOM
                )
            }

        binding.btnTotalSeal
            .setOnClickListener {

                startAutomaticSequence(
                    TYPE_SEAL
                )
            }

        binding.btnTotalForming
            .setOnClickListener {

                startAutomaticSequence(
                    TYPE_FORMING
                )
            }

        binding.btnTotalTab
            .setOnClickListener {

                startAutomaticSequence(
                    TYPE_TAB
                )
            }

        binding.btnTotalDisassembly
            .setOnClickListener {

                startAutomaticSequence(
                    TYPE_DISASSEMBLY
                )
            }

        binding.btnTotalRefresh
            .setOnClickListener {

                refreshInspectionResults()

                Toast.makeText(
                    this,
                    "현재 종합검사 결과를 새로고침했습니다.",
                    Toast.LENGTH_SHORT
                ).show()
            }

        binding.btnTotalHistory
            .setOnClickListener {

                startActivity(
                    Intent(
                        this,
                        HistoryActivity::class.java
                    )
                )
            }

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

        /*
         * 5개 완료 후 같은 화면에서 다시 검사 버튼을 누르면
         * 새로운 Session으로 시작합니다.
         */
        if (
            isSessionComplete()
        ) {

            startNewSession()
        }

        autoSequenceActive =
            true

        launchInspection(
            inspectionType
        )
    }

    /*
     * =========================================================
     * 새 종합검사 Session
     * =========================================================
     */

    private fun startNewSession() {

        sessionStartTime =
            System.currentTimeMillis()

        autoSequenceActive =
            false

        launchedInspectionType =
            null

        launchedInspectionTime =
            0L

        waitingForInspectionResult =
            false

        totalSummarySaved =
            false

        refreshInspectionResults()
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
            when (
                inspectionType
            ) {

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
     * 검사 화면에서 돌아온 후 결과 저장 확인
     * =========================================================
     */

    private fun checkReturnedInspection() {

        val type =
            launchedInspectionType
                ?: return

        val latestRecord =
            InspectionHistoryStore
                .load(
                    this
                )
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

        if (
            !successfullySaved
        ) {

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
         * 검사 저장 성공
         */
        launchedInspectionType =
            null

        launchedInspectionTime =
            0L

        refreshInspectionResults()

        if (
            !autoSequenceActive
        ) {

            return
        }

        val nextType =
            findNextIncompleteInspection()

        if (
            nextType == null
        ) {

            /*
             * 5개 모두 완료
             */
            autoSequenceActive =
                false

            saveTotalSessionSummaryIfNeeded()

            refreshInspectionResults()

            Toast.makeText(
                this,
                "5개 종합검사가 모두 완료되었습니다.\n종합검사 1회 이력이 저장되었습니다.",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        /*
         * 현재 결과를 잠시 보여준 뒤
         * 다음 검사 화면으로 자동 이동
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
     * 현재 Session 완료 여부
     * =========================================================
     */

    private fun isSessionComplete():
        Boolean {

        return findNextIncompleteInspection() ==
            null &&
            getCompletedInspectionRecords().size ==
            inspectionOrder.size
    }

    /*
     * =========================================================
     * 다음 미완료 검사
     * =========================================================
     */

    private fun findNextIncompleteInspection():
        String? {

        val records =
            getCurrentSessionHistory()

        for (
            type in inspectionOrder
        ) {

            val completed =
                records.any {

                    it.inspectionType.equals(
                        type,
                        ignoreCase = true
                    )
                }

            if (
                !completed
            ) {

                return type
            }
        }

        return null
    }

    /*
     * =========================================================
     * 현재 종합검사 Session의 개별 검사 이력
     * =========================================================
     */

    private fun getCurrentSessionHistory():
        List<InspectionHistoryStore.InspectionRecord> {

        return InspectionHistoryStore
            .load(
                this
            )
            .filter {

                it.id >=
                    sessionStartTime &&
                    inspectionOrder.any { type ->

                        it.inspectionType.equals(
                            type,
                            ignoreCase = true
                        )
                    }
            }
    }

    /*
     * =========================================================
     * 현재 Session에서 검사별 최신 결과 5개
     * =========================================================
     */

    private fun getCompletedInspectionRecords():
        List<InspectionHistoryStore.InspectionRecord> {

        val records =
            getCurrentSessionHistory()

        return inspectionOrder
            .mapNotNull { type ->

                findLatestRecord(
                    records,
                    type
                )
            }
    }

    /*
     * =========================================================
     * 종합검사 Summary가 이미 저장됐는지 확인
     * =========================================================
     */

    private fun hasSavedTotalSessionSummary():
        Boolean {

        val token =
            "SESSION_START=$sessionStartTime"

        return InspectionHistoryStore
            .load(
                this
            )
            .any {

                it.inspectionType.equals(
                    TYPE_TOTAL_SESSION,
                    ignoreCase = true
                ) &&
                    it.details.contains(
                        token
                    )
            }
    }

    /*
     * =========================================================
     * 종합검사 1회 Summary 저장
     *
     * InspectionHistoryStore 파일을 다시 수정하지 않고도
     * 현재 5개 결과를 하나의 Session으로 묶을 수 있도록,
     * 별도의 TOTAL SESSION 기록 1건을 저장합니다.
     *
     * 각 검사 record id를 Details에 함께 넣습니다.
     * 다음 단계에서 HistoryActivity가 이 id들을 이용해
     * 5개 결과와 사진을 한 화면에 묶어 보여줄 수 있습니다.
     * =========================================================
     */

    private fun saveTotalSessionSummaryIfNeeded() {

        if (
            totalSummarySaved ||
            hasSavedTotalSessionSummary()
        ) {

            totalSummarySaved =
                true

            return
        }

        val completedRecords =
            getCompletedInspectionRecords()

        if (
            completedRecords.size !=
            inspectionOrder.size
        ) {

            return
        }

        val averageScore =
            completedRecords
                .map {

                    it.score
                }
                .average()

        val worstRecord =
            completedRecords
                .maxByOrNull {

                    judgmentSeverity(
                        it.judgment
                    )
                }

        val finalJudgment =
            worstRecord
                ?.judgment
                ?: "-"

        val bottom =
            findLatestRecord(
                completedRecords,
                TYPE_BOTTOM
            )

        val seal =
            findLatestRecord(
                completedRecords,
                TYPE_SEAL
            )

        val forming =
            findLatestRecord(
                completedRecords,
                TYPE_FORMING
            )

        val tab =
            findLatestRecord(
                completedRecords,
                TYPE_TAB
            )

        val disassembly =
            findLatestRecord(
                completedRecords,
                TYPE_DISASSEMBLY
            )

        if (
            bottom == null ||
            seal == null ||
            forming == null ||
            tab == null ||
            disassembly == null
        ) {

            return
        }

        /*
         * 이 줄들은 다음 History 묶음 기능에서
         * 각 사진 기록을 정확하게 찾기 위한 내부 정보입니다.
         */
        val details =
            """
SESSION_START=$sessionStartTime
BOTTOM_ID=${bottom.id}
SEAL_ID=${seal.id}
FORMING_ID=${forming.id}
TAB_ID=${tab.id}
DISASSEMBLY_ID=${disassembly.id}

종합검사 완료

Bottom Corner : ${formatRecord(bottom)}
Seal : ${formatRecord(seal)}
Forming : ${formatRecord(forming)}
Tab : ${formatRecord(tab)}
분해검사 : ${formatRecord(disassembly)}

평균 Quality Score : ${String.format(Locale.getDefault(), "%.1f", averageScore)} / 100
종합 판정 : $finalJudgment

※ 종합 판정은 5개 항목 중 가장 주의가 필요한 판정을 기준으로 합니다.
※ 현재 결과는 영상 기반 검사 보조 결과입니다.
※ 실제 양산 OK/NG 판정에는 Spec, Master Sample 및 불량품 검증이 필요합니다.
            """.trimIndent()

        val success =
            InspectionHistoryStore.save(
                context = this,
                inspectionType = TYPE_TOTAL_SESSION,
                score = averageScore,
                judgment = finalJudgment,
                sensitivity = 0,
                details = details
            )

        totalSummarySaved =
            success
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
            when (
                type
            ) {

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

        if (
            record == null
        ) {

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

        if (
            completedCount ==
            0
        ) {

            binding.tvTotalSummary.text =
                """
종합검사 준비

진행 상태 : 0 / 5

1. Bottom Corner
2. Seal
3. Forming
4. Tab
5. 분해검사

Bottom Corner의 '검사' 버튼을 누르면
종합검사를 시작할 수 있습니다.

각 검사 화면에서 분석 후
반드시 '검사 결과 저장'을 눌러주세요.

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
            worstRecord
                ?.judgment
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

        if (
            completedCount ==
            5
        ) {

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

종합검사 1회 Summary가
검사 이력에 함께 저장됩니다.

다음 단계에서 History 화면에서
이 5개 검사와 결과 사진을 하나의 묶음으로 볼 수 있게 연결합니다.

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
'검사 결과 저장'을 눌러주세요.

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
            findLatestRecord(
                records,
                type
            )

        return if (
            record ==
            null
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

    private fun formatRecord(
        record:
        InspectionHistoryStore.InspectionRecord
    ): String {

        return String.format(
            Locale.getDefault(),
            "%.1f점 / %s",
            record.score,
            record.judgment
        )
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
