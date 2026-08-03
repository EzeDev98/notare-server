package com.notare.feature.auth.service;

public interface EmailService {
    void sendEmail(String toEmail, String recipient, String otpCode);
}
