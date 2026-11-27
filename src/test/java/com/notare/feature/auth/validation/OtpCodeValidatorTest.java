package com.notare.feature.auth.validation;

import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Tests for OtpCodeValidator. */
@ExtendWith(MockitoExtension.class)
class OtpCodeValidatorTest {

    @Mock
    private ConstraintValidatorContext context;

    @Mock
    private ConstraintValidatorContext.ConstraintViolationBuilder violationBuilder;

    private OtpCodeValidator validator;

    @BeforeEach
    void setUp() {
        validator = new OtpCodeValidator();

        lenient().when(context.buildConstraintViolationWithTemplate(anyString()))
                .thenReturn(violationBuilder);
    }

    @Test
    void isValid_correctLengthAllDigitsIsValid() {
        assertThat(validator.isValid("123456", context)).isTrue();
    }

    @Test
    void isValid_validValueNeverTouchesTheConstraintContext() {
        validator.isValid("123456", context);

        verifyNoInteractions(context);
    }

    @Test
    void isValid_nullValueIsInvalid() {
        assertThat(validator.isValid(null, context)).isFalse();
    }

    @Test
    void isValid_tooShortIsInvalid() {
        assertThat(validator.isValid("12345", context)).isFalse();
    }

    @Test
    void isValid_tooLongIsInvalid() {
        assertThat(validator.isValid("1234567", context)).isFalse();
    }

    @Test
    void isValid_containsANonDigitCharacterIsInvalid() {
        assertThat(validator.isValid("12a456", context)).isFalse();
    }

    @Test
    void isValid_emptyStringIsInvalid() {
        assertThat(validator.isValid("", context)).isFalse();
    }

    @Test
    void isValid_whitespaceOfTheCorrectLengthIsStillInvalid() {
        assertThat(validator.isValid("      ", context)).isFalse();
    }

    @Test
    void isValid_leadingMinusSignAtTheCorrectLengthIsInvalid() {
        assertThat(validator.isValid("-12345", context)).isFalse();
    }

    @Test
    void isValid_unicodeDecimalDigitsThatAreNotAsciiCurrentlyPass() {
        String arabicIndicSix = "١٢٣٤٥٦";
        assertThat(arabicIndicSix).hasSize(6);

        assertThat(validator.isValid(arabicIndicSix, context)).isTrue();
    }

    @Test
    void isValid_requiresExactlySixDigits() {
        assertThat(validator.isValid("123456", context)).isTrue();
        assertThat(validator.isValid("1234", context)).isFalse();
        assertThat(validator.isValid("1234567", context)).isFalse();
    }

    @Test
    void isValid_invalidValueDisablesDefaultViolationAndBuildsTheLengthMessage() {
        when(context.buildConstraintViolationWithTemplate(anyString())).thenReturn(violationBuilder);

        boolean result = validator.isValid("abc", context);

        assertThat(result).isFalse();
        verify(context).disableDefaultConstraintViolation();
        verify(context).buildConstraintViolationWithTemplate("Code must be 6 digits.");
        verify(violationBuilder).addConstraintViolation();
    }

    @Test
    void isValid_nullValueBuildsAViolationTheSameWayAnyOtherInvalidValueDoes() {
        when(context.buildConstraintViolationWithTemplate(anyString())).thenReturn(violationBuilder);

        assertThat(validator.isValid(null, context)).isFalse();

        verify(context).disableDefaultConstraintViolation();
    }
}
