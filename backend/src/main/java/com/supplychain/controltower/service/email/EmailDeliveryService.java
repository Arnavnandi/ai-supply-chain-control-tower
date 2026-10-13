package com.supplychain.controltower.service.email;

public interface EmailDeliveryService {

    void sendOtpEmail(String recipientEmail, String otpCode, int expiryMinutes);

    boolean isRealDeliveryConfigured();
}
