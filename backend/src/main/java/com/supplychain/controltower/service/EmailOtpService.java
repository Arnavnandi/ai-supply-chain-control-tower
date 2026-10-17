package com.supplychain.controltower.service;

import com.supplychain.controltower.dto.auth.LoginInitiationResponse;
import com.supplychain.controltower.entity.EmailOtpVerification;
import com.supplychain.controltower.entity.User;
import com.supplychain.controltower.repository.EmailOtpVerificationRepository;
import com.supplychain.controltower.service.email.EmailDeliveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailOtpService {

    private static final int OTP_EXPIRY_MINUTES = 5;
    private static final int RESEND_COOLDOWN_SECONDS = 60;
    private static final int MAX_ATTEMPTS = 3;

    private final EmailOtpVerificationRepository otpRepository;
    private final EmailDeliveryService emailDeliveryService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional(readOnly = true)
    public boolean isUserEmailVerified(User user) {
        if (user == null) {
            return false;
        }
        // Seed administrative accounts are verified by default
        if ("admin".equalsIgnoreCase(user.getUsername()) || "manager".equalsIgnoreCase(user.getUsername())) {
            return true;
        }
        return otpRepository.existsByUserAndIsUsedTrue(user);
    }

    @Transactional
    public LoginInitiationResponse initiateOtpChallenge(User user) {
        LocalDateTime now = LocalDateTime.now();

        // Enforce per-user initiation cooldown: prevent rapid repeated login requests spamming SMTP
        Optional<EmailOtpVerification> existingActive = otpRepository.findTopByUserAndIsUsedFalseOrderByCreatedAtDesc(user);
        if (existingActive.isPresent()) {
            EmailOtpVerification active = existingActive.get();
            if (now.isBefore(active.getResendCooldownUntil()) && now.isBefore(active.getExpiresAt()) && active.getAttemptsRemaining() > 0) {
                long remainingSeconds = Duration.between(now, active.getResendCooldownUntil()).toSeconds();
                long remainingExpiry = Duration.between(now, active.getExpiresAt()).toSeconds();
                log.info("[OTP CHALLENGE] Reusing active verification challenge for userId={}, mfaToken={} (cooldown active: {}s remaining)",
                        user.getId(), active.getMfaToken(), remainingSeconds);
                return LoginInitiationResponse.builder()
                        .mfaRequired(true)
                        .mfaToken(active.getMfaToken())
                        .maskedEmail(maskEmail(user.getEmail()))
                        .expiresInSeconds(Math.max(1, remainingExpiry))
                        .resendCooldownSeconds(Math.max(1, remainingSeconds))
                        .message("A verification code was recently sent. Please check your email or wait for cooldown to request a new code.")
                        .build();
            } else {
                // Invalidate older unused challenge before creating a fresh one
                active.setIsUsed(true);
                otpRepository.save(active);
            }
        }

        String rawOtp = generateSecure6DigitCode();
        String mfaToken = UUID.randomUUID().toString();
        String salt = generateSalt();
        String hashedOtp = hashOtp(salt, rawOtp);

        LocalDateTime expiresAt = now.plusMinutes(OTP_EXPIRY_MINUTES);
        LocalDateTime cooldownUntil = now.plusSeconds(RESEND_COOLDOWN_SECONDS);

        EmailOtpVerification verification = EmailOtpVerification.builder()
                .user(user)
                .mfaToken(mfaToken)
                .hashedOtp(hashedOtp)
                .salt(salt)
                .expiresAt(expiresAt)
                .attemptsRemaining(MAX_ATTEMPTS)
                .resendCooldownUntil(cooldownUntil)
                .isUsed(false)
                .build();

        otpRepository.save(verification);

        // Dispatch OTP via email delivery service (fails fast if unconfigured in staging)
        emailDeliveryService.sendOtpEmail(user.getEmail(), rawOtp, OTP_EXPIRY_MINUTES);

        log.info("[OTP CHALLENGE] Created verification challenge for userId={}, mfaToken={}",
                user.getId(), mfaToken);

        return LoginInitiationResponse.builder()
                .mfaRequired(true)
                .mfaToken(mfaToken)
                .maskedEmail(maskEmail(user.getEmail()))
                .expiresInSeconds(OTP_EXPIRY_MINUTES * 60L)
                .resendCooldownSeconds(RESEND_COOLDOWN_SECONDS)
                .message("Verification code sent to registered email.")
                .build();
    }

    @Transactional
    public User verifyOtp(String mfaToken, String submittedOtp) {
        if (mfaToken == null || mfaToken.isBlank() || submittedOtp == null || submittedOtp.isBlank()) {
            throw new BadCredentialsException("Verification token and OTP code are required.");
        }

        EmailOtpVerification verification = otpRepository.findByMfaToken(mfaToken.trim())
                .orElseThrow(() -> new BadCredentialsException("Invalid or expired verification session."));

        if (Boolean.TRUE.equals(verification.getIsUsed())) {
            throw new BadCredentialsException("This verification code has already been used. Please log in again.");
        }

        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(verification.getExpiresAt())) {
            throw new BadCredentialsException("Verification code has expired. Please request a new code.");
        }

        if (verification.getAttemptsRemaining() <= 0) {
            throw new BadCredentialsException("Too many failed attempts. Verification session locked. Please log in again.");
        }

        String computedHash = hashOtp(verification.getSalt(), submittedOtp.trim());
        boolean matches = MessageDigest.isEqual(
                verification.getHashedOtp().getBytes(StandardCharsets.UTF_8),
                computedHash.getBytes(StandardCharsets.UTF_8)
        );

        if (!matches) {
            int remaining = verification.getAttemptsRemaining() - 1;
            verification.setAttemptsRemaining(remaining);
            otpRepository.save(verification);

            if (remaining <= 0) {
                log.warn("[OTP VERIFY] Verification locked for userId={} due to excessive attempts",
                        verification.getUser().getId());
                throw new BadCredentialsException("Too many failed attempts. Verification session locked. Please log in again.");
            } else {
                throw new BadCredentialsException("Invalid verification code. " + remaining + " attempt(s) remaining.");
            }
        }

        // Single-use enforcement: invalidate OTP immediately upon successful verification
        verification.setIsUsed(true);
        otpRepository.save(verification);

        log.info("[OTP VERIFY] Verification successful for userId={}", verification.getUser().getId());
        return verification.getUser();
    }

    @Transactional
    public LoginInitiationResponse resendOtp(String mfaToken) {
        if (mfaToken == null || mfaToken.isBlank()) {
            throw new BadCredentialsException("MFA token is required.");
        }

        EmailOtpVerification verification = otpRepository.findByMfaToken(mfaToken.trim())
                .orElseThrow(() -> new BadCredentialsException("Invalid verification session."));

        if (Boolean.TRUE.equals(verification.getIsUsed())) {
            throw new BadCredentialsException("This verification session has already been used. Please log in again.");
        }

        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(verification.getResendCooldownUntil())) {
            long remainingSeconds = Duration.between(now, verification.getResendCooldownUntil()).toSeconds();
            throw new BadCredentialsException("Please wait " + Math.max(1, remainingSeconds) + " seconds before requesting a new code.");
        }

        String newRawOtp = generateSecure6DigitCode();
        String newSalt = generateSalt();
        String newHashedOtp = hashOtp(newSalt, newRawOtp);

        LocalDateTime newExpiresAt = now.plusMinutes(OTP_EXPIRY_MINUTES);
        LocalDateTime newCooldownUntil = now.plusSeconds(RESEND_COOLDOWN_SECONDS);

        verification.setHashedOtp(newHashedOtp);
        verification.setSalt(newSalt);
        verification.setAttemptsRemaining(MAX_ATTEMPTS);
        verification.setExpiresAt(newExpiresAt);
        verification.setResendCooldownUntil(newCooldownUntil);

        otpRepository.save(verification);

        emailDeliveryService.sendOtpEmail(verification.getUser().getEmail(), newRawOtp, OTP_EXPIRY_MINUTES);

        log.info("[OTP RESEND] Resent verification code for userId={}", verification.getUser().getId());

        return LoginInitiationResponse.builder()
                .mfaRequired(true)
                .mfaToken(mfaToken)
                .maskedEmail(maskEmail(verification.getUser().getEmail()))
                .expiresInSeconds(OTP_EXPIRY_MINUTES * 60L)
                .resendCooldownSeconds(RESEND_COOLDOWN_SECONDS)
                .message("A new verification code has been dispatched to your email.")
                .build();
    }

    private String generateSecure6DigitCode() {
        int code = 100_000 + secureRandom.nextInt(900_000);
        return String.valueOf(code);
    }

    private String generateSalt() {
        byte[] saltBytes = new byte[16];
        secureRandom.nextBytes(saltBytes);
        return Base64.getEncoder().encodeToString(saltBytes);
    }

    private String hashOtp(String salt, String otp) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(salt.getBytes(StandardCharsets.UTF_8));
            byte[] hashedBytes = digest.digest(otp.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashedBytes);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 digest algorithm not available", ex);
        }
    }

    public static String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***@***";
        }
        String[] parts = email.split("@", 2);
        String name = parts[0];
        String domain = parts[1];

        if (name.length() <= 2) {
            return name.charAt(0) + "***@" + domain;
        }
        return name.charAt(0) + "***" + name.charAt(name.length() - 1) + "@" + domain;
    }
}
