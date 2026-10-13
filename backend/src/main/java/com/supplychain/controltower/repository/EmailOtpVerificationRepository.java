package com.supplychain.controltower.repository;

import com.supplychain.controltower.entity.EmailOtpVerification;
import com.supplychain.controltower.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface EmailOtpVerificationRepository extends JpaRepository<EmailOtpVerification, Long> {

    Optional<EmailOtpVerification> findByMfaToken(String mfaToken);

    Optional<EmailOtpVerification> findTopByUserAndIsUsedFalseOrderByCreatedAtDesc(User user);

    void deleteByExpiresAtBefore(LocalDateTime time);
}
