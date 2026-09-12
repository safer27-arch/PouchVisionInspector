package com.pouchvision.inspector

import android.content.Context
import android.graphics.Bitmap
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/*
 * =============================================================
 * Telegram 전송 공용 Helper
 * =============================================================
 *
 * 기능
 * 1) 저장된 Bot Token / Chat ID 사용
 * 2) 개인 여러 명 + 단체방 동시 전송
 * 3) 텍스트 테스트 메시지 전송
 * 4) 검사 결과 이미지 + Caption 전송
 * 5) UI가 멈추지 않도록 Background Thread에서 통신
 *
 * 중요
 * - Bot Token은 이 파일에 직접 넣지 않습니다.
 * - TelegramSettingsStore에 저장된 암호화 Token을 사용합니다.
 * - 실제 NG 자동전송 연결은 다음 단계에서 검사 저장 로직에 붙입니다.
 * =============================================================
 */

object TelegramSender {

    data class SendResult(
        val success: Boolean,
        val successCount: Int,
        val failureCount: Int,
        val message: String
    )

    /*
     * =========================================================
     * 설정 화면용 테스트 메시지
     * =========================================================
     */

    fun sendTestMessage(
        context: Context,
        callback: (SendResult) -> Unit
    ) {

        val settings =
            TelegramSettingsStore.load(
                context
            )

        if (
            settings.botToken.isBlank()
        ) {

            callback(
                SendResult(
                    success = false,
                    successCount = 0,
                    failureCount = 0,
                    message = "Bot Token이 등록되지 않았습니다."
                )
            )

            return
        }

        if (
            settings.chatIds.isEmpty()
        ) {

            callback(
                SendResult(
                    success = false,
                    successCount = 0,
                    failureCount = 0,
                    message = "Chat ID가 등록되지 않았습니다."
                )
            )

            return
        }

        val production =
            ProductionContextStore.getCurrent(
                context
            )

        val message =
            """
✅ Pouch Vision Inspector Telegram 테스트

Model : ${production.model}
Line : ${production.line}

Telegram 연결 테스트 메시지입니다.
정상적으로 수신되면 Bot Token / Chat ID 연결이 완료된 상태입니다.
            """.trimIndent()

        Thread {

            val result =
                sendTextToAll(
                    botToken = settings.botToken,
                    chatIds = settings.chatIds,
                    text = message
                )

            callback(
                result
            )

        }.start()
    }

    /*
     * =========================================================
     * 검사 결과 Telegram 전송
     * =========================================================
     *
     * 실제 검사 화면과 연결할 때 사용합니다.
     *
     * judgment에 따라 설정된 알림 기준을 자동 적용합니다.
     * =========================================================
     */

    fun sendDashboardSummary(
        context: Context,
        message: String,
        callback: ((SendResult) -> Unit)? = null,
        allowWhenSummaryOff: Boolean = false
    ) {
        val settings = TelegramSettingsStore.load(context)

        if (
            !settings.enabled ||
            (!settings.dashboardSummaryEnabled && !allowWhenSummaryOff)
        ) {
            callback?.invoke(
                SendResult(
                    success = false,
                    successCount = 0,
                    failureCount = 0,
                    message = "Dashboard 정기 Summary가 OFF 상태입니다."
                )
            )
            return
        }

        if (settings.botToken.isBlank() || settings.chatIds.isEmpty()) {
            callback?.invoke(
                SendResult(
                    success = false,
                    successCount = 0,
                    failureCount = 0,
                    message = "Telegram Token 또는 Chat ID 설정이 필요합니다."
                )
            )
            return
        }

        Thread {
            val result =
                sendTextToAll(
                    botToken = settings.botToken,
                    chatIds = settings.chatIds,
                    text = message.take(3900)
                )

            callback?.invoke(result)
        }.start()
    }

