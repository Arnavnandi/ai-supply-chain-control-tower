package com.supplychain.controltower.service.email;

import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class SmtpEmailService implements EmailDeliveryService {

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${spring.mail.host:}")
    private String mailHost;

    @Value("${app.email.from:no-reply@controltower.supplychain.com}")
    private String fromAddress;

    @Value("${app.email.enabled:true}")
    private boolean emailEnabled;

    @Value("${app.email.dev-mode:false}")
    private boolean devMode;

    @Override
    public boolean isRealDeliveryConfigured() {
        return emailEnabled && !devMode && mailSender != null && mailHost != null && !mailHost.isBlank();
    }

    @Override
    public void sendOtpEmail(String recipientEmail, String otpCode, int expiryMinutes) {
        if (devMode) {
            // Local development console sink: only active when explicitly configured in dev profile!
            log.info("[DEV CONSOLE EMAIL SINK] Dispatched OTP [REDACTED] to email={}. Expiry: {} minutes", recipientEmail, expiryMinutes);
            return;
        }

        if (!isRealDeliveryConfigured()) {
            log.error("[EMAIL DELIVERY] Staging/Production email delivery unavailable. Host: '{}', MailSender: {}",
                    mailHost, mailSender != null ? "Present" : "Missing");
            throw new EmailDeliveryUnavailableException(
                    "Staging email delivery unavailable: SMTP relay is not configured. " +
                    "Set SPRING_MAIL_HOST, SPRING_MAIL_USERNAME, SPRING_MAIL_PASSWORD, and SPRING_MAIL_PORT in environment variables.");
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress, "Tower AI Control Tower");
            helper.setTo(recipientEmail);
            helper.setSubject("Your Verification Code - Tower AI Control Tower");

            String htmlContent = """
                <div style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; max-width: 560px; margin: 0 auto; padding: 24px; background: #0b1120; border: 1px solid #1e293b; border-radius: 16px; color: #f1f5f9;">
                  <div style="margin-bottom: 20px;">
                    <span style="font-size: 18px; font-weight: 800; color: #38bdf8; letter-spacing: 0.05em;">TOWER AI</span>
                    <span style="font-size: 13px; color: #94a3b8; margin-left: 8px;">Supply Chain Intelligence</span>
                  </div>
                  <h2 style="font-size: 20px; font-weight: 700; color: #ffffff; margin-top: 0;">Sign-In Verification Code</h2>
                  <p style="font-size: 14px; color: #cbd5e1; line-height: 1.5;">
                    Please enter the following 6-digit one-time code to authenticate your session:
                  </p>
                  <div style="margin: 28px 0; text-align: center;">
                    <div style="display: inline-block; font-size: 32px; font-weight: 800; letter-spacing: 0.25em; padding: 16px 28px; background: #1e293b; border: 1px solid #38bdf8; border-radius: 12px; color: #38bdf8; font-family: monospace;">
                      %s
                    </div>
                  </div>
                  <p style="font-size: 12px; color: #94a3b8; margin-bottom: 4px;">
                    • This code expires in <strong>%d minutes</strong>.<br/>
                    • Never share this code with anyone. Control Tower operators will never ask for your code.
                  </p>
                  <hr style="border: 0; border-top: 1px solid #1e293b; margin: 24px 0;" />
                  <p style="font-size: 11px; color: #64748b;">
                    If you did not request this verification code, please ignore this email or notify your system administrator immediately.
                  </p>
                </div>
                """.formatted(otpCode, expiryMinutes);

            helper.setText(htmlContent, true);
            mailSender.send(message);
            log.info("[EMAIL DELIVERY] OTP email dispatched to email={}", recipientEmail);
        } catch (EmailDeliveryUnavailableException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("[EMAIL DELIVERY FAILED] Failed to send OTP email to recipient: {}", recipientEmail, ex);
            throw new EmailDeliveryUnavailableException("Staging email delivery failed to dispatch message via SMTP: " + ex.getMessage(), ex);
        }
    }
}
