package com.example.arintabletusage.mail

import android.util.Log
import java.util.Properties
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

/** JavaMail(SMTP)을 이용해 사용자 개입 없이 텍스트 이메일을 발송한다. */
object EmailSender {
    private const val TAG = "EmailSender"

    data class SmtpConfig(
        val host: String,
        val port: Int,
        val senderEmail: String,
        val senderPassword: String
    )

    fun sendTextMail(
        config: SmtpConfig,
        recipient: String,
        subject: String,
        body: String
    ): Result<Unit> {
        return try {
            val props = Properties().apply {
                put("mail.smtp.auth", "true")
                put("mail.smtp.starttls.enable", "true")
                put("mail.smtp.host", config.host)
                put("mail.smtp.port", config.port.toString())
                put("mail.smtp.ssl.trust", config.host)
                put("mail.smtp.connectiontimeout", "15000")
                put("mail.smtp.timeout", "15000")
            }
            val session = Session.getInstance(props, object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication {
                    return PasswordAuthentication(config.senderEmail, config.senderPassword)
                }
            })
            val message = MimeMessage(session).apply {
                setFrom(InternetAddress(config.senderEmail))
                setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipient))
                setSubject(subject, "UTF-8")
                setText(body, "UTF-8")
            }
            Transport.send(message)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "메일 전송 실패", e)
            Result.failure(e)
        }
    }
}