    fun sendInspectionAlert(
        context: Context,
        inspectionType: String,
        score: Double,
        judgment: String,
        details: String,
        resultBitmap: Bitmap?,
        callback: ((SendResult) -> Unit)? = null
    ) {

        val settings =
            TelegramSettingsStore.load(
                context
            )

        if (
            !settings.enabled
        ) {

            callback?.invoke(
                SendResult(
                    success = false,
                    successCount = 0,
                    failureCount = 0,
                    message = "Telegram 자동 알림이 OFF 상태입니다."
                )
            )

            return
        }

        if (
            settings.botToken.isBlank() ||
            settings.chatIds.isEmpty()
        ) {

            callback?.invoke(
                SendResult(
                    success = false,
                    successCount = 0,
                    failureCount = 0,
                    message = "Telegram Token 또는 Chat ID 설정이 필요합니다."
                )
            )

            return
        }

        if (
            !TelegramSettingsStore
                .shouldSendForJudgment(
                    context,
                    judgment
                )
        ) {

            callback?.invoke(
                SendResult(
                    success = true,
                    successCount = 0,
                    failureCount = 0,
                    message = "현재 Telegram 전송 기준에 해당하지 않는 판정입니다."
                )
            )

            return
        }

        val production =
            ProductionContextStore.getCurrent(
                context
            )

        val message =
            buildInspectionMessage(
                model = production.model,
                line = production.line,
                inspectionType = inspectionType,
                score = score,
                judgment = judgment,
                details = details
            )

        Thread {

            var tempImageFile:
                File? =
                null

            var deliveryRecordId =
                0L

            try {

                /*
                 * =================================================
                 * Telegram 전송 이력 PENDING 생성
                 * =================================================
                 *
                 * 사용자가 "결과 이미지 전송"을 켠 경우에만
                 * 실패 재전송용 이미지를 앱 내부에 임시 보관합니다.
                 */
                deliveryRecordId =
                    TelegramDeliveryStore.createPending(
                        context = context,
                        model = production.model,
                        line = production.line,
                        inspectionType = inspectionType,
                        score = score,
                        judgment = judgment,
                        resultBitmap =
                            if (
                                settings.sendImage
                            ) {
                                resultBitmap
                            } else {
                                null
                            }
                    )

                val result =
                    if (
                        settings.sendImage &&
                        resultBitmap != null
                    ) {

                        tempImageFile =
                            createTemporaryImage(
                                context = context,
                                bitmap = resultBitmap
                            )

                        if (
                            tempImageFile != null
                        ) {

                            sendPhotoToAll(
                                botToken = settings.botToken,
                                chatIds = settings.chatIds,
                                photoFile = tempImageFile!!,
                                caption = message
                            )

                        } else {

                            /*
                             * 이미지 임시 생성에 실패한 경우
                             * 알림 자체를 놓치지 않도록 Text로 전송합니다.
                             */
                            sendTextToAll(
                                botToken = settings.botToken,
                                chatIds = settings.chatIds,
                                text = message
                            )
                        }

                    } else {

                        sendTextToAll(
                            botToken = settings.botToken,
                            chatIds = settings.chatIds,
                            text = message
                        )
                    }

                /*
                 * =================================================
                 * Telegram 성공 / 실패 이력 갱신
                 * =================================================
                 *
                 * 여러 수신처 중 하나라도 실패하면
                 * STATUS_FAILED로 남겨 재전송 대상으로 관리합니다.
                 */
                if (
                    deliveryRecordId !=
                    0L
                ) {

                    TelegramDeliveryStore.updateResult(
                        context = context,
                        recordId = deliveryRecordId,
                        success = result.success,
                        successCount = result.successCount,
                        failureCount = result.failureCount,
                        message = result.message
                    )

                    /*
                     * 네트워크 단절 / Timeout / Telegram 일시 장애인 경우에만
                     * 네트워크 복구 후 자동 재전송을 예약합니다.
                     *
                     * HTTP 400, 잘못된 Chat ID/Token 등 설정 오류는
                     * TelegramRetryWorker에서 재시도 대상으로 보지 않습니다.
                     */
                    if (
                        !result.success &&
                        TelegramRetryWorker.isRetryableFailure(
                            result.message
                        )
                    ) {

                        TelegramRetryWorker.schedule(
                            context
                        )
                    }
                }

                callback?.invoke(
                    result
                )

            } catch (
                e: Exception
            ) {

                val errorResult =
                    SendResult(
                        success = false,
                        successCount = 0,
                        failureCount = settings.chatIds.size,
                        message =
                            "Telegram 전송 오류: " +
                                (
                                    e.message
                                        ?: "알 수 없는 오류"
                                    )
                    )

                if (
                    deliveryRecordId !=
                    0L
                ) {

                    TelegramDeliveryStore.updateResult(
                        context = context,
                        recordId = deliveryRecordId,
                        success = false,
                        successCount = errorResult.successCount,
                        failureCount = errorResult.failureCount,
                        message = errorResult.message
                    )

                    if (
                        TelegramRetryWorker.isRetryableFailure(
                            errorResult.message
                        )
                    ) {

                        TelegramRetryWorker.schedule(
                            context
                        )
                    }
                }

                callback?.invoke(
                    errorResult
                )

            } finally {

                try {

                    tempImageFile
                        ?.takeIf {
                            it.exists()
                        }
                        ?.delete()

                } catch (
                    e: Exception
                ) {
                    // 임시 파일 삭제 실패는 무시합니다.
                }
            }

        }.start()
    }

