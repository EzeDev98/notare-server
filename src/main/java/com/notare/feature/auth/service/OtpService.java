package com.notare.feature.auth.service;

import com.notare.feature.auth.dto.request.ResendOtpRequest;
import com.notare.feature.auth.enums.OtpPurpose;
import com.notare.feature.user.model.User;

public interface OtpService {

    void generateAndSendOtp(User user, OtpPurpose purpose);

    void verifyOtp(Long userId, OtpPurpose purpose, String submittedCode);

    /**
     * Verify a code against an account named by either its notarianId or its
     * email address.
     *
     * <p>Renamed from verifyOtpByNotarianId: the identifier is no longer one
     * specific thing, and a method name that promises otherwise is the sort
     * that quietly stops being true.
     */
    void verifyOtpByIdentifier(String notarianIdOrEmail, OtpPurpose purpose, String submittedCode);

    void resendOtp(ResendOtpRequest request);

    void sendOtp(Long userId);
}