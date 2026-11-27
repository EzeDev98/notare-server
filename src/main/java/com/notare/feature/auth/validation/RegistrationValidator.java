package com.notare.feature.auth.validation;

import com.notare.common.exception.RegistrationException;
import com.notare.feature.user.dto.request.RegistrationRequest;
import org.springframework.stereotype.Component;

/** Validates new-user registration requests. */
@Component
public class RegistrationValidator {

    public void validateRegistrationRequest(RegistrationRequest request) {

        if (request.getFirstName() == null || request.getFirstName().isBlank()) {
            throw new RegistrationException("First name is required");
        }

        if (request.getLastName() == null || request.getLastName().isBlank()) {
            throw new RegistrationException("Last name is required");
        }

        if (request.getUsername() == null || request.getUsername().isBlank()) {
            throw new RegistrationException("Username is required");
        }

        if (request.getEmail() == null || request.getEmail().isBlank()) {
            throw new RegistrationException("Email is required");
        }

        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new RegistrationException("Password is required");
        }

        if (request.getPassword().length() < 8) {
            throw new RegistrationException("Password must be at least 8 characters");
        }

        if (request.getConfirmPassword() == null || request.getConfirmPassword().isBlank()) {
            throw new RegistrationException("Confirm password is required");
        }

        if (!request.getConfirmPassword().equals(request.getPassword())) {
            throw new RegistrationException("Passwords do not match");
        }
    }
}
