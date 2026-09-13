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
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ShiftWeeklyReportWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : Worker(appContext, workerParams) {

    override fun doWork(): Result {
        val settings = TelegramSettingsStore.load(applicationContext)

        if (!settings.enabled || settings.botToken.isBlank() || settings.chatIds.isEmpty()) {
            return Result.success()
        }

        /*
         * 수동 테스트는 실제 07:00 / 19:00 / 월요일 자동전송 기록과
         * 완전히 분리합니다. 따라서 테스트를 여러 번 눌러도
         * 실제 자동 리포트 전송에는 영향을 주지 않습니다.
         */
        when (
            inputData.getString(
                INPUT_MANUAL_TEST
            )
        ) {

            TEST_SHIFT -> {

                val end =
                    Calendar.getInstance()

                val start =
                    (
                        end.clone() as
                            Calendar
                        ).apply {

                        add(
                            Calendar.HOUR_OF_DAY,
                            -12
                        )
                    }

                return if (
                    sendShiftReport(
                        shiftName = "TEST 최근 12시간",
                        start = start,
                        end = end
                    )
                ) {
                    Result.success()
                } else {
                    Result.retry()
                }
            }

            TEST_WEEKLY -> {

                val end =
                    Calendar.getInstance()

                val start =
                    (
                        end.clone() as
                            Calendar
                        ).apply {

                        add(
                            Calendar.DAY_OF_YEAR,
                            -7
                        )
                    }

                return if (
                    sendWeeklyReport(
                        start = start,
                        end = end
                    )
                ) {
                    Result.success()
                } else {
                    Result.retry()
                }
            }
        }

        val now = Calendar.getInstance()
        val hour = now.get(Calendar.HOUR_OF_DAY)
        var retryNeeded = false

        if (hour >= 7) {
            val end = boundaryToday(now, 7)
            val shiftKey = "SHIFT_NIGHT_" + keyFormat.format(end.time)

            if (!wasSent(shiftKey)) {
                val start = (end.clone() as Calendar).apply {
                    add(Calendar.HOUR_OF_DAY, -12)
                }

                if (sendShiftReport("야간조", start, end)) {
                    markSent(shiftKey)
                } else {
                    retryNeeded = true
                }
            }

            if (now.get(Calendar.DAY_OF_WEEK) == Calendar.MONDAY) {
                val weeklyKey = "WEEKLY_" + keyFormat.format(end.time)

                if (!wasSent(weeklyKey)) {
                    val start = (end.clone() as Calendar).apply {
                        add(Calendar.DAY_OF_YEAR, -7)
                    }

                    if (sendWeeklyReport(start, end)) {
                        markSent(weeklyKey)
                    } else {
                        retryNeeded = true
                    }
                }
            }
        }

        if (hour >= 19) {
            val end = boundaryToday(now, 19)
            val shiftKey = "SHIFT_DAY_" + keyFormat.format(end.time)

            if (!wasSent(shiftKey)) {
                val start = (end.clone() as Calendar).apply {
                    add(Calendar.HOUR_OF_DAY, -12)
                }

                if (sendShiftReport("주간조", start, end)) {
                    markSent(shiftKey)
                } else {
                    retryNeeded = true
                }
            }
        }

        /*
         * 60분 검사 누락 자동 알림
         *
         * - 현재 진행 중인 1시간 구간은 검사하지 않습니다.
         * - 직전에 완전히 종료된 1시간 구간만 확인합니다.
         * - 해당 Model / Line에 개별 검사 결과가 1건도 없으면 Telegram 전송.
         * - 같은 시간대는 한 번만 전송합니다.
         * - 07:00 / 19:00 교대 경계에서도 직전 1시간을 정상 확인합니다.
         */
        if (!checkMissedInspectionAlert(now)) {
            retryNeeded = true
        }

        return if (retryNeeded) Result.retry() else Result.success()
    }

    private fun checkMissedInspectionAlert(
        now: Calendar
    ): Boolean {

        val settings =
            TelegramSettingsStore.load(
                applicationContext
            )

        if (!settings.missedInspectionEnabled) {
            return true
        }

        val intervalHours =
            settings.missedInspectionIntervalHours
                .coerceAtLeast(1)

        val shiftStart =
            currentShiftStart(
                now
            )

        val elapsedMillis =
            now.timeInMillis -
                shiftStart.timeInMillis

        val intervalMillis =
            TimeUnit.HOURS.toMillis(
                intervalHours.toLong()
            )

        /*
         * 아직 첫 검사 주기가 끝나지 않았다면
         * 누락 여부를 판정하지 않습니다.
         */
        if (elapsedMillis < intervalMillis) {
            return true
        }

        /*
         * 교대 시작시간(07:00 / 19:00)을 기준으로
         * 가장 최근에 완전히 끝난 검사 구간을 계산합니다.
         *
         * 예)
         * 2시간: 07~09, 09~11, 11~13 ...
         * 3시간: 07~10, 10~13, 13~16 ...
         */
        val completedIntervals =
            (
                elapsedMillis /
                    intervalMillis
                ).toInt()

        if (completedIntervals <= 0) {
            return true
        }

        val slotEnd =
            (shiftStart.clone() as Calendar).apply {
                add(
                    Calendar.HOUR_OF_DAY,
                    completedIntervals *
                        intervalHours
                )
            }

        val slotStart =
            (slotEnd.clone() as Calendar).apply {
                add(
                    Calendar.HOUR_OF_DAY,
                    -intervalHours
                )
            }

        val production =
            ProductionContextStore.getCurrent(
                applicationContext
            )

        val records =
            loadRecords(
                slotStart.timeInMillis,
                slotEnd.timeInMillis,
                production.model,
                production.line
            )

        if (records.isNotEmpty()) {
            return true
        }

        val alertKey =
            "MISSED_" +
                production.model +
                "_" +
                production.line +
                "_" +
                intervalHours +
                "H_" +
                keyFormat.format(
                    slotStart.time
                )

        if (wasSent(alertKey)) {
            return true
        }

        val message =
            buildString {
                append("⚠ Pouch 정기검사 미실시 알림")
                append("\nModel : ${production.model}")
                append("\nLine : ${production.line}")
                append(
                    "\n누락 시간대 : " +
                        displayFormat.format(
                            slotStart.time
                        ) +
                        " ~ " +
                        displayFormat.format(
                            slotEnd.time
                        )
                )
                append(
                    "\n검사 기준 : ${intervalHours}시간마다 최소 1회"
                )
                append("\n상태 : 해당 검사 구간 이력 0건")
                append("\n\n📌 다음 검사 주기 내 검사를 실시해주세요.")
            }

        val success =
            sendReport(
                reportType =
                    "MISSED INSPECTION",
                judgment =
                    "${intervalHours}시간 검사 미실시",
                model =
                    production.model,
                line =
                    production.line,
                message =
                    message,
                images =
                    emptyList()
            )

        if (success) {
            markSent(
                alertKey
            )
        }

        return success
    }

    private fun currentShiftStart(
        now: Calendar
    ): Calendar {

        val hour =
            now.get(
                Calendar.HOUR_OF_DAY
            )

        return if (hour >= 7 && hour < 19) {

            (now.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, 7)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

        } else if (hour >= 19) {

            (now.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, 19)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

        } else {

            (now.clone() as Calendar).apply {
                add(
                    Calendar.DAY_OF_YEAR,
                    -1
                )
                set(Calendar.HOUR_OF_DAY, 19)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
        }
    }

    private fun sendShiftReport(
        shiftName: String,
        start: Calendar,
        end: Calendar
    ): Boolean {
        val production = ProductionContextStore.getCurrent(applicationContext)
        val records = loadRecords(
            start.timeInMillis,
            end.timeInMillis,
            production.model,
            production.line
        )

        val message = buildShiftMessage(
            shiftName,
            start,
            end,
            production.model,
            production.line,
            records
        )

        // 교대조 리포트: 한계정상/불량 이미지는 모두 첨부
        val images =
            records
                .filter { severity(it.judgment) >= 3 && it.imagePath.isNotBlank() }
                .sortedWith(
                    compareByDescending<InspectionHistoryStore.InspectionRecord> {
                        severity(it.judgment)
                    }.thenBy { it.score }
                )
                .map {
                    TelegramSender.ReportImageAttachment(
                        imagePath = it.imagePath,
                        caption =
                            "📷 $shiftName 이상 결과\n" +
                                "Model : ${production.model}\n" +
                                "Line : ${production.line}\n" +
                                "검사항목 : ${normalizeType(it.inspectionType)}\n" +
                                "판정 : ${it.judgment}\n" +
                                "Score : ${fmt(it.score)}"
                    )
                }

        return sendReport(
            reportType = "SHIFT REPORT",
            judgment = "$shiftName / 12시간",
            model = production.model,
            line = production.line,
            message = message,
            images = images
        )
    }

    private fun sendWeeklyReport(
        start: Calendar,
        end: Calendar
    ): Boolean {
        val production = ProductionContextStore.getCurrent(applicationContext)

        val records = loadRecords(
            start.timeInMillis,
            end.timeInMillis,
            production.model,
            production.line
        )

        val fourWeekStart = (end.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, -28)
        }

        val fourWeekRecords = loadRecords(
            fourWeekStart.timeInMillis,
            end.timeInMillis,
            production.model,
            production.line
        )

        val message = buildWeeklyMessage(
            start,
            end,
            production.model,
            production.line,
            records,
            fourWeekRecords
        )

        // Weekly: 항목별 최악 결과 최대 1장씩만 대표 첨부
        val images =
            representativeAbnormal(records)
                .filter { it.imagePath.isNotBlank() }
                .map {
                    TelegramSender.ReportImageAttachment(
                        imagePath = it.imagePath,
                        caption =
                            "📷 Weekly 대표 이상 이미지\n" +
                                "Model : ${production.model}\n" +
                                "Line : ${production.line}\n" +
                                "검사항목 : ${normalizeType(it.inspectionType)}\n" +
                                "판정 : ${it.judgment}\n" +
                                "Score : ${fmt(it.score)}"
                    )
                }

        return sendReport(
            reportType = "WEEKLY REPORT",
            judgment = "AUTO / 월요일 07:00",
            model = production.model,
            line = production.line,
            message = message,
            images = images
        )
    }

    private fun sendReport(
        reportType: String,
        judgment: String,
        model: String,
        line: String,
        message: String,
        images: List<TelegramSender.ReportImageAttachment>
    ): Boolean {
        val deliveryId =
            TelegramDeliveryStore.createPending(
                context = applicationContext,
                model = model,
                line = line,
                inspectionType = reportType,
                score = 0.0,
                judgment = judgment,
                resultBitmap = null
            )

        val latch = CountDownLatch(1)
        var sendResult: TelegramSender.SendResult? = null

        TelegramSender.sendReportWithImages(
            context = applicationContext,
            message = message,
            images = images,
            callback = {
                sendResult = it
                latch.countDown()
            }
        )

        val completed =
            try {
                latch.await(180, TimeUnit.SECONDS)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                false
            }

        if (!completed) {
            TelegramDeliveryStore.updateResult(
                context = applicationContext,
                recordId = deliveryId,
                success = false,
                successCount = 0,
                failureCount = 1,
                message = "$reportType 전송 시간 초과"
            )
            return false
        }

        val result = sendResult

        TelegramDeliveryStore.updateResult(
            context = applicationContext,
            recordId = deliveryId,
            success = result?.success == true,
            successCount = result?.successCount ?: 0,
            failureCount = result?.failureCount ?: 1,
            message = result?.message ?: "$reportType 전송 결과 없음"
        )

        return result?.success == true
    }

    private fun loadRecords(
        start: Long,
        end: Long,
        model: String,
        line: String
    ): List<InspectionHistoryStore.InspectionRecord> {
        return InspectionHistoryStore.load(applicationContext)
            .filter {
                it.id >= start &&
                    it.id < end &&
                    !it.inspectionType.equals("TOTAL SESSION", ignoreCase = true) &&
                    it.model == model &&
                    it.line == line
            }
            .sortedBy { it.id }
    }

    private fun buildShiftMessage(
        shiftName: String,
        start: Calendar,
        end: Calendar,
        model: String,
        line: String,
        records: List<InspectionHistoryStore.InspectionRecord>
    ): String {
        val completedSlots = completedSlots(records, start, 12)
        val missingSlots = (0 until 12).filter { it !in completedSlots }
        val compliance = completedSlots.size.toDouble() / 12.0 * 100.0

        return buildString {
            append("📋 Pouch 교대조 품질 Summary")
            append("\n교대 : $shiftName")
            append("\nModel : $model")
            append("\nLine : $line")
            append("\n기간 : ${displayFormat.format(start.time)} ~ ${displayFormat.format(end.time)}")
            append("\n\n총 검사 : ${records.size}건")
            append("\n검사 계획 : 12회 | 실시 : ${completedSlots.size}회")
            append("\n검사 준수율 : ${fmt(compliance)}%")
            append(
                "\n정상 ${countSeverity(records, 1)} | " +
                    "주의 ${countSeverity(records, 2)} | " +
                    "한계정상 ${countSeverity(records, 3)} | " +
                    "불량 ${records.count { severity(it.judgment) >= 4 }}"
            )

            if (missingSlots.isEmpty()) {
                append("\n✅ 누락 시간대 없음")
            } else {
                append("\n⚠ 누락 시간대 : ")
                append(missingSlots.joinToString(", ") { slotLabel(start, it) })
            }

            appendItemSummary(records)

            val abnormal = records.filter { severity(it.judgment) >= 3 }

            if (abnormal.isEmpty()) {
                append("\n\n✅ 한계정상/불량 결과 없음")
            } else {
                append("\n\n📷 한계정상/불량 : ${abnormal.size}건")
                append("\n※ 해당 결과 이미지를 이어서 첨부합니다.")
            }
        }
    }

    private fun buildWeeklyMessage(
        start: Calendar,
        end: Calendar,
        model: String,
        line: String,
        records: List<InspectionHistoryStore.InspectionRecord>,
        fourWeekRecords: List<InspectionHistoryStore.InspectionRecord>
    ): String {
        val expectedSlots = 7 * 24
        val completed = completedSlots(records, start, expectedSlots)
        val compliance = completed.size.toDouble() / expectedSlots.toDouble() * 100.0

        return buildString {
            append("📊 Pouch Weekly Quality Report")
            append("\nModel : $model")
            append("\nLine : $line")
            append("\n기간 : ${displayFormat.format(start.time)} ~ ${displayFormat.format(end.time)}")
            append("\n\n총 검사 : ${records.size}건")
            append("\n검사 준수율 : ${fmt(compliance)}%")
            append(
                "\n정상 ${countSeverity(records, 1)} | " +
                    "주의 ${countSeverity(records, 2)} | " +
                    "한계정상 ${countSeverity(records, 3)} | " +
                    "불량 ${records.count { severity(it.judgment) >= 4 }}"
            )

            appendItemSummary(records)

            append("\n\n📈 최근 4주 추세")

            for (weekIndex in 4 downTo 1) {
                val weekEnd = (end.clone() as Calendar).apply {
                    add(Calendar.DAY_OF_YEAR, -7 * (weekIndex - 1))
                }
                val weekStart = (weekEnd.clone() as Calendar).apply {
                    add(Calendar.DAY_OF_YEAR, -7)
                }

                val wr = fourWeekRecords.filter {
                    it.id >= weekStart.timeInMillis && it.id < weekEnd.timeInMillis
                }

                val avg =
                    if (wr.isEmpty()) 0.0
                    else wr.map { it.score }.average()

                val issueRate =
                    if (wr.isEmpty()) 0.0
                    else wr.count { severity(it.judgment) >= 2 }.toDouble() /
                        wr.size.toDouble() * 100.0

                append(
                    "\n${shortDateFormat.format(weekStart.time)}~" +
                        "${shortDateFormat.format(weekEnd.time)} : " +
                        "${wr.size}건 | Avg ${fmt(avg)} | 이상률 ${fmt(issueRate)}%"
                )
            }

            val reps = representativeAbnormal(records)
            if (reps.isEmpty()) {
                append("\n\n✅ 이번 주 한계정상/불량 없음")
            } else {
                append("\n\n📷 주간 대표 한계정상/불량")
                reps.forEach {
                    append(
                        "\n- ${normalizeType(it.inspectionType)} : " +
                            "${it.judgment} | Score ${fmt(it.score)}"
                    )
                }
                append("\n※ 이미지는 항목별 최악 결과 최대 1장씩 첨부합니다.")
            }
        }
    }

    private fun StringBuilder.appendItemSummary(
        records: List<InspectionHistoryStore.InspectionRecord>
    ) {
        append("\n\n📌 검사항목별")

        inspectionTypes.forEach { type ->
            val item = records.filter { normalizeType(it.inspectionType) == type }

            if (item.isEmpty()) {
                append("\n$type : 0건")
            } else {
                append(
                    "\n$type : ${item.size}건 | " +
                        "Avg ${fmt(item.map { it.score }.average())} | " +
                        "Min ${fmt(item.minOf { it.score })}"
                )
            }
        }
    }

    private fun representativeAbnormal(
        records: List<InspectionHistoryStore.InspectionRecord>
    ): List<InspectionHistoryStore.InspectionRecord> {
        return inspectionTypes.mapNotNull { type ->
            records
                .filter {
                    normalizeType(it.inspectionType) == type &&
                        severity(it.judgment) >= 3
                }
                .sortedWith(
                    compareByDescending<InspectionHistoryStore.InspectionRecord> {
                        severity(it.judgment)
                    }.thenBy { it.score }
                )
                .firstOrNull()
        }
    }

    private fun completedSlots(
        records: List<InspectionHistoryStore.InspectionRecord>,
        start: Calendar,
        slotCount: Int
    ): Set<Int> {
        val result = mutableSetOf<Int>()

        records.forEach { record ->
            val slot =
                ((record.id - start.timeInMillis) / TimeUnit.HOURS.toMillis(1)).toInt()

            if (slot in 0 until slotCount) {
                result.add(slot)
            }
        }

        return result
    }

    private fun slotLabel(
        start: Calendar,
        slot: Int
    ): String {
        val a = (start.clone() as Calendar).apply {
            add(Calendar.HOUR_OF_DAY, slot)
        }
        val b = (a.clone() as Calendar).apply {
            add(Calendar.HOUR_OF_DAY, 1)
        }

        return String.format(
            Locale.getDefault(),
            "%02d:00~%02d:00",
            a.get(Calendar.HOUR_OF_DAY),
            b.get(Calendar.HOUR_OF_DAY)
        )
    }

    private fun countSeverity(
        records: List<InspectionHistoryStore.InspectionRecord>,
        target: Int
    ): Int {
        return records.count { severity(it.judgment) == target }
    }

    private fun severity(judgment: String): Int {
        val value = judgment.trim()

        return when {
            value.contains("불량") -> 4
            value.contains("한계") -> 3
            value.contains("주의") -> 2
            else -> 1
        }
    }

    private fun normalizeType(value: String): String {
        val normalized =
            value.trim().uppercase(Locale.US).replace("_", " ")

        return when (normalized) {
            "분해검사" -> "DISASSEMBLY"
            else -> normalized
        }
    }

    private fun fmt(value: Double): String {
        return String.format(Locale.getDefault(), "%.1f", value)
    }

    private fun boundaryToday(
        now: Calendar,
        hour: Int
    ): Calendar {
        return (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    private fun wasSent(key: String): Boolean {
        return applicationContext
            .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getBoolean(key, false)
    }

    private fun markSent(key: String) {
        applicationContext
            .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(key, true)
            .apply()
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "pouch_shift_weekly_report"
        private const val PREF_NAME = "shift_weekly_report_sent"

        private const val INPUT_MANUAL_TEST =
            "manual_report_test"

        private const val TEST_SHIFT =
            "shift"

        private const val TEST_WEEKLY =
            "weekly"

        private val inspectionTypes =
            listOf(
                "BOTTOM CORNER",
                "SEAL",
                "FORMING",
                "TAB",
                "DISASSEMBLY"
            )

        private val displayFormat =
            SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())

        private val shortDateFormat =
            SimpleDateFormat("MM/dd", Locale.getDefault())

        private val keyFormat =
            SimpleDateFormat("yyyyMMddHH", Locale.US)

        fun sendShiftTestNow(
            context: Context
        ) {

            val constraints =
                Constraints.Builder()
                    .setRequiredNetworkType(
                        NetworkType.CONNECTED
                    )
                    .build()

            val request =
                androidx.work.OneTimeWorkRequestBuilder<ShiftWeeklyReportWorker>()
                    .setInputData(
                        androidx.work.workDataOf(
                            INPUT_MANUAL_TEST to
                                TEST_SHIFT
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

        fun sendWeeklyTestNow(
            context: Context
        ) {

            val constraints =
                Constraints.Builder()
                    .setRequiredNetworkType(
                        NetworkType.CONNECTED
                    )
                    .build()

            val request =
                androidx.work.OneTimeWorkRequestBuilder<ShiftWeeklyReportWorker>()
                    .setInputData(
                        androidx.work.workDataOf(
                            INPUT_MANUAL_TEST to
                                TEST_WEEKLY
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

        fun applySchedule(context: Context) {
            val constraints =
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

            val request =
                PeriodicWorkRequestBuilder<ShiftWeeklyReportWorker>(
                    1,
                    TimeUnit.HOURS
                )
                    .setConstraints(constraints)
                    .build()

            WorkManager.getInstance(context.applicationContext)
                .enqueueUniquePeriodicWork(
                    UNIQUE_WORK_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    request
                )
        }
    }
}
