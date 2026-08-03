package com.notare.feature.auth.service;

import com.notare.feature.auth.dto.request.ResendOtpRequest;
import com.notare.feature.auth.enums.OtpPurpose;
import com.notare.feature.user.model.User;

public interface OtpService {
    void generateAndSendOtp(User user, OtpPurpose purpose);
    void verifyOtp(Long userId, OtpPurpose purpose, String submittedCode);
    void verifyOtpByNotarianId(String notarianId, OtpPurpose purpose, String submittedCode);
    void resendOtp(ResendOtpRequest request);
    void sendOtp(Long userId);
}