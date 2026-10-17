package com.notare.feature.note.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.factory.annotation.Value;

import java.util.Locale;

/**
 * The cap is a server limit against abuse, much larger than the product limit. The product limit
 * and its counter live in the client (step 17j, B-19).
 */
public class MaxNoteLengthValidator implements ConstraintValidator<MaxNoteLength, CharSequence> {

    @Value("${notare.note.max-content-length}")
    private int maxLength;

    @Override
    public boolean isValid(CharSequence value, ConstraintValidatorContext context) {
        if (value == null || value.length() <= maxLength) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(
                String.format(Locale.ENGLISH, "Content must not exceed %,d characters.", maxLength))
                .addConstraintViolation();
        return false;
    }
}