    /*
     * =========================================================
     * 실패한 Telegram 이력 재전송
     * =========================================================
     *
     * - 원래 FAILED 기록을 그대로 사용합니다.
     * - 새 이력 레코드를 추가하지 않습니다.
     * - 재전송 성공 시 기존 FAILED → SUCCESS로 변경됩니다.
     * - 실패 시 FAILED 상태를 유지합니다.
     * - 현재 앱에 저장된 Bot Token / Chat ID를 사용합니다.
     * =========================================================
     */

    fun retryDelivery(
        context: Context,
        record: TelegramDeliveryStore.DeliveryRecord,
        callback: (SendResult) -> Unit
    ) {

        val settings =
            TelegramSettingsStore.load(
                context
            )

        if (
            settings.botToken.isBlank()
        ) {

            callback(
                SendResult(
                    success = false,
                    successCount = 0,
                    failureCount = 0,
                    message = "Bot Token이 등록되지 않았습니다."
                )
            )

            return
        }

        if (
            settings.chatIds.isEmpty()
        ) {

            callback(
                SendResult(
                    success = false,
                    successCount = 0,
                    failureCount = 0,
                    message = "Chat ID가 등록되지 않았습니다."
                )
            )

            return
        }

        /*
         * 수동 재전송은 자동 알림 ON/OFF와 무관하게 허용합니다.
         * 사용자가 이력 화면에서 직접 눌렀기 때문입니다.
         */
        TelegramDeliveryStore.markRetryStarted(
            context = context,
            recordId = record.id
        )

        val message =
            buildString {

                append(
                    "🔁 Pouch 품질 알림 재전송"
                )

                append(
                    "\n\nModel : "
                )

                append(
                    record.model
                )

                append(
                    "\nLine : "
                )

                append(
                    record.line
                )

                append(
                    "\n검사항목 : "
                )

                append(
                    record.inspectionType
                )

                append(
                    "\n판정 : "
                )

                append(
                    record.judgment
                )

                append(
                    "\nQuality Score : "
                )

                append(
                    String.format(
                        "%.1f",
                        record.score
                    )
                )

                append(
                    "\n원 전송시간 : "
                )

                append(
                    record.dateTime
                )
            }
                .take(
                    950
                )

        Thread {

            try {

                val retryImage =
                    TelegramDeliveryStore.getRetryImageFile(
                        record
                    )

                val result =
                    if (
                        retryImage != null
                    ) {

                        sendPhotoToAll(
                            botToken = settings.botToken,
                            chatIds = settings.chatIds,
                            photoFile = retryImage,
                            caption = message
                        )

                    } else {

                        sendTextToAll(
                            botToken = settings.botToken,
                            chatIds = settings.chatIds,
                            text = message
                        )
                    }

                TelegramDeliveryStore.updateResult(
                    context = context,
                    recordId = record.id,
                    success = result.success,
                    successCount = result.successCount,
                    failureCount = result.failureCount,
                    message = result.message
                )

                /* 자동 재시도 예약은 TelegramRetryWorker 한 곳에서만 관리합니다. */
callback(
                    result
                )

            } catch (
                e: Exception
            ) {

                val result =
                    SendResult(
                        success = false,
                        successCount = 0,
                        failureCount = settings.chatIds.size,
                        message =
                            "Telegram 재전송 오류: " +
                                (
                                    e.message
                                        ?: "알 수 없는 오류"
                                    )
                    )

                TelegramDeliveryStore.updateResult(
                    context = context,
                    recordId = record.id,
                    success = false,
                    successCount = result.successCount,
                    failureCount = result.failureCount,
                    message = result.message
                )

                /* 재전송 실패 시에도 여기서는 Worker를 새로 예약하지 않습니다. */
callback(
                    result
                )
            }

        }.start()
    }

    /*
     * =========================================================
     * 검사 메시지 구성
     * =========================================================
     */

