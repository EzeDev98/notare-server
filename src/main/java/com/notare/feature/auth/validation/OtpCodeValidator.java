package com.notare.feature.auth.validation;

import com.notare.config.OtpProperties;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OtpCodeValidator implements ConstraintValidator<ValidOtpCode, String> {

    private final OtpProperties otpProperties;

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {

        int expectedLength = otpProperties.getLength();

        boolean valid = value != null
                && value.length() == expectedLength
                && value.chars().allMatch(Character::isDigit);

        if (!valid) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("Code must be " + expectedLength + " digits.")
                    .addConstraintViolation();
        }

        return valid;
    }
}