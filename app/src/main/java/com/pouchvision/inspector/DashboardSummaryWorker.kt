package com.pouchvision.inspector

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class DashboardSummaryWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : Worker(
    appContext,
    workerParams
) {

    override fun doWork(): Result {

        val settings =
            TelegramSettingsStore.load(
                applicationContext
            )

        val testMode =
            inputData.getBoolean(
                "test_mode",
                false
            )

        if (
            !settings.enabled ||
            (!settings.dashboardSummaryEnabled && !testMode) ||
            settings.botToken.isBlank() ||
            settings.chatIds.isEmpty()
        ) {
            return Result.success()
        }

        val now =
            System.currentTimeMillis()

        val start =
            now -
                TimeUnit.HOURS.toMillis(
                    settings.dashboardSummaryIntervalHours.toLong()
                )

        val production =
            ProductionContextStore.getCurrent(
                applicationContext
            )

        /*
         * 현재 선택된 Model / Line 기준으로 Summary를 만듭니다.
         * 과거 이력 중 Model/Line 정보가 없는 기록은 제외합니다.
         */
        val records =
            InspectionHistoryStore.load(
                applicationContext
            )
                .filter {
                    it.id in start..now &&
                    it.inspectionType != "TOTAL SESSION" &&
                    it.model == production.model &&
                    it.line == production.line
                }

        val message =
            buildSummaryMessage(
                records = records,
                model = production.model,
                line = production.line,
                intervalHours = settings.dashboardSummaryIntervalHours,
                zeroWarning = settings.dashboardZeroWarning,
                missingItemWarning = settings.dashboardMissingItemWarning,
                lowCountWarning = settings.dashboardLowCountWarning,
                minCount = settings.dashboardMinCount
            )

        val summaryDeliveryRecordId =
            TelegramDeliveryStore.createDashboardSummaryPending(
                context = applicationContext,
                model = production.model,
                line = production.line,
                intervalHours = settings.dashboardSummaryIntervalHours,
                testMode = testMode
            )

        val latch =
            CountDownLatch(
                1
            )

        var sendResult:
            TelegramSender.SendResult? =
            null

        TelegramSender.sendDashboardSummary(
            context = applicationContext,
            message = message,
            callback = { result ->
                sendResult =
                    result

                latch.countDown()
            },
            allowWhenSummaryOff = testMode
        )

        val completed =
            try {
                latch.await(
                    45,
                    TimeUnit.SECONDS
                )
            } catch (
                e: InterruptedException
            ) {
                Thread.currentThread()
                    .interrupt()

                false
            }

        if (
            !completed
        ) {

            TelegramDeliveryStore.updateResult(
                context = applicationContext,
                recordId = summaryDeliveryRecordId,
                success = false,
                successCount = 0,
                failureCount = settings.chatIds.size,
                message = "Dashboard Summary 전송 시간 초과"
            )

            return Result.retry()
        }

        val finalResult =
            sendResult

        TelegramDeliveryStore.updateResult(
            context = applicationContext,
            recordId = summaryDeliveryRecordId,
            success = finalResult?.success == true,
            successCount = finalResult?.successCount ?: 0,
            failureCount = finalResult?.failureCount ?: settings.chatIds.size,
            message =
                finalResult?.message
                    ?: "Dashboard Summary 전송 결과 없음"
        )

        return if (
            finalResult?.success ==
            true
        ) {
            Result.success()
        } else {
            Result.retry()
        }
    }

    private fun buildSummaryMessage(
        records:
        List<
            InspectionHistoryStore
                .InspectionRecord
            >,
        model: String,
        line: String,
        intervalHours: Int,
        zeroWarning: Boolean,
        missingItemWarning: Boolean,
        lowCountWarning: Boolean,
        minCount: Int
    ): String {

        val inspectionTypes =
            listOf(
                "BOTTOM CORNER",
                "SEAL",
                "FORMING",
                "TAB",
                "DISASSEMBLY"
            )

        val normalCount =
            records.count {
                severity(
                    it.judgment
                ) ==
                    1
            }

        val warningCount =
            records.count {
                severity(
                    it.judgment
                ) ==
                    2
            }

        val limitCount =
            records.count {
                severity(
                    it.judgment
                ) ==
                    3
            }

        val ngCount =
            records.count {
                severity(
                    it.judgment
                ) >=
                    4
            }

        val missingTypes =
            inspectionTypes.filter { type ->
                records.none {
                    normalizeType(
                        it.inspectionType
                    ) ==
                        type
                }
            }

        val average =
            if (
                records.isEmpty()
            ) {
                0.0
            } else {
                records
                    .map {
                        it.score
                    }
                    .average()
            }

        val dateFormat =
            SimpleDateFormat(
                "yyyy-MM-dd HH:mm",
                Locale.getDefault()
            )

        val lastRecord =
            InspectionHistoryStore.load(
                applicationContext
            )
                .filter {
                    it.inspectionType !=
                        "TOTAL SESSION" &&
                    it.model ==
                        model &&
                    it.line ==
                        line
                }
                .maxByOrNull {
                    it.id
                }

        return buildString {

            if (
                records.isEmpty() &&
                zeroWarning
            ) {

                append(
                    "🚨 Pouch 검사 미실시 경고"
                )

            } else {

                append(
                    "📊 Pouch 품질 Dashboard Summary"
                )
            }

            append(
                "\n\nModel : $model"
            )

            append(
                "\nLine : $line"
            )

            append(
                "\n집계기간 : 최근 ${intervalHours}시간"
            )

            append(
                "\n전송시각 : ${dateFormat.format(Date())}"
            )

            append(
                "\n\n검사 건수 : ${records.size}건"
            )

            if (
                records.isNotEmpty()
            ) {

                append(
                    "\n평균 Quality Score : ${String.format(Locale.getDefault(), "%.1f", average)} / 100"
                )

                append(
                    "\n정상 $normalCount | 주의 $warningCount | 한계정상 $limitCount | 불량 $ngCount"
                )
            }

            if (
                records.isEmpty() &&
                zeroWarning
            ) {

                append(
                    "\n\n⚠ 최근 ${intervalHours}시간 동안 저장된 검사 데이터가 없습니다."
                )

                append(
                    "\n작업자 검사 누락 또는 앱 사용 상태를 확인해주세요."
                )

                if (
                    lastRecord !=
                    null
                ) {

                    append(
                        "\n\n마지막 검사 : ${lastRecord.dateTime}"
                    )

                    append(
                        "\n마지막 항목 : ${normalizeType(lastRecord.inspectionType)}"
                    )

                    append(
                        "\n마지막 판정 : ${lastRecord.judgment}"
                    )
                }
            }

            if (
                records.isNotEmpty() &&
                missingItemWarning &&
                missingTypes.isNotEmpty()
            ) {

                append(
                    "\n\n⚠ 검사 누락 항목"
                )

                inspectionTypes.forEach { type ->

                    val count =
                        records.count {
                            normalizeType(
                                it.inspectionType
                            ) ==
                                type
                        }

                    append(
                        "\n$type : ${count}건 ${if (count == 0) "⚠" else "✅"}"
                    )
                }
            }

            if (
                lowCountWarning &&
                records.isNotEmpty() &&
                records.size <
                minCount
            ) {

                append(
                    "\n\n⚠ 검사 횟수 부족"
                )

                append(
                    "\n기준 : 최소 ${minCount}건 / 실제 : ${records.size}건"
                )
            }

            if (
                records.isNotEmpty()
            ) {

                val riskSignals =
                    buildRiskSignals(
                        records
                    )

                if (
                    riskSignals.isNotEmpty()
                ) {

                    append(
                        "\n\n🚦 품질 위험 신호"
                    )

                    riskSignals
                        .take(
                            5
                        )
                        .forEach { signal ->

                            append(
                                "\n"
                            )

                            append(
                                signal
                            )
                        }
                }

                append(
                    "\n\n검사별 평균"
                )

                inspectionTypes.forEach { type ->

                    val itemRecords =
                        records.filter {
                            normalizeType(
                                it.inspectionType
                            ) ==
                                type
                        }

                    if (
                        itemRecords.isNotEmpty()
                    ) {

                        append(
                            "\n$type : ${
                                String.format(
                                    Locale.getDefault(),
                                    "%.1f",
                                    itemRecords.map { it.score }.average()
                                )
                            } / ${itemRecords.size}건"
                        )
                    }
                }

                val worst =
                    records.minByOrNull {
                        it.score
                    }

                if (
                    worst !=
                    null
                ) {

                    append(
                        "\n\n🔎 우선 확인"
                    )

                    append(
                        "\n${normalizeType(worst.inspectionType)} · Score ${
                            String.format(
                                Locale.getDefault(),
                                "%.1f",
                                worst.score
                            )
                        } · ${worst.judgment}"
                    )
                }
            }
        }
    }

    private fun buildRiskSignals(
        records:
        List<
            InspectionHistoryStore
                .InspectionRecord
            >
    ): List<String> {

        val inspectionTypes =
            listOf(
                "BOTTOM CORNER",
                "SEAL",
                "FORMING",
                "TAB",
                "DISASSEMBLY"
            )

        val result =
            mutableListOf<
                Pair<
                    Int,
                    String
                >
            >()

        inspectionTypes.forEach { type ->

            val itemRecords =
                records
                    .filter {
                        normalizeType(
                            it.inspectionType
                        ) ==
                            type
                    }
                    .sortedBy {
                        it.id
                    }

            if (
                itemRecords.isEmpty()
            ) {
                return@forEach
            }

            val latest =
                itemRecords.last()

            val latestSeverity =
                severity(
                    latest.judgment
                )

            if (
                latestSeverity >=
                4
            ) {

                result.add(
                    4 to
                        "🚨 $type : 최근 불량 · Score ${
                            String.format(
                                Locale.getDefault(),
                                "%.1f",
                                latest.score
                            )
                        }"
                )

            } else if (
                latestSeverity ==
                3
            ) {

                result.add(
                    3 to
                        "⚠️ $type : 최근 한계정상 · Score ${
                            String.format(
                                Locale.getDefault(),
                                "%.1f",
                                latest.score
                            )
                        }"
                )
            }

            if (
                itemRecords.size >=
                3
            ) {

                val last3 =
                    itemRecords
                        .takeLast(
                            3
                        )

                val falling =
                    last3[0].score >
                        last3[1].score &&
                    last3[1].score >
                        last3[2].score

                if (
                    falling
                ) {

                    val drop =
                        last3.first().score -
                            last3.last().score

                    result.add(
                        3 to
                            "📉 $type : 3회 연속 Score 하락 · ${
                                String.format(
                                    Locale.getDefault(),
                                    "%.1f",
                                    last3.first().score
                                )
                            } → ${
                                String.format(
                                    Locale.getDefault(),
                                    "%.1f",
                                    last3.last().score
                                )
                            } (▼${
                                String.format(
                                    Locale.getDefault(),
                                    "%.1f",
                                    drop
                                )
                            })"
                    )
                }
            }

            if (
                itemRecords.size >=
                6
            ) {

                val previous3 =
                    itemRecords
                        .dropLast(
                            3
                        )
                        .takeLast(
                            3
                        )
                        .map {
                            it.score
                        }
                        .average()

                val recent3 =
                    itemRecords
                        .takeLast(
                            3
                        )
                        .map {
                            it.score
                        }
                        .average()

                val drop =
                    previous3 -
                        recent3

                if (
                    drop >=
                    5.0
                ) {

                    val level =
                        if (
                            drop >=
                            10.0
                        ) {
                            4
                        } else {
                            3
                        }

                    result.add(
                        level to
                            "${if (level == 4) "🚨" else "⚠️"} $type : 최근 3회 평균 악화 · ${
                                String.format(
                                    Locale.getDefault(),
                                    "%.1f",
                                    previous3
                                )
                            } → ${
                                String.format(
                                    Locale.getDefault(),
                                    "%.1f",
                                    recent3
                                )
                            }"
                    )
                }
            }

            if (
                itemRecords.size >=
                4
            ) {

                val issueCount =
                    itemRecords.count {
                        severity(
                            it.judgment
                        ) >=
                            2
                    }

                val issueRate =
                    issueCount
                        .toDouble() /
                        itemRecords.size
                        .toDouble() *
                        100.0

                if (
                    issueRate >=
                    50.0
                ) {

                    val level =
                        if (
                            issueRate >=
                            75.0
                        ) {
                            4
                        } else {
                            3
                        }

                    result.add(
                        level to
                            "${if (level == 4) "🚨" else "⚠️"} $type : 이상 판정 비율 ${
                                String.format(
                                    Locale.getDefault(),
                                    "%.0f",
                                    issueRate
                                )
                            }% (${issueCount}/${itemRecords.size})"
                    )
                }
            }
        }

        return result
            .sortedByDescending {
                it.first
            }
            .map {
                it.second
            }
            .distinct()
    }

    private fun normalizeType(
        value: String
    ): String {

        return when (
            value
                .trim()
                .uppercase(
                    Locale.US
                )
                .replace(
                    "_",
                    " "
                )
        ) {

            "DISASSEMBLY",
            "분해검사" ->
                "DISASSEMBLY"

            else ->
                value
                    .trim()
                    .uppercase(
                        Locale.US
                    )
                    .replace(
                        "_",
                        " "
                    )
        }
    }

    private fun severity(
        judgment: String
    ): Int {

        val value =
            judgment
                .trim()

        return when {

            value.contains(
                "불량"
            ) ->
                4

            value.contains(
                "한계"
            ) ->
                3

            value.contains(
                "주의"
            ) ->
                2

            else ->
                1
        }
    }

    companion object {

        private const val UNIQUE_WORK_NAME =
            "pouch_dashboard_summary"

        fun sendTestNow(
            context: Context
        ) {

            val constraints =
                Constraints.Builder()
                    .setRequiredNetworkType(
                        NetworkType.CONNECTED
                    )
                    .build()

            val request =
                androidx.work.OneTimeWorkRequestBuilder<DashboardSummaryWorker>()
                    .setInputData(
                        androidx.work.workDataOf(
                            "test_mode" to true
                        )
                    )
                    .setConstraints(
                        constraints
                    )
                    .build()

            WorkManager.getInstance(
                context.applicationContext
            )
                .enqueue(
                    request
                )
        }

        fun applySchedule(
            context: Context
        ) {

            val settings =
                TelegramSettingsStore.load(
                    context
                )

            val workManager =
                WorkManager.getInstance(
                    context.applicationContext
                )

            if (
                !settings.dashboardSummaryEnabled
            ) {

                workManager.cancelUniqueWork(
                    UNIQUE_WORK_NAME
                )

                return
            }

            val constraints =
                Constraints.Builder()
                    .setRequiredNetworkType(
                        NetworkType.CONNECTED
                    )
                    .build()

            val request =
                PeriodicWorkRequestBuilder<DashboardSummaryWorker>(
                    settings.dashboardSummaryIntervalHours.toLong(),
                    TimeUnit.HOURS
                )
                    .setConstraints(
                        constraints
                    )
                    .build()

            workManager.enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }
}