    private fun buildInspectionMessage(
        model: String,
        line: String,
        inspectionType: String,
        score: Double,
        judgment: String,
        details: String
    ): String {

        val compactDetails =
            details
                .lineSequence()
                .map {
                    it.trim()
                }
                .filter {
                    it.isNotBlank()
                }
                .take(
                    8
                )
                .joinToString(
                    separator = "\n"
                )

        return buildString {

            append(
                "🚨 Pouch 품질 알림"
            )

            append(
                "\n\nModel : "
            )

            append(
                model
            )

            append(
                "\nLine : "
            )

            append(
                line
            )

            append(
                "\n검사항목 : "
            )

            append(
                inspectionType
            )

            append(
                "\n판정 : "
            )

            append(
                judgment
            )

            append(
                "\nQuality Score : "
            )

            append(
                String.format(
                    "%.1f",
                    score
                )
            )

            if (
                compactDetails.isNotBlank()
            ) {

                append(
                    "\n\n"
                )

                append(
                    compactDetails
                )
            }
        }
            /*
             * sendPhoto Caption 길이 제한을 고려해 여유 있게 제한
             */
            .take(
                950
            )
    }

    /*
     * =========================================================
     * 여러 Chat ID로 텍스트 전송
     * =========================================================
     */

    private fun sendTextToAll(
        botToken: String,
        chatIds: List<String>,
        text: String
    ): SendResult {

        var successCount =
            0

        var failureCount =
            0

        val failureMessages =
            mutableListOf<String>()

        chatIds.forEach { chatId ->

            val result =
                sendText(
                    botToken = botToken,
                    chatId = chatId,
                    text = text
                )

            if (
                result.first
            ) {

                successCount++

            } else {

                failureCount++

                failureMessages.add(
                    "$chatId : ${result.second}"
                )
            }
        }

        return SendResult(
            success =
                failureCount ==
                    0 &&
                    successCount >
                    0,

            successCount =
                successCount,

            failureCount =
                failureCount,

            message =
                buildResultMessage(
                    successCount,
                    failureCount,
                    failureMessages
                )
        )
    }

    /*
     * =========================================================
     * 여러 Chat ID로 사진 전송
     * =========================================================
     */

    private fun sendPhotoToAll(
        botToken: String,
        chatIds: List<String>,
        photoFile: File,
        caption: String
    ): SendResult {

        var successCount =
            0

        var failureCount =
            0

        val failureMessages =
            mutableListOf<String>()

        chatIds.forEach { chatId ->

            val result =
                sendPhoto(
                    botToken = botToken,
                    chatId = chatId,
                    photoFile = photoFile,
                    caption = caption
                )

            if (
                result.first
            ) {

                successCount++

            } else {

                failureCount++

                failureMessages.add(
                    "$chatId : ${result.second}"
                )
            }
        }

        return SendResult(
            success =
                failureCount ==
                    0 &&
                    successCount >
                    0,

            successCount =
                successCount,

            failureCount =
                failureCount,

            message =
                buildResultMessage(
                    successCount,
                    failureCount,
                    failureMessages
                )
        )
    }

    /*
     * =========================================================
     * Telegram sendMessage
     * =========================================================
     */

    private fun sendText(
        botToken: String,
        chatId: String,
        text: String
    ): Pair<Boolean, String> {

        var connection:
            HttpURLConnection? =
            null

        return try {

            val url =
                URL(
                    "https://api.telegram.org/bot$botToken/sendMessage"
                )

            connection =
                url.openConnection() as
                    HttpURLConnection

            connection.requestMethod =
                "POST"

            connection.connectTimeout =
                15000

            connection.readTimeout =
                20000

            connection.doOutput =
                true

            connection.setRequestProperty(
                "Content-Type",
                "application/x-www-form-urlencoded; charset=UTF-8"
            )

            val body =
                "chat_id=" +
                    encode(
                        chatId
                    ) +
                    "&text=" +
                    encode(
                        text
                    )

            connection.outputStream.use {

                it.write(
                    body.toByteArray(
                        StandardCharsets.UTF_8
                    )
                )

                it.flush()
            }

            val responseCode =
                connection.responseCode

            if (
                responseCode in
                200..299
            ) {

                Pair(
                    true,
                    "OK"
                )

            } else {

                Pair(
                    false,
                    readErrorBody(
                        connection,
                        responseCode
                    )
                )
            }

        } catch (
            e: Exception
        ) {

            Pair(
                false,
                e.message
                    ?: "네트워크 오류"
            )

        } finally {

            connection
                ?.disconnect()
        }
    }

    /*
     * =========================================================
     * Telegram sendPhoto
     * =========================================================
     */

