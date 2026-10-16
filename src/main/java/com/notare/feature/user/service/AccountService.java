package com.notare.feature.user.service;

import com.notare.client.dto.request.InternalChangePasswordRequest;
import com.notare.client.service.InternalAuthClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Account actions that belong to the signed-in person themselves. */
@Service
@RequiredArgsConstructor
public class AccountService {

    private final InternalAuthClient authClient;

    /**
     * Changes the password through the internal auth service (step 17g, B-02), which owns passwords.
     * A wrong current password is its 400 "Incorrect password.", passed through unchanged.
     *
     * <p>Other sessions: the auth service keeps one session per user, the caller's own, so there is
     * none to end and the caller stays signed in. When it holds several, ending the others goes here.
     */
    public void changePassword(String username, String currentPassword, String newPassword) {
        authClient.changePassword(new InternalChangePasswordRequest(username, currentPassword, newPassword));
    }
}
