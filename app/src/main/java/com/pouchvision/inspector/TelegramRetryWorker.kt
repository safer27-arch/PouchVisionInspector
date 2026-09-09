package com.pouchvision.inspector

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/*
 * =============================================================
 * Telegram 네트워크 장애 자동 재전송 Worker
 * =============================================================
 *
 * 동작 원칙
 * 1) 네트워크가 연결된 상태에서만 실행
 * 2) TelegramDeliveryStore의 FAILED 기록만 확인
 * 3) 네트워크/일시적 서버 오류만 자동 재전송
 * 4) 잘못된 Chat ID / Token 등 설정 오류는 자동 재전송하지 않음
 * 5) 한 기록당 자동/수동 포함 최대 retryCount 3회까지만 재시도
 *
 * 중요
 * - 이 파일을 추가하는 것만으로 아직 자동 예약은 시작되지 않습니다.
 * - 다음 단계에서 TelegramSender.kt가 전송 실패 시
 *   schedule()을 호출하도록 연결합니다.
 * =============================================================
 */

class TelegramRetryWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : Worker(
    appContext,
    workerParams
) {

    override fun doWork(): Result {

        val failedRecords =
            TelegramDeliveryStore.load(
                applicationContext
            )
                .filter {
                    it.status ==
                        TelegramDeliveryStore.STATUS_FAILED
                }
                .filter {
                    it.retryCount <
                        MAX_RETRY_COUNT
                }
                .filter {
                    isRetryableFailure(
                        it.message
                    )
                }

        /*
         * 자동 재전송 대상이 없으면 정상 종료합니다.
         */
        if (
            failedRecords.isEmpty()
        ) {
            return Result.success()
        }

        var retryableFailureStillExists =
            false

        failedRecords.forEach { record ->

            val latch =
                CountDownLatch(
                    1
                )

            var resultReceived =
                false

            TelegramSender.retryDelivery(
                context = applicationContext,
                record = record
            ) { result ->

                resultReceived =
                    true

                /*
                 * 재전송 후에도 실패했으며,
                 * 여전히 네트워크성/일시적 오류이면
                 * WorkManager의 다음 재시도를 허용합니다.
                 */
                if (
                    !result.success &&
                    isRetryableFailure(
                        result.message
                    )
                ) {

                    val updated =
                        TelegramDeliveryStore.find(
                            context = applicationContext,
                            recordId = record.id
                        )

                    if (
                        updated != null &&
                        updated.retryCount <
                        MAX_RETRY_COUNT
                    ) {

                        retryableFailureStillExists =
                            true
                    }
                }

                latch.countDown()
            }

            /*
             * TelegramSender는 별도 Thread에서 동작하므로
             * Worker는 결과를 기다립니다.
             *
             * 너무 오래 멈추지 않도록 최대 45초만 대기합니다.
             */
            val completed =
                try {

                    latch.await(
                        SEND_WAIT_TIMEOUT_SECONDS,
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
                !completed ||
                !resultReceived
            ) {

                val updated =
                    TelegramDeliveryStore.find(
                        context = applicationContext,
                        recordId = record.id
                    )

                if (
                    updated != null &&
                    updated.retryCount <
                    MAX_RETRY_COUNT
                ) {

                    retryableFailureStillExists =
                        true
                }
            }
        }

        /*
         * 재시도 가능한 실패가 남아 있으면 WorkManager가
         * 지수형 Backoff 후 다시 실행합니다.
         *
         * 각 기록의 retryCount가 3회에 도달하면
         * 이후 자동 재전송은 중단됩니다.
         */
        return if (
            retryableFailureStillExists
        ) {

            Result.retry()

        } else {

            Result.success()
        }
    }

    companion object {

        private const val UNIQUE_WORK_NAME =
            "pouch_telegram_auto_retry"

        private const val MAX_RETRY_COUNT =
            3

        private const val SEND_WAIT_TIMEOUT_SECONDS =
            45L

        /*
         * =====================================================
         * 자동 재전송 예약
         * =====================================================
         *
         * 같은 작업이 이미 대기/실행 중이면 중복 생성하지 않습니다.
         * 네트워크가 연결되는 순간 WorkManager가 실행할 수 있습니다.
         * =====================================================
         */

        fun schedule(
            context: Context
        ) {

            val constraints =
                Constraints.Builder()
                    .setRequiredNetworkType(
                        NetworkType.CONNECTED
                    )
                    .build()

            val request =
                OneTimeWorkRequest.Builder(
                    TelegramRetryWorker::class.java
                )
                    .setConstraints(
                        constraints
                    )
                    .setBackoffCriteria(
                        BackoffPolicy.EXPONENTIAL,
                        10,
                        TimeUnit.SECONDS
                    )
                    .build()

            WorkManager.getInstance(
                context.applicationContext
            )
                .enqueueUniqueWork(
                    UNIQUE_WORK_NAME,
                    ExistingWorkPolicy.KEEP,
                    request
                )
        }

        /*
         * =====================================================
         * 자동 재전송 허용 오류 판별
         * =====================================================
         */

        fun isRetryableFailure(
            message: String
        ): Boolean {

            val value =
                message
                    .lowercase(
                        Locale.US
                    )

            /*
             * 설정/권한/주소 오류:
             * 반복해도 성공할 가능성이 없으므로 자동 재전송 금지
             */
            val permanentErrors =
                listOf(
                    "http 400",
                    "http 401",
                    "http 403",
                    "http 404",
                    "chat not found",
                    "unauthorized",
                    "forbidden",
                    "bot was blocked",
                    "bot token",
                    "chat id"
                )

            if (
                permanentErrors.any {
                    value.contains(
                        it
                    )
                }
            ) {
                return false
            }

            /*
             * 네트워크 단절 / Timeout / Telegram 일시 장애:
             * 자동 재전송 허용
             */
            val temporaryErrors =
                listOf(
                    "http 408",
                    "http 409",
                    "http 425",
                    "http 429",
                    "http 500",
                    "http 502",
                    "http 503",
                    "http 504",
                    "timeout",
                    "timed out",
                    "unable to resolve host",
                    "unknownhost",
                    "network",
                    "connection",
                    "socket",
                    "reset",
                    "refused",
                    "unreachable",
                    "temporarily unavailable"
                )

            return temporaryErrors.any {
                value.contains(
                    it
                )
            }
        }
    }
}
