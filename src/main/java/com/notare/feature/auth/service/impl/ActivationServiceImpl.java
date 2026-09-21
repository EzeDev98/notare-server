package com.notare.feature.auth.service.impl;

import com.notare.client.service.InternalAuthClient;
import com.notare.feature.auth.service.ActivationService;
import com.notare.feature.user.model.User;
import com.notare.feature.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ActivationServiceImpl implements ActivationService {

    private final UserRepository userRepository;
    private final InternalAuthClient authClient;

    @Override
    public void resendActivation(String notarianIdOrEmail) {
        String value = notarianIdOrEmail == null ? "" : notarianIdOrEmail.trim();
        if (value.isEmpty()) {
            return;
        }

        String email = value.contains("@")
                ? value.toLowerCase(Locale.ROOT)
                : userRepository.findByNotarianId(value.toUpperCase(Locale.ROOT)).map(User::getEmail).orElse(null);

        // Unknown account: do nothing and answer the same, so this endpoint is not an account oracle.
        if (email != null) {
            authClient.resendActivation(email);
        }
    }
}
