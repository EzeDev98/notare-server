package com.notare.feature.auth.service;

/** Resends account-activation emails. */
public interface ActivationService {

    void resendActivation(String notarianIdOrEmail);
}