    private fun sendPhoto(
        botToken: String,
        chatId: String,
        photoFile: File,
        caption: String
    ): Pair<Boolean, String> {

        var connection:
            HttpURLConnection? =
            null

        return try {

            val boundary =
                "----PouchVisionBoundary" +
                    System.currentTimeMillis()

            val url =
                URL(
                    "https://api.telegram.org/bot$botToken/sendPhoto"
                )

            connection =
                url.openConnection() as
                    HttpURLConnection

            connection.requestMethod =
                "POST"

            connection.connectTimeout =
                15000

            connection.readTimeout =
                30000

            connection.doOutput =
                true

            connection.setRequestProperty(
                "Content-Type",
                "multipart/form-data; boundary=$boundary"
            )

            BufferedOutputStream(
                connection.outputStream
            ).use { output ->

                writeTextPart(
                    output = output,
                    boundary = boundary,
                    name = "chat_id",
                    value = chatId
                )

                writeTextPart(
                    output = output,
                    boundary = boundary,
                    name = "caption",
                    value = caption
                )

                val header =
                    "--$boundary\r\n" +
                        "Content-Disposition: form-data; name=\"photo\"; filename=\"pouch_result.jpg\"\r\n" +
                        "Content-Type: image/jpeg\r\n\r\n"

                output.write(
                    header.toByteArray(
                        StandardCharsets.UTF_8
                    )
                )

                photoFile.inputStream().use { input ->

                    val buffer =
                        ByteArray(
                            8192
                        )

                    while (
                        true
                    ) {

                        val count =
                            input.read(
                                buffer
                            )

                        if (
                            count <=
                            0
                        ) {
                            break
                        }

                        output.write(
                            buffer,
                            0,
                            count
                        )
                    }
                }

                output.write(
                    "\r\n".toByteArray(
                        StandardCharsets.UTF_8
                    )
                )

                output.write(
                    "--$boundary--\r\n".toByteArray(
                        StandardCharsets.UTF_8
                    )
                )

                output.flush()
            }

            val responseCode =
                connection.responseCode

            if (
                responseCode in
                200..299
            ) {

                Pair(
                    true,
                    "OK"
                )

            } else {

                Pair(
                    false,
                    readErrorBody(
                        connection,
                        responseCode
                    )
                )
            }

        } catch (
            e: Exception
        ) {

            Pair(
                false,
                e.message
                    ?: "네트워크 오류"
            )

        } finally {

            connection
                ?.disconnect()
        }
    }

    /*
     * =========================================================
     * Multipart Text Part
     * =========================================================
     */

    private fun writeTextPart(
        output: BufferedOutputStream,
        boundary: String,
        name: String,
        value: String
    ) {

        val part =
            "--$boundary\r\n" +
                "Content-Disposition: form-data; name=\"$name\"\r\n\r\n" +
                value +
                "\r\n"

        output.write(
            part.toByteArray(
                StandardCharsets.UTF_8
            )
        )
    }

    /*
     * =========================================================
     * 임시 JPG 생성
     * =========================================================
     */

    private fun createTemporaryImage(
        context: Context,
        bitmap: Bitmap
    ): File? {

        return try {

            val file =
                File(
                    context.cacheDir,
                    "telegram_result_" +
                        System.currentTimeMillis() +
                        ".jpg"
                )

            FileOutputStream(
                file
            ).use { output ->

                val success =
                    bitmap.compress(
                        Bitmap.CompressFormat.JPEG,
                        88,
                        output
                    )

                if (
                    !success
                ) {

                    return null
                }

                output.flush()
            }

            file

        } catch (
            e: Exception
        ) {

            null
        }
    }

    /*
     * =========================================================
     * Response / Utility
     * =========================================================
     */

    private fun readErrorBody(
        connection: HttpURLConnection,
        responseCode: Int
    ): String {

        return try {

            val body =
                connection.errorStream
                    ?.bufferedReader()
                    ?.use {
                        it.readText()
                    }
                    .orEmpty()

            if (
                body.isBlank()
            ) {

                "HTTP $responseCode"

            } else {

                "HTTP $responseCode : " +
                    body.take(
                        250
                    )
            }

        } catch (
            e: Exception
        ) {

            "HTTP $responseCode"
        }
    }

    private fun encode(
        text: String
    ): String {

        return URLEncoder.encode(
            text,
            StandardCharsets.UTF_8.name()
        )
    }

    private fun buildResultMessage(
        successCount: Int,
        failureCount: Int,
        failureMessages: List<String>
    ): String {

        return buildString {

            append(
                "Telegram 전송 결과"
            )

            append(
                "\n성공 : "
            )

            append(
                successCount
            )

            append(
                "개"
            )

            append(
                "\n실패 : "
            )

            append(
                failureCount
            )

            append(
                "개"
            )

            if (
                failureMessages.isNotEmpty()
            ) {

                append(
                    "\n\n"
                )

                append(
                    failureMessages
                        .take(
                            3
                        )
                        .joinToString(
                            separator = "\n"
                        )
                )
            }
        }
    }
}
