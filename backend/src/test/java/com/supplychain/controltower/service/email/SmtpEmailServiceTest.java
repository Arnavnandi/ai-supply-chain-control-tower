package com.supplychain.controltower.service.email;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@ExtendWith(MockitoExtension.class)
class SmtpEmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    private RestClient restClient;
    private MockRestServiceServer mockServer;
    private SmtpEmailService emailService;

    @BeforeEach
    void setUp() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
        restClient = restClientBuilder.build();

        emailService = new SmtpEmailService(mailSender, restClientBuilder);
        emailService.setRestClient(restClient);
        emailService.setBrevoBaseUrl("https://api.brevo.com/v3");
        emailService.setFromAddress("sender@company.com");
        emailService.setFromName("Tower AI Control Tower");
        emailService.setEmailEnabled(true);
        emailService.setDevMode(false);
    }

    @Test
    @DisplayName("Brevo HTTPS API: Dispatches OTP email successfully on HTTP 201 Created")
    void testSendOtpEmail_BrevoApiSuccess() {
        emailService.setBrevoApiKey("test-xkeysib-api-key");

        mockServer.expect(requestTo("https://api.brevo.com/v3/smtp/email"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("api-key", "test-xkeysib-api-key"))
                .andExpect(header("accept", "application/json"))
                .andExpect(header("content-type", "application/json"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("recipient@test.com")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("123456")))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"messageId\":\"<20261014.12345@smtp-relay.brevo.com>\"}"));

        assertDoesNotThrow(() ->
                emailService.sendOtpEmail("recipient@test.com", "123456", 5));

        mockServer.verify();
        verifyNoInteractions(mailSender);
    }

    @Test
    @DisplayName("Brevo HTTPS API: Throws EmailDeliveryUnavailableException on HTTP 400 Bad Request")
    void testSendOtpEmail_BrevoApiHttpError400() {
        emailService.setBrevoApiKey("test-xkeysib-api-key");

        mockServer.expect(requestTo("https://api.brevo.com/v3/smtp/email"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withBadRequest()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"invalid_parameter\",\"message\":\"sender.email is not a valid email or not verified in your account\"}"));

        EmailDeliveryUnavailableException ex = assertThrows(EmailDeliveryUnavailableException.class, () ->
                emailService.sendOtpEmail("recipient@test.com", "123456", 5));

        assertTrue(ex.getMessage().contains("400"));
        assertTrue(ex.getMessage().contains("sender.email is not a valid email"));
        mockServer.verify();
        verifyNoInteractions(mailSender);
    }

    @Test
    @DisplayName("Brevo HTTPS API: Throws EmailDeliveryUnavailableException on HTTP 401 Unauthorized")
    void testSendOtpEmail_BrevoApiHttpError401() {
        emailService.setBrevoApiKey("invalid-key");

        mockServer.expect(requestTo("https://api.brevo.com/v3/smtp/email"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"unauthorized\",\"message\":\"Key not found\"}"));

        EmailDeliveryUnavailableException ex = assertThrows(EmailDeliveryUnavailableException.class, () ->
                emailService.sendOtpEmail("recipient@test.com", "123456", 5));

        assertTrue(ex.getMessage().contains("401"));
        assertTrue(ex.getMessage().contains("Key not found"));
        mockServer.verify();
    }

    @Test
    @DisplayName("Fail-Closed: Throws EmailDeliveryUnavailableException when neither Brevo API nor SMTP is configured")
    void testSendOtpEmail_MissingApiKeyAndSmtp_FailsClosed() {
        emailService.setBrevoApiKey(null);
        emailService.setMailHost(null);

        assertFalse(emailService.isRealDeliveryConfigured());

        EmailDeliveryUnavailableException ex = assertThrows(EmailDeliveryUnavailableException.class, () ->
                emailService.sendOtpEmail("recipient@test.com", "123456", 5));

        assertTrue(ex.getMessage().contains("Neither Brevo API key nor SMTP relay is configured"));
        verifyNoInteractions(mailSender);
    }

    @Test
    @DisplayName("Dev Mode: Console sink logs and returns immediately without remote calls")
    void testSendOtpEmail_DevMode_ConsoleSink() {
        emailService.setDevMode(true);
        emailService.setBrevoApiKey("some-key");

        assertFalse(emailService.isRealDeliveryConfigured());
        assertDoesNotThrow(() ->
                emailService.sendOtpEmail("recipient@test.com", "123456", 5));

        verifyNoInteractions(mailSender);
    }

    @Test
    @DisplayName("SMTP Fallback: Dispatches via JavaMailSender when Brevo key is absent but SMTP is configured")
    void testSendOtpEmail_SmtpFallback_WhenBrevoKeyAbsent() {
        emailService.setBrevoApiKey("");
        emailService.setMailHost("smtp.example.com");

        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        assertTrue(emailService.isRealDeliveryConfigured());
        assertDoesNotThrow(() ->
                emailService.sendOtpEmail("recipient@test.com", "654321", 5));

        verify(mailSender, times(1)).send(mimeMessage);
    }
}
