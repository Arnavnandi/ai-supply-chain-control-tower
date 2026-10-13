package com.supplychain.controltower.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginInitiationResponse {

    private boolean mfaRequired;
    private String mfaToken;
    private String maskedEmail;
    private long expiresInSeconds;
    private long resendCooldownSeconds;
    private String message;
}
