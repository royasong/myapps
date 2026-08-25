package com.example.arintabletusage.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * 이메일 수신자, 발신 SMTP 계정, 예약 시간 등을 암호화된 SharedPreferences에 저장/조회한다.
 * 발신 계정의 앱 비밀번호(App Password)가 포함되므로 평문 SharedPreferences 대신
 * EncryptedSharedPreferences를 사용한다.
 */
class AppPreferences(context: Context) {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "arin_usage_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /** 리포트를 받을 수신 이메일 */
    var recipientEmail: String
        get() = prefs.getString(KEY_RECIPIENT_EMAIL, DEFAULT_EMAIL) ?: DEFAULT_EMAIL
        set(value) = prefs.edit().putString(KEY_RECIPIENT_EMAIL, value).apply()

    /** 발신에 사용할 이메일 계정 (예: Gmail) */
    var senderEmail: String
        get() = prefs.getString(KEY_SENDER_EMAIL, DEFAULT_EMAIL) ?: DEFAULT_EMAIL
        set(value) = prefs.edit().putString(KEY_SENDER_EMAIL, value).apply()

    /** 발신 계정의 앱 비밀번호(App Password) */
    var senderPassword: String
        get() = prefs.getString(KEY_SENDER_PASSWORD, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SENDER_PASSWORD, value).apply()

    var smtpHost: String
        get() = prefs.getString(KEY_SMTP_HOST, "smtp.gmail.com") ?: "smtp.gmail.com"
        set(value) = prefs.edit().putString(KEY_SMTP_HOST, value).apply()

    var smtpPort: Int
        get() = prefs.getInt(KEY_SMTP_PORT, 587)
        set(value) = prefs.edit().putInt(KEY_SMTP_PORT, value).apply()

    /** 매일 리포트를 발송할 시(0~23) */
    var scheduleHour: Int
        get() = prefs.getInt(KEY_HOUR, 21)
        set(value) = prefs.edit().putInt(KEY_HOUR, value).apply()

    /** 매일 리포트를 발송할 분(0~59) */
    var scheduleMinute: Int
        get() = prefs.getInt(KEY_MINUTE, 0)
        set(value) = prefs.edit().putInt(KEY_MINUTE, value).apply()

    /** 예약이 활성화되어 있는지 여부 */
    var scheduleEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    /** 하루 총 사용 시간이 이 값(시간)을 넘으면 경고 대상으로 판단한다. 기본값 3시간. */
    var warningThresholdHours: Int
        get() = prefs.getInt(KEY_WARNING_THRESHOLD_HOURS, DEFAULT_WARNING_THRESHOLD_HOURS)
        set(value) = prefs.edit().putInt(KEY_WARNING_THRESHOLD_HOURS, value).apply()

    /** 마지막 발송 결과 메시지 (UI에 표시용) */
    var lastSendResult: String
        get() = prefs.getString(KEY_LAST_RESULT, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_RESULT, value).apply()

    /** 마지막 발송 시각(epoch millis) */
    var lastSendAt: Long
        get() = prefs.getLong(KEY_LAST_SENT_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SENT_AT, value).apply()

    companion object {
        private const val KEY_RECIPIENT_EMAIL = "recipient_email"
        private const val KEY_SENDER_EMAIL = "sender_email"
        private const val KEY_SENDER_PASSWORD = "sender_password"
        private const val KEY_SMTP_HOST = "smtp_host"
        private const val KEY_SMTP_PORT = "smtp_port"
        private const val KEY_HOUR = "schedule_hour"
        private const val KEY_MINUTE = "schedule_minute"
        private const val KEY_ENABLED = "schedule_enabled"
        private const val KEY_LAST_RESULT = "last_send_result"
        private const val KEY_LAST_SENT_AT = "last_send_at"
        private const val KEY_WARNING_THRESHOLD_HOURS = "warning_threshold_hours"
        const val DEFAULT_WARNING_THRESHOLD_HOURS = 3
        private const val DEFAULT_EMAIL = "roya.song@gmail.com"
    }
}
