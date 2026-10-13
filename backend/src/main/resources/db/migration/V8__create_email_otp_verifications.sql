-- V8__create_email_otp_verifications.sql: Email OTP Multi-Factor Authentication
CREATE TABLE IF NOT EXISTS email_otp_verifications (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    mfa_token VARCHAR(64) NOT NULL UNIQUE,
    hashed_otp VARCHAR(128) NOT NULL,
    salt VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    attempts_remaining INT NOT NULL DEFAULT 3,
    resend_cooldown_until TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    is_used BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_email_otp_mfa_token ON email_otp_verifications(mfa_token);
CREATE INDEX IF NOT EXISTS idx_email_otp_user_id ON email_otp_verifications(user_id);
