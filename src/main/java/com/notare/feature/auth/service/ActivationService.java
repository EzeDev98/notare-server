package com.notare.feature.auth.service;

public interface ActivationService {

    /** Asks internal-auth-server to email a fresh activation link. Silent if the account is unknown. */
    void resendActivation(String notarianIdOrEmail);
}
