package com.notare.feature.auth.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validates the 6-digit code internal-auth emails for a password reset.
 * The length used to come from notare's own OTP config; that config is gone
 * along with notare-issued OTPs, so the length is fixed here.
 */
public class OtpCodeValidator implements ConstraintValidator<ValidOtpCode, String> {

    private static final int CODE_LENGTH = 6;

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        boolean valid = value != null && value.length() == CODE_LENGTH && value.chars().allMatch(Character::isDigit);

        if (!valid) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("Code must be " + CODE_LENGTH + " digits.")
                    .addConstraintViolation();
        }

        return valid;
    }
}
