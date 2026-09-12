package com.pouchvision.inspector

import android.content.Context
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/*
 * Telegram 설정 저장소
 *
 * - Bot Token을 Kotlin 소스에 직접 넣지 않습니다.
 * - 사용자가 앱에서 입력한 Token은 Android Keystore AES/GCM으로
 *   암호화하여 앱 내부에 저장합니다.
 * - 개인 Chat ID 여러 개 또는 단체방 Chat ID를 저장할 수 있습니다.
 * - 어떤 판정부터 Telegram 알림을 보낼지 정책을 저장합니다.
 *
 * 이 파일만 추가해도 아직 Telegram 전송은 시작되지 않습니다.
 * 다음 단계에서 설정 화면과 실제 전송 기능을 연결합니다.
 */
object TelegramSettingsStore {

    private const val PREF_NAME = "telegram_settings_pref"
    private const val KEY_ENABLED = "telegram_enabled"
    private const val KEY_BOT_TOKEN_ENCRYPTED = "telegram_bot_token_encrypted"
    private const val KEY_BOT_TOKEN_IV = "telegram_bot_token_iv"
    private const val KEY_CHAT_IDS = "telegram_chat_ids"
    private const val KEY_ALERT_POLICY = "telegram_alert_policy"
    private const val KEY_SEND_IMAGE = "telegram_send_image"

    // Dashboard 정기 Summary
    private const val KEY_DASHBOARD_SUMMARY_ENABLED = "dashboard_summary_enabled"
    private const val KEY_DASHBOARD_SUMMARY_INTERVAL_HOURS = "dashboard_summary_interval_hours"
    private const val KEY_DASHBOARD_ZERO_WARNING = "dashboard_zero_warning"
    private const val KEY_DASHBOARD_MISSING_ITEM_WARNING = "dashboard_missing_item_warning"
    private const val KEY_DASHBOARD_LOW_COUNT_WARNING = "dashboard_low_count_warning"
    private const val KEY_DASHBOARD_MIN_COUNT = "dashboard_min_count"

    private const val KEYSTORE_NAME = "AndroidKeyStore"
    private const val KEY_ALIAS = "PouchVisionTelegramTokenKey"

    enum class AlertPolicy(
        val displayName: String
    ) {
        NG_ONLY("불량만 전송"),
        LIMIT_AND_NG("한계정상 + 불량"),
        WARNING_AND_ABOVE("주의 이상 전체")
    }

    data class TelegramSettings(
        val enabled: Boolean,
        val botToken: String,
        val chatIds: List<String>,
        val alertPolicy: AlertPolicy,
        val sendImage: Boolean,
        val dashboardSummaryEnabled: Boolean,
        val dashboardSummaryIntervalHours: Int,
        val dashboardZeroWarning: Boolean,
        val dashboardMissingItemWarning: Boolean,
        val dashboardLowCountWarning: Boolean,
        val dashboardMinCount: Int
    )

    fun load(
        context: Context
    ): TelegramSettings {

        val prefs =
            context.getSharedPreferences(
                PREF_NAME,
                Context.MODE_PRIVATE
            )

        val enabled =
            prefs.getBoolean(
                KEY_ENABLED,
                false
            )

        val token =
            readBotToken(
                context
            )

        val chatIds =
            prefs.getStringSet(
                KEY_CHAT_IDS,
                emptySet()
            )
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?.distinct()
                ?.sorted()
                ?: emptyList()

        val policyName =
            prefs.getString(
                KEY_ALERT_POLICY,
                AlertPolicy.NG_ONLY.name
            )
                ?: AlertPolicy.NG_ONLY.name

        val policy =
            try {
                AlertPolicy.valueOf(
                    policyName
                )
            } catch (
                e: Exception
            ) {
                AlertPolicy.NG_ONLY
            }

        val sendImage =
            prefs.getBoolean(
                KEY_SEND_IMAGE,
                true
            )

        val dashboardSummaryEnabled =
            prefs.getBoolean(KEY_DASHBOARD_SUMMARY_ENABLED, false)

        val dashboardSummaryIntervalHours =
            prefs.getInt(KEY_DASHBOARD_SUMMARY_INTERVAL_HOURS, 6)
                .takeIf { it == 6 || it == 12 || it == 24 }
                ?: 6

        val dashboardZeroWarning =
            prefs.getBoolean(KEY_DASHBOARD_ZERO_WARNING, true)

        val dashboardMissingItemWarning =
            prefs.getBoolean(KEY_DASHBOARD_MISSING_ITEM_WARNING, true)

        val dashboardLowCountWarning =
            prefs.getBoolean(KEY_DASHBOARD_LOW_COUNT_WARNING, true)

        val dashboardMinCount =
            prefs.getInt(KEY_DASHBOARD_MIN_COUNT, 4)
                .coerceAtLeast(1)

        return TelegramSettings(
            enabled = enabled,
            botToken = token,
            chatIds = chatIds,
            alertPolicy = policy,
            sendImage = sendImage,
            dashboardSummaryEnabled = dashboardSummaryEnabled,
            dashboardSummaryIntervalHours = dashboardSummaryIntervalHours,
            dashboardZeroWarning = dashboardZeroWarning,
            dashboardMissingItemWarning = dashboardMissingItemWarning,
            dashboardLowCountWarning = dashboardLowCountWarning,
            dashboardMinCount = dashboardMinCount
        )
    }

