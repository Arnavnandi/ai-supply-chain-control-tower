package com.supplychain.controltower.service.email;

import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class SmtpEmailService implements EmailDeliveryService {

    @Autowired(required = false)
    private JavaMailSender mailSender;

    private RestClient restClient;

    @Value("${brevo.api-key:}")
    private String brevoApiKey;

    @Value("${brevo.base-url:https://api.brevo.com/v3}")
    private String brevoBaseUrl;

    @Value("${spring.mail.host:}")
    private String mailHost;

    @Value("${app.email.from:no-reply@controltower.supplychain.com}")
    private String fromAddress;

    @Value("${app.email.from-name:Tower AI Control Tower}")
    private String fromName;

    @Value("${app.email.enabled:true}")
    private boolean emailEnabled;

    @Value("${app.email.dev-mode:false}")
    private boolean devMode;

    public SmtpEmailService() {
        this.restClient = RestClient.builder().build();
    }

    public SmtpEmailService(@Autowired(required = false) JavaMailSender mailSender,
                            @Autowired(required = false) RestClient.Builder restClientBuilder) {
        this.mailSender = mailSender;
        this.restClient = (restClientBuilder != null) ? restClientBuilder.build() : RestClient.builder().build();
    }

    // Setters for unit testing
    public void setBrevoApiKey(String brevoApiKey) { this.brevoApiKey = brevoApiKey; }
    public void setBrevoBaseUrl(String brevoBaseUrl) { this.brevoBaseUrl = brevoBaseUrl; }
    public void setMailHost(String mailHost) { this.mailHost = mailHost; }
    public void setFromAddress(String fromAddress) { this.fromAddress = fromAddress; }
    public void setFromName(String fromName) { this.fromName = fromName; }
    public void setEmailEnabled(boolean emailEnabled) { this.emailEnabled = emailEnabled; }
    public void setDevMode(boolean devMode) { this.devMode = devMode; }
    public void setRestClient(RestClient restClient) { this.restClient = restClient; }
    public void setMailSender(JavaMailSender mailSender) { this.mailSender = mailSender; }

    @Override
    public boolean isRealDeliveryConfigured() {
        if (!emailEnabled || devMode) {
            return false;
        }
        boolean isBrevoConfigured = brevoApiKey != null && !brevoApiKey.isBlank();
        boolean isSmtpConfigured = mailSender != null && mailHost != null && !mailHost.isBlank();
        return isBrevoConfigured || isSmtpConfigured;
    }

    @Override
    public void sendOtpEmail(String recipientEmail, String otpCode, int expiryMinutes) {
        if (devMode) {
            // Local development console sink: only active when explicitly configured in dev profile!
            log.info("[DEV CONSOLE EMAIL SINK] Dispatched OTP [REDACTED] to email={}. Expiry: {} minutes", recipientEmail, expiryMinutes);
            return;
        }

        if (!isRealDeliveryConfigured()) {
            log.error("[EMAIL DELIVERY] Staging/Production email delivery unavailable. Brevo API configured: {}, SMTP Host configured: {}",
                    (brevoApiKey != null && !brevoApiKey.isBlank()), (mailHost != null && !mailHost.isBlank()));
            throw new EmailDeliveryUnavailableException(
                    "Staging email delivery unavailable: Neither Brevo API key nor SMTP relay is configured. " +
                    "Set BREVO_API_KEY (recommended for Render) or SPRING_MAIL_* variables in environment.");
        }

        // Priority 1: Brevo HTTPS REST API (Bypasses Render outbound SMTP port 587 block)
        if (brevoApiKey != null && !brevoApiKey.isBlank()) {
            sendViaBrevoApi(recipientEmail, otpCode, expiryMinutes);
            return;
        }

        // Priority 2: Standard SMTP Relay (Used when SMTP is explicitly configured and Brevo API key is absent)
        sendViaSmtp(recipientEmail, otpCode, expiryMinutes);
    }

    private void sendViaBrevoApi(String recipientEmail, String otpCode, int expiryMinutes) {
        String htmlContent = buildOtpHtmlContent(otpCode, expiryMinutes);
        String textContent = String.format("Your verification code is: %s. This code expires in %d minutes.", otpCode, expiryMinutes);
        String senderName = (fromName != null && !fromName.isBlank()) ? fromName : "Tower AI Control Tower";

        Map<String, Object> payload = Map.of(
                "sender", Map.of("name", senderName, "email", fromAddress),
                "to", List.of(Map.of("email", recipientEmail)),
                "subject", "Your Verification Code - Tower AI Control Tower",
                "htmlContent", htmlContent,
                "textContent", textContent
        );

        String endpoint = (brevoBaseUrl != null && !brevoBaseUrl.isBlank())
                ? brevoBaseUrl.replaceAll("/+$", "") + "/smtp/email"
                : "https://api.brevo.com/v3/smtp/email";

        try {
            ResponseEntity<String> response = restClient.post()
                    .uri(endpoint)
                    .header("api-key", brevoApiKey.trim())
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toEntity(String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("[BREVO API EMAIL DELIVERY] OTP email dispatched successfully via Brevo HTTPS API to email={}", recipientEmail);
            } else {
                log.error("[BREVO API EMAIL DELIVERY FAILED] Status: {} Body: {}", response.getStatusCode(), response.getBody());
                throw new EmailDeliveryUnavailableException(
                        "Brevo API email delivery returned status " + response.getStatusCode() + ": " + response.getBody());
            }
        } catch (EmailDeliveryUnavailableException ex) {
            throw ex;
        } catch (RestClientResponseException ex) {
            log.error("[BREVO API HTTP ERROR] Status: {} Body: {}", ex.getStatusCode(), ex.getResponseBodyAsString(), ex);
            throw new EmailDeliveryUnavailableException(
                    String.format("Brevo API email delivery failed (HTTP %s): %s", ex.getStatusCode(), ex.getResponseBodyAsString()), ex);
        } catch (Exception ex) {
            log.error("[BREVO API CALL FAILED] Error calling Brevo API: {}", ex.getMessage(), ex);
            throw new EmailDeliveryUnavailableException("Failed to dispatch email via Brevo HTTPS API: " + ex.getMessage(), ex);
        }
    }

    private void sendViaSmtp(String recipientEmail, String otpCode, int expiryMinutes) {
        if (mailSender == null) {
            throw new EmailDeliveryUnavailableException("SMTP mail sender bean is not initialized.");
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            String senderName = (fromName != null && !fromName.isBlank()) ? fromName : "Tower AI Control Tower";
            helper.setFrom(fromAddress, senderName);
            helper.setTo(recipientEmail);
            helper.setSubject("Your Verification Code - Tower AI Control Tower");

            String htmlContent = buildOtpHtmlContent(otpCode, expiryMinutes);
            helper.setText(htmlContent, true);
            mailSender.send(message);
            log.info("[EMAIL DELIVERY] OTP email dispatched via SMTP to email={}", recipientEmail);
        } catch (EmailDeliveryUnavailableException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("[EMAIL DELIVERY FAILED] Failed to send OTP email to recipient via SMTP: {}", recipientEmail, ex);
            throw new EmailDeliveryUnavailableException("Staging email delivery failed to dispatch message via SMTP: " + ex.getMessage(), ex);
        }
    }

    private String buildOtpHtmlContent(String otpCode, int expiryMinutes) {
        return """
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
    }
}
