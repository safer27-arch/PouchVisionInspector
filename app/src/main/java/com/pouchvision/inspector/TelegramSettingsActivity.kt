package com.pouchvision.inspector

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.pouchvision.inspector.databinding.ActivityTelegramSettingsBinding

class TelegramSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTelegramSettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityTelegramSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupPolicySpinner()
        setupDashboardSpinners()
        applyVisibilityStyle()
        loadCurrentSettings()

        binding.btnSaveTelegramSettings.setOnClickListener {
            saveSettings()
        }

        binding.btnTelegramTest.setOnClickListener {
            sendTestMessage()
        }

        binding.btnTelegramBack.setOnClickListener {
            finish()
        }
    }

    private fun setupPolicySpinner() {
        val policies = TelegramSettingsStore.AlertPolicy.values().toList()
        val labels = policies.map { it.displayName }

        binding.spinnerAlertPolicy.adapter =
            createVisibleSpinnerAdapter(labels)
    }

    private val dashboardIntervals =
        listOf(
            6,
            12,
            24
        )

    private val dashboardMinCounts =
        listOf(
            1,
            2,
            3,
            4,
            5,
            6,
            8,
            10,
            12
        )

    private fun setupDashboardSpinners() {

        binding.spinnerDashboardInterval.adapter =
            createVisibleSpinnerAdapter(
                dashboardIntervals.map {
                    "${it}시간"
                }
            )

        binding.spinnerDashboardMinCount.adapter =
            createVisibleSpinnerAdapter(
                dashboardMinCounts.map {
                    "${it}건"
                }
            )
    }

    /*
     * =========================================================
     * 화면 가시성 고정
     * =========================================================
     *
     * Samsung 다크모드/테마와 관계없이
     * 밝은 카드 + 진한 글씨 + 명확한 버튼 대비를 유지합니다.
     */
    private fun applyVisibilityStyle() {
        binding.editBotToken.setTextColor(Color.parseColor("#102A43"))
        binding.editBotToken.setHintTextColor(Color.parseColor("#829AB1"))

        binding.editChatIds.setTextColor(Color.parseColor("#102A43"))
        binding.editChatIds.setHintTextColor(Color.parseColor("#829AB1"))

        binding.tvTelegramStatus.setTextColor(Color.parseColor("#486581"))

        binding.switchTelegramEnabled.setTextColor(Color.parseColor("#102A43"))
        binding.switchSendImage.setTextColor(Color.parseColor("#102A43"))
        binding.switchDashboardSummary.setTextColor(Color.parseColor("#102A43"))
        binding.switchDashboardZeroWarning.setTextColor(Color.parseColor("#102A43"))
        binding.switchDashboardMissingWarning.setTextColor(Color.parseColor("#102A43"))
        binding.switchDashboardLowCountWarning.setTextColor(Color.parseColor("#102A43"))

        setNavyButton(binding.btnSaveTelegramSettings, "#102A43")
        setNavyButton(binding.btnTelegramTest, "#123E63")
        setNavyButton(binding.btnTelegramBack, "#486581")
    }

    private fun setNavyButton(
        button: android.widget.Button,
        backgroundColor: String
    ) {
        button.setTextColor(Color.WHITE)
        button.backgroundTintList =
            ColorStateList.valueOf(Color.parseColor(backgroundColor))
    }

    private fun createVisibleSpinnerAdapter(
        items: List<String>
    ): ArrayAdapter<String> {

        return object : ArrayAdapter<String>(
            this,
            android.R.layout.simple_spinner_item,
            items
        ) {
            override fun getView(
                position: Int,
                convertView: View?,
                parent: ViewGroup
            ): View {
                val view = super.getView(position, convertView, parent)
                styleSpinnerText(view, false)
                return view
            }

            override fun getDropDownView(
                position: Int,
                convertView: View?,
                parent: ViewGroup
            ): View {
                val view = super.getDropDownView(position, convertView, parent)
                styleSpinnerText(view, true)
                return view
            }
        }.apply {
            setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
            )
        }
    }

    private fun styleSpinnerText(
        view: View,
        isDropDown: Boolean
    ) {
        if (view is TextView) {
            view.setTextColor(Color.parseColor("#102A43"))
            view.textSize = 16f
            view.gravity = Gravity.CENTER_VERTICAL
            view.setPadding(dp(14), 0, dp(14), 0)
            view.setBackgroundColor(
                Color.parseColor(
                    if (isDropDown) "#FFFFFF" else "#F4F6F8"
                )
            )
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun loadCurrentSettings() {
        val settings = TelegramSettingsStore.load(this)

        binding.switchTelegramEnabled.isChecked = settings.enabled
        binding.editBotToken.setText(settings.botToken)
        binding.editChatIds.setText(settings.chatIds.joinToString("\n"))
        binding.switchSendImage.isChecked = settings.sendImage

        binding.switchDashboardSummary.isChecked =
            settings.dashboardSummaryEnabled

        binding.switchDashboardZeroWarning.isChecked =
            settings.dashboardZeroWarning

        binding.switchDashboardMissingWarning.isChecked =
            settings.dashboardMissingItemWarning

        binding.switchDashboardLowCountWarning.isChecked =
            settings.dashboardLowCountWarning

        val intervalIndex =
            dashboardIntervals.indexOf(
                settings.dashboardSummaryIntervalHours
            )

        if (intervalIndex >= 0) {
            binding.spinnerDashboardInterval.setSelection(intervalIndex)
        }

        val minCountIndex =
            dashboardMinCounts.indexOf(
                settings.dashboardMinCount
            )

        if (minCountIndex >= 0) {
            binding.spinnerDashboardMinCount.setSelection(minCountIndex)
        }

        val policyIndex = TelegramSettingsStore.AlertPolicy
            .values()
            .indexOf(settings.alertPolicy)

        if (policyIndex >= 0) {
            binding.spinnerAlertPolicy.setSelection(policyIndex)
        }

        updateStatusText()
    }

    private fun saveSettings() {
        val enabled = binding.switchTelegramEnabled.isChecked
        val token = binding.editBotToken.text?.toString()?.trim().orEmpty()

        val chatIds = binding.editChatIds.text
            ?.toString()
            .orEmpty()
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .toList()

        val policy = TelegramSettingsStore.AlertPolicy.values().getOrElse(
            binding.spinnerAlertPolicy.selectedItemPosition
        ) {
            TelegramSettingsStore.AlertPolicy.NG_ONLY
        }

        if (enabled && token.isBlank()) {
            Toast.makeText(
                this,
                "Telegram을 사용하려면 Bot Token을 입력해주세요.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        if (enabled && chatIds.isEmpty()) {
            Toast.makeText(
                this,
                "Telegram을 사용하려면 Chat ID를 1개 이상 입력해주세요.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        if (!TelegramSettingsStore.saveBotToken(this, token)) {
            Toast.makeText(
                this,
                "Bot Token 암호화 저장에 실패했습니다.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        TelegramSettingsStore.replaceChatIds(this, chatIds)
        TelegramSettingsStore.setAlertPolicy(this, policy)
        TelegramSettingsStore.setSendImage(
            this,
            binding.switchSendImage.isChecked
        )
        TelegramSettingsStore.setEnabled(this, enabled)

        val dashboardInterval =
            dashboardIntervals.getOrElse(
                binding.spinnerDashboardInterval.selectedItemPosition
            ) {
                6
            }

        val dashboardMinCount =
            dashboardMinCounts.getOrElse(
                binding.spinnerDashboardMinCount.selectedItemPosition
            ) {
                4
            }

        TelegramSettingsStore.setDashboardSummarySettings(
            context = this,
            enabled = binding.switchDashboardSummary.isChecked,
            intervalHours = dashboardInterval,
            zeroWarning = binding.switchDashboardZeroWarning.isChecked,
            missingItemWarning = binding.switchDashboardMissingWarning.isChecked,
            lowCountWarning = binding.switchDashboardLowCountWarning.isChecked,
            minCount = dashboardMinCount
        )

        DashboardSummaryWorker.applySchedule(
            this
        )

        updateStatusText()

        Toast.makeText(
            this,
            "Telegram 설정을 저장했습니다.",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun sendTestMessage() {
        val token = binding.editBotToken.text
            ?.toString()
            ?.trim()
            .orEmpty()

        val chatIds = binding.editChatIds.text
            ?.toString()
            .orEmpty()
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .toList()

        val policy = TelegramSettingsStore.AlertPolicy.values().getOrElse(
            binding.spinnerAlertPolicy.selectedItemPosition
        ) {
            TelegramSettingsStore.AlertPolicy.NG_ONLY
        }

        if (token.isBlank()) {
            Toast.makeText(
                this,
                "Bot Token을 입력해주세요.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        if (chatIds.isEmpty()) {
            Toast.makeText(
                this,
                "Chat ID를 1개 이상 입력해주세요.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        if (!TelegramSettingsStore.saveBotToken(this, token)) {
            Toast.makeText(
                this,
                "Bot Token 암호화 저장에 실패했습니다.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        TelegramSettingsStore.replaceChatIds(
            this,
            chatIds
        )

        TelegramSettingsStore.setAlertPolicy(
            this,
            policy
        )

        TelegramSettingsStore.setSendImage(
            this,
            binding.switchSendImage.isChecked
        )

        TelegramSettingsStore.setEnabled(
            this,
            binding.switchTelegramEnabled.isChecked
        )

        binding.tvTelegramStatus.text =
            "Telegram 테스트 메시지 전송 중..."

        binding.btnTelegramTest.isEnabled =
            false

        TelegramSender.sendTestMessage(
            context = this
        ) { result ->

            runOnUiThread {

                binding.btnTelegramTest.isEnabled =
                    true

                binding.tvTelegramStatus.text =
                    buildString {
                        append(
                            if (result.success) {
                                "✅ Telegram 테스트 성공"
                            } else {
                                "⚠ Telegram 테스트 실패"
                            }
                        )
                        append("\n")
                        append(result.message)
                    }

                Toast.makeText(
                    this,
                    result.message,
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun updateStatusText() {
        val settings = TelegramSettingsStore.load(this)

        binding.tvTelegramStatus.text = buildString {
            append("Telegram : ")
            append(if (settings.enabled) "사용" else "사용 안 함")
            append("\nBot Token : ")
            append(if (settings.botToken.isBlank()) "미등록" else "등록됨")
            append("\n수신처 : ${settings.chatIds.size}개")
            append("\n전송 기준 : ${settings.alertPolicy.displayName}")
            append("\n결과 이미지 : ")
            append(if (settings.sendImage) "전송" else "미전송")
            append("\nDashboard Summary : ")
            append(if (settings.dashboardSummaryEnabled) "ON" else "OFF")
            if (settings.dashboardSummaryEnabled) {
                append(" / ${settings.dashboardSummaryIntervalHours}시간")
                append(" / 최소 ${settings.dashboardMinCount}건")
            }
            append("\n\n※ 테스트 메시지로 Bot / Chat 연결을 확인할 수 있습니다.")
            append("\n※ 실제 NG 자동전송은 다음 단계에서 검사 화면과 연결합니다.")
        }
    }
}