    fun setEnabled(
        context: Context,
        enabled: Boolean
    ) {

        context.getSharedPreferences(
            PREF_NAME,
            Context.MODE_PRIVATE
        )
            .edit()
            .putBoolean(
                KEY_ENABLED,
                enabled
            )
            .apply()
    }

    fun saveBotToken(
        context: Context,
        botToken: String
    ): Boolean {

        val safeToken =
            botToken.trim()

        if (
            safeToken.isBlank()
        ) {

            clearBotToken(
                context
            )

            return true
        }

        return try {

            val secretKey =
                getOrCreateSecretKey()

            val cipher =
                Cipher.getInstance(
                    "AES/GCM/NoPadding"
                )

            cipher.init(
                Cipher.ENCRYPT_MODE,
                secretKey
            )

            val encrypted =
                cipher.doFinal(
                    safeToken.toByteArray(
                        StandardCharsets.UTF_8
                    )
                )

            val iv =
                cipher.iv

            context.getSharedPreferences(
                PREF_NAME,
                Context.MODE_PRIVATE
            )
                .edit()
                .putString(
                    KEY_BOT_TOKEN_ENCRYPTED,
                    Base64.encodeToString(
                        encrypted,
                        Base64.NO_WRAP
                    )
                )
                .putString(
                    KEY_BOT_TOKEN_IV,
                    Base64.encodeToString(
                        iv,
                        Base64.NO_WRAP
                    )
                )
                .commit()

        } catch (
            e: Exception
        ) {
            false
        }
    }

    fun readBotToken(
        context: Context
    ): String {

        return try {

            val prefs =
                context.getSharedPreferences(
                    PREF_NAME,
                    Context.MODE_PRIVATE
                )

            val encryptedText =
                prefs.getString(
                    KEY_BOT_TOKEN_ENCRYPTED,
                    ""
                )
                    .orEmpty()

            val ivText =
                prefs.getString(
                    KEY_BOT_TOKEN_IV,
                    ""
                )
                    .orEmpty()

            if (
                encryptedText.isBlank() ||
                ivText.isBlank()
            ) {
                return ""
            }

            val encrypted =
                Base64.decode(
                    encryptedText,
                    Base64.NO_WRAP
                )

            val iv =
                Base64.decode(
                    ivText,
                    Base64.NO_WRAP
                )

            val secretKey =
                getOrCreateSecretKey()

            val cipher =
                Cipher.getInstance(
                    "AES/GCM/NoPadding"
                )

            cipher.init(
                Cipher.DECRYPT_MODE,
                secretKey,
                GCMParameterSpec(
                    128,
                    iv
                )
            )

            val plain =
                cipher.doFinal(
                    encrypted
                )

            String(
                plain,
                StandardCharsets.UTF_8
            )

        } catch (
            e: Exception
        ) {
            ""
        }
    }

    fun clearBotToken(
        context: Context
    ) {

        context.getSharedPreferences(
            PREF_NAME,
            Context.MODE_PRIVATE
        )
            .edit()
            .remove(
                KEY_BOT_TOKEN_ENCRYPTED
            )
            .remove(
                KEY_BOT_TOKEN_IV
            )
            .apply()
    }

    fun getChatIds(
        context: Context
    ): List<String> {

        return load(
            context
        ).chatIds
    }

    fun addChatId(
        context: Context,
        chatId: String
    ) {

        val safeChatId =
            chatId.trim()

        if (
            safeChatId.isBlank()
        ) {
            return
        }

        val ids =
            getChatIds(
                context
            )
                .toMutableSet()

        ids.add(
            safeChatId
        )

        context.getSharedPreferences(
            PREF_NAME,
            Context.MODE_PRIVATE
        )
            .edit()
            .putStringSet(
                KEY_CHAT_IDS,
                ids
            )
            .apply()
    }

