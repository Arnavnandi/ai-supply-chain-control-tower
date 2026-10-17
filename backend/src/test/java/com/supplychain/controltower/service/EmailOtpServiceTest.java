package com.supplychain.controltower.service;

import com.supplychain.controltower.dto.auth.LoginInitiationResponse;
import com.supplychain.controltower.entity.EmailOtpVerification;
import com.supplychain.controltower.entity.Role;
import com.supplychain.controltower.entity.User;
import com.supplychain.controltower.repository.EmailOtpVerificationRepository;
import com.supplychain.controltower.service.email.EmailDeliveryService;
import com.supplychain.controltower.service.email.EmailDeliveryUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailOtpServiceTest {

    @Mock
    private EmailOtpVerificationRepository otpRepository;

    @Mock
    private EmailDeliveryService emailDeliveryService;

    @InjectMocks
    private EmailOtpService emailOtpService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(101L)
                .username("testoperator")
                .email("operator@supplychain.com")
                .role(Role.ROLE_ADMIN)
                .build();
    }

    @Test
    void testInitiateOtpChallenge_GeneratesHashedOtpAndDispatchesEmail() {
        ArgumentCaptor<EmailOtpVerification> entityCaptor = ArgumentCaptor.forClass(EmailOtpVerification.class);
        ArgumentCaptor<String> otpCaptor = ArgumentCaptor.forClass(String.class);

        LoginInitiationResponse response = emailOtpService.initiateOtpChallenge(testUser);

        assertNotNull(response);
        assertTrue(response.isMfaRequired());
        assertNotNull(response.getMfaToken());
        assertEquals("o***r@supplychain.com", response.getMaskedEmail());
        assertEquals(300L, response.getExpiresInSeconds());
        assertEquals(60L, response.getResendCooldownSeconds());

        verify(emailDeliveryService, times(1)).sendOtpEmail(eq("operator@supplychain.com"), otpCaptor.capture(), eq(5));
        String rawOtp = otpCaptor.getValue();
        assertEquals(6, rawOtp.length());

        verify(otpRepository, times(1)).save(entityCaptor.capture());
        EmailOtpVerification saved = entityCaptor.getValue();
        assertEquals(testUser, saved.getUser());
        assertEquals(3, saved.getAttemptsRemaining());
        assertFalse(saved.getIsUsed());
        assertNotNull(saved.getHashedOtp());
        assertNotNull(saved.getSalt());

        // Plaintext OTP must NOT be stored in database
        assertNotEquals(rawOtp, saved.getHashedOtp());
    }

    @Test
    void testVerifyOtp_ValidCode_MarksUsedAndReturnsUser() throws NoSuchAlgorithmException {
        String salt = Base64.getEncoder().encodeToString("test-salt-16bytes".getBytes(StandardCharsets.UTF_8));
        String rawOtp = "654321";
        String hashedOtp = computeHash(salt, rawOtp);

        EmailOtpVerification record = EmailOtpVerification.builder()
                .id(1L)
                .user(testUser)
                .mfaToken("valid-mfa-token")
                .hashedOtp(hashedOtp)
                .salt(salt)
                .expiresAt(LocalDateTime.now().plusMinutes(4))
                .attemptsRemaining(3)
                .resendCooldownUntil(LocalDateTime.now().plusSeconds(30))
                .isUsed(false)
                .build();

        when(otpRepository.findByMfaToken("valid-mfa-token")).thenReturn(Optional.of(record));

        User resultUser = emailOtpService.verifyOtp("valid-mfa-token", "654321");

        assertNotNull(resultUser);
        assertEquals(testUser.getId(), resultUser.getId());
        assertTrue(record.getIsUsed(), "Successful OTP verification must mark challenge as used (single-use enforcement)!");
        verify(otpRepository, times(1)).save(record);
    }

    @Test
    void testVerifyOtp_IncorrectCode_DecrementsAttemptsRemaining() throws NoSuchAlgorithmException {
        String salt = Base64.getEncoder().encodeToString("test-salt-16bytes".getBytes(StandardCharsets.UTF_8));
        String hashedOtp = computeHash(salt, "654321");

        EmailOtpVerification record = EmailOtpVerification.builder()
                .id(1L)
                .user(testUser)
                .mfaToken("valid-mfa-token")
                .hashedOtp(hashedOtp)
                .salt(salt)
                .expiresAt(LocalDateTime.now().plusMinutes(4))
                .attemptsRemaining(3)
                .resendCooldownUntil(LocalDateTime.now().plusSeconds(30))
                .isUsed(false)
                .build();

        when(otpRepository.findByMfaToken("valid-mfa-token")).thenReturn(Optional.of(record));

        BadCredentialsException ex = assertThrows(BadCredentialsException.class, () ->
                emailOtpService.verifyOtp("valid-mfa-token", "000000"));

        assertTrue(ex.getMessage().contains("2 attempt(s) remaining"));
        assertEquals(2, record.getAttemptsRemaining());
        assertFalse(record.getIsUsed());
        verify(otpRepository, times(1)).save(record);
    }

    @Test
    void testVerifyOtp_ThreeFailedAttempts_LocksSession() throws NoSuchAlgorithmException {
        String salt = Base64.getEncoder().encodeToString("test-salt-16bytes".getBytes(StandardCharsets.UTF_8));
        String hashedOtp = computeHash(salt, "654321");

        EmailOtpVerification record = EmailOtpVerification.builder()
                .id(1L)
                .user(testUser)
                .mfaToken("valid-mfa-token")
                .hashedOtp(hashedOtp)
                .salt(salt)
                .expiresAt(LocalDateTime.now().plusMinutes(4))
                .attemptsRemaining(1) // Last attempt remaining
                .resendCooldownUntil(LocalDateTime.now().plusSeconds(30))
                .isUsed(false)
                .build();

        when(otpRepository.findByMfaToken("valid-mfa-token")).thenReturn(Optional.of(record));

        BadCredentialsException ex = assertThrows(BadCredentialsException.class, () ->
                emailOtpService.verifyOtp("valid-mfa-token", "999999"));

        assertTrue(ex.getMessage().contains("locked"));
        assertEquals(0, record.getAttemptsRemaining());
        verify(otpRepository, times(1)).save(record);
    }

    @Test
    void testVerifyOtp_ExpiredCode_ThrowsBadCredentialsException() throws NoSuchAlgorithmException {
        String salt = Base64.getEncoder().encodeToString("test-salt-16bytes".getBytes(StandardCharsets.UTF_8));
        String hashedOtp = computeHash(salt, "654321");

        EmailOtpVerification record = EmailOtpVerification.builder()
                .id(1L)
                .user(testUser)
                .mfaToken("expired-mfa-token")
                .hashedOtp(hashedOtp)
                .salt(salt)
                .expiresAt(LocalDateTime.now().minusMinutes(1)) // Expired!
                .attemptsRemaining(3)
                .resendCooldownUntil(LocalDateTime.now().minusMinutes(2))
                .isUsed(false)
                .build();

        when(otpRepository.findByMfaToken("expired-mfa-token")).thenReturn(Optional.of(record));

        BadCredentialsException ex = assertThrows(BadCredentialsException.class, () ->
                emailOtpService.verifyOtp("expired-mfa-token", "654321"));

        assertTrue(ex.getMessage().contains("expired"));
    }

    @Test
    void testVerifyOtp_ReusedCode_ThrowsBadCredentialsException() throws NoSuchAlgorithmException {
        String salt = Base64.getEncoder().encodeToString("test-salt-16bytes".getBytes(StandardCharsets.UTF_8));
        String hashedOtp = computeHash(salt, "654321");

        EmailOtpVerification record = EmailOtpVerification.builder()
                .id(1L)
                .user(testUser)
                .mfaToken("reused-mfa-token")
                .hashedOtp(hashedOtp)
                .salt(salt)
                .expiresAt(LocalDateTime.now().plusMinutes(4))
                .attemptsRemaining(3)
                .resendCooldownUntil(LocalDateTime.now().plusSeconds(30))
                .isUsed(true) // Already used!
                .build();

        when(otpRepository.findByMfaToken("reused-mfa-token")).thenReturn(Optional.of(record));

        BadCredentialsException ex = assertThrows(BadCredentialsException.class, () ->
                emailOtpService.verifyOtp("reused-mfa-token", "654321"));

        assertTrue(ex.getMessage().contains("already been used"));
    }

    @Test
    void testResendOtp_BeforeCooldown_ThrowsBadCredentialsException() {
        EmailOtpVerification record = EmailOtpVerification.builder()
                .id(1L)
                .user(testUser)
                .mfaToken("cooling-mfa-token")
                .hashedOtp("hash")
                .salt("salt")
                .expiresAt(LocalDateTime.now().plusMinutes(4))
                .attemptsRemaining(2)
                .resendCooldownUntil(LocalDateTime.now().plusSeconds(45)) // In cooldown!
                .isUsed(false)
                .build();

        when(otpRepository.findByMfaToken("cooling-mfa-token")).thenReturn(Optional.of(record));

        BadCredentialsException ex = assertThrows(BadCredentialsException.class, () ->
                emailOtpService.resendOtp("cooling-mfa-token"));

        assertTrue(ex.getMessage().contains("wait"));
        verify(emailDeliveryService, never()).sendOtpEmail(anyString(), anyString(), anyInt());
    }

    @Test
    void testResendOtp_AfterCooldown_GeneratesNewSaltAndHashedOtp() {
        EmailOtpVerification record = EmailOtpVerification.builder()
                .id(1L)
                .user(testUser)
                .mfaToken("cooling-mfa-token")
                .hashedOtp("old_hash")
                .salt("old_salt")
                .expiresAt(LocalDateTime.now().plusMinutes(1))
                .attemptsRemaining(1)
                .resendCooldownUntil(LocalDateTime.now().minusSeconds(5)) // Cooldown expired!
                .isUsed(false)
                .build();

        when(otpRepository.findByMfaToken("cooling-mfa-token")).thenReturn(Optional.of(record));

        LoginInitiationResponse response = emailOtpService.resendOtp("cooling-mfa-token");

        assertNotNull(response);
        assertEquals(3, record.getAttemptsRemaining(), "Resend must reset attempt counter to 3!");
        assertNotEquals("old_hash", record.getHashedOtp());
        verify(emailDeliveryService, times(1)).sendOtpEmail(eq("operator@supplychain.com"), anyString(), eq(5));
        verify(otpRepository, times(1)).save(record);
    }

    @Test
    void testInitiateOtpChallenge_WhenEmailDeliveryFails_PropagatesException() {
        doThrow(new EmailDeliveryUnavailableException("Staging email delivery unavailable: SMTP relay is not configured."))
                .when(emailDeliveryService).sendOtpEmail(anyString(), anyString(), anyInt());

        assertThrows(EmailDeliveryUnavailableException.class, () ->
                emailOtpService.initiateOtpChallenge(testUser));
    }

    @Test
    void testInitiateOtpChallenge_WhenWithinCooldown_ReusesActiveChallengeWithoutDispatchingEmail() {
        EmailOtpVerification activeChallenge = EmailOtpVerification.builder()
                .id(99L)
                .user(testUser)
                .mfaToken("active-mfa-token-123")
                .hashedOtp("some_hash")
                .salt("some_salt")
                .expiresAt(LocalDateTime.now().plusMinutes(4))
                .attemptsRemaining(3)
                .resendCooldownUntil(LocalDateTime.now().plusSeconds(45))
                .isUsed(false)
                .build();

        when(otpRepository.findTopByUserAndIsUsedFalseOrderByCreatedAtDesc(testUser))
                .thenReturn(Optional.of(activeChallenge));

        LoginInitiationResponse response = emailOtpService.initiateOtpChallenge(testUser);

        assertNotNull(response);
        assertTrue(response.isMfaRequired());
        assertEquals("active-mfa-token-123", response.getMfaToken());
        assertTrue(response.getResendCooldownSeconds() > 0 && response.getResendCooldownSeconds() <= 45);

        // Crucial: no new email must be dispatched during cooldown!
        verify(emailDeliveryService, never()).sendOtpEmail(anyString(), anyString(), anyInt());
        verify(otpRepository, never()).save(any());
    }

    @Test
    void testInitiateOtpChallenge_WhenAfterCooldown_DeletesOldAndCreatesNewChallenge() {
        EmailOtpVerification oldChallenge = EmailOtpVerification.builder()
                .id(88L)
                .user(testUser)
                .mfaToken("old-mfa-token-456")
                .hashedOtp("old_hash")
                .salt("old_salt")
                .expiresAt(LocalDateTime.now().plusMinutes(3))
                .attemptsRemaining(3)
                .resendCooldownUntil(LocalDateTime.now().minusSeconds(10))
                .isUsed(false)
                .build();

        when(otpRepository.findTopByUserAndIsUsedFalseOrderByCreatedAtDesc(testUser))
                .thenReturn(Optional.of(oldChallenge));

        LoginInitiationResponse response = emailOtpService.initiateOtpChallenge(testUser);

        assertNotNull(response);
        assertTrue(response.isMfaRequired());
        assertNotEquals("old-mfa-token-456", response.getMfaToken());

        // Old unverified challenge must be deleted rather than marked is_used=true
        verify(otpRepository, times(1)).delete(oldChallenge);
        verify(otpRepository, never()).save(oldChallenge);

        // New email must be dispatched
        verify(emailDeliveryService, times(1)).sendOtpEmail(eq("operator@supplychain.com"), anyString(), eq(5));
    }

    @Test
    void testInitiateOtpChallenge_ReplacingExpiredChallenge_DoesNotMarkUnverifiedUserAsVerified() {
        EmailOtpVerification expiredChallenge = EmailOtpVerification.builder()
                .id(92L)
                .user(testUser)
                .mfaToken("expired-mfa-token-789")
                .hashedOtp("expired_hash")
                .salt("expired_salt")
                .expiresAt(LocalDateTime.now().minusMinutes(10))
                .attemptsRemaining(3)
                .resendCooldownUntil(LocalDateTime.now().minusMinutes(9))
                .isUsed(false)
                .build();

        when(otpRepository.findTopByUserAndIsUsedFalseOrderByCreatedAtDesc(testUser))
                .thenReturn(Optional.of(expiredChallenge));

        LoginInitiationResponse response = emailOtpService.initiateOtpChallenge(testUser);

        assertNotNull(response);
        assertTrue(response.isMfaRequired());

        // Confirm expired challenge was deleted so it cannot be misconstrued as a verified OTP
        verify(otpRepository, times(1)).delete(expiredChallenge);

        // Verification query must still report false
        when(otpRepository.existsByUserAndIsUsedTrue(testUser)).thenReturn(false);
        assertFalse(emailOtpService.isUserEmailVerified(testUser),
                "Replacing an expired/superseded challenge must not mark an unverified user as email-verified");
    }

    @Test
    void testIsUserEmailVerified_ReturnsTrueForSeedAdminUser() {
        User adminUser = User.builder().id(1L).username("admin").email("admin@supplychain.com").build();
        assertTrue(emailOtpService.isUserEmailVerified(adminUser), "Seed admin user must be verified by default");
        verify(otpRepository, never()).existsByUserAndIsUsedTrue(any());
    }

    @Test
    void testIsUserEmailVerified_ReturnsTrueForSeedManagerUser() {
        User managerUser = User.builder().id(2L).username("manager").email("manager@supplychain.com").build();
        assertTrue(emailOtpService.isUserEmailVerified(managerUser), "Seed manager user must be verified by default");
        verify(otpRepository, never()).existsByUserAndIsUsedTrue(any());
    }

    @Test
    void testIsUserEmailVerified_ReturnsTrueWhenUsedVerificationExists() {
        User regularUser = User.builder().id(50L).username("john_doe").email("john@corp.com").build();
        when(otpRepository.existsByUserAndIsUsedTrue(regularUser)).thenReturn(true);

        assertTrue(emailOtpService.isUserEmailVerified(regularUser), "User with used OTP record must be verified");
        verify(otpRepository, times(1)).existsByUserAndIsUsedTrue(regularUser);
    }

    @Test
    void testIsUserEmailVerified_ReturnsFalseWhenNoUsedVerificationExists() {
        User unverifiedUser = User.builder().id(51L).username("jane_doe").email("jane@corp.com").build();
        when(otpRepository.existsByUserAndIsUsedTrue(unverifiedUser)).thenReturn(false);

        assertFalse(emailOtpService.isUserEmailVerified(unverifiedUser), "User without used OTP record must not be verified");
        verify(otpRepository, times(1)).existsByUserAndIsUsedTrue(unverifiedUser);
    }

    @Test
    void testIsUserEmailVerified_ReturnsFalseForNullUser() {
        assertFalse(emailOtpService.isUserEmailVerified(null));
    }

    private String computeHash(String salt, String rawOtp) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(salt.getBytes(StandardCharsets.UTF_8));
        byte[] hash = digest.digest(rawOtp.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(hash);
    }
}