    fun removeChatId(
        context: Context,
        chatId: String
    ) {

        val ids =
            getChatIds(
                context
            )
                .toMutableSet()

        ids.remove(
            chatId.trim()
        )

        context.getSharedPreferences(
            PREF_NAME,
            Context.MODE_PRIVATE
        )
            .edit()
            .putStringSet(
                KEY_CHAT_IDS,
                ids
            )
            .apply()
    }

    fun replaceChatIds(
        context: Context,
        chatIds: List<String>
    ) {

        val cleanIds =
            chatIds
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .toSet()

        context.getSharedPreferences(
            PREF_NAME,
            Context.MODE_PRIVATE
        )
            .edit()
            .putStringSet(
                KEY_CHAT_IDS,
                cleanIds
            )
            .apply()
    }

    fun setAlertPolicy(
        context: Context,
        policy: AlertPolicy
    ) {

        context.getSharedPreferences(
            PREF_NAME,
            Context.MODE_PRIVATE
        )
            .edit()
            .putString(
                KEY_ALERT_POLICY,
                policy.name
            )
            .apply()
    }

    fun setSendImage(
        context: Context,
        sendImage: Boolean
    ) {

        context.getSharedPreferences(
            PREF_NAME,
            Context.MODE_PRIVATE
        )
            .edit()
            .putBoolean(
                KEY_SEND_IMAGE,
                sendImage
            )
            .apply()
    }

    fun setDashboardSummarySettings(
        context: Context,
        enabled: Boolean,
        intervalHours: Int,
        zeroWarning: Boolean,
        missingItemWarning: Boolean,
        lowCountWarning: Boolean,
        minCount: Int
    ) {
        context.getSharedPreferences(
            PREF_NAME,
            Context.MODE_PRIVATE
        )
            .edit()
            .putBoolean(KEY_DASHBOARD_SUMMARY_ENABLED, enabled)
            .putInt(
                KEY_DASHBOARD_SUMMARY_INTERVAL_HOURS,
                if (intervalHours == 12 || intervalHours == 24) intervalHours else 6
            )
            .putBoolean(KEY_DASHBOARD_ZERO_WARNING, zeroWarning)
            .putBoolean(KEY_DASHBOARD_MISSING_ITEM_WARNING, missingItemWarning)
            .putBoolean(KEY_DASHBOARD_LOW_COUNT_WARNING, lowCountWarning)
            .putInt(KEY_DASHBOARD_MIN_COUNT, minCount.coerceAtLeast(1))
            .apply()
    }

    fun isReady(
        context: Context
    ): Boolean {

        val settings =
            load(
                context
            )

        return settings.enabled &&
            settings.botToken.isNotBlank() &&
            settings.chatIds.isNotEmpty()
    }

    fun shouldSendForJudgment(
        context: Context,
        judgment: String
    ): Boolean {

        val policy =
            load(
                context
            ).alertPolicy

        val severity =
            judgmentSeverity(
                judgment
            )

        return when (
            policy
        ) {

            AlertPolicy.NG_ONLY -> {
                severity >= 4
            }

            AlertPolicy.LIMIT_AND_NG -> {
                severity >= 3
            }

            AlertPolicy.WARNING_AND_ABOVE -> {
                severity >= 2
            }
        }
    }

    private fun judgmentSeverity(
        judgment: String
    ): Int {

        return when {

            judgment.contains(
                "불량"
            ) -> 4

            judgment.contains(
                "한계"
            ) -> 3

            judgment.contains(
                "주의"
            ) -> 2

            judgment.contains(
                "정상"
            ) -> 1

            else -> 0
        }
    }

    private fun getOrCreateSecretKey():
        SecretKey {

        val keyStore =
            KeyStore.getInstance(
                KEYSTORE_NAME
            )

        keyStore.load(
            null
        )

        val existingKey =
            keyStore.getKey(
                KEY_ALIAS,
                null
            )

        if (
            existingKey is SecretKey
        ) {
            return existingKey
        }

        val keyGenerator =
            KeyGenerator.getInstance(
                android.security.keystore.KeyProperties.KEY_ALGORITHM_AES,
                KEYSTORE_NAME
            )

        val parameterSpec =
            android.security.keystore.KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or
                    android.security.keystore.KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(
                    android.security.keystore.KeyProperties.BLOCK_MODE_GCM
                )
                .setEncryptionPaddings(
                    android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE
                )
                .setRandomizedEncryptionRequired(
                    true
                )
                .build()

        keyGenerator.init(
            parameterSpec
        )

        return keyGenerator.generateKey()
    }
}
