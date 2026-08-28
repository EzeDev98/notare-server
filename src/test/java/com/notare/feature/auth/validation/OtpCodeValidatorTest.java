package com.notare.feature.auth.validation;

import com.notare.config.OtpProperties;
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

/**
 * Step 2a of the coverage roadmap.
 *
 * <p>Covers {@link OtpCodeValidator#isValid}: exact-length, all-digits
 * validation against the CONFIGURED length (not a hardcoded 6), and that an
 * invalid value disables the default violation and builds the dynamic
 * "Code must be N digits." message instead.
 *
 * <p>Also documents one thing worth knowing rather than assuming:
 * {@code Character::isDigit} is Unicode-aware, not ASCII-only. A run of
 * Arabic-Indic digits (e.g. "١٢٣٤٥٦") is 6
 * characters and every one of them satisfies {@code Character.isDigit},
 * so this validator currently accepts it as a "valid" 6-digit code. That
 * is very unlikely to be what OTP verification downstream expects when it
 * compares the value or parses it as a plain decimal integer. Not something
 * this test fixes - {@link #isValid_unicodeDecimalDigitsThatAreNotAsciiCurrentlyPass()}
 * pins down the current behavior so it's a deliberate, visible fact rather
 * than a surprise found in production.
 */
@ExtendWith(MockitoExtension.class)
class OtpCodeValidatorTest {

    @Mock
    private ConstraintValidatorContext context;

    @Mock
    private ConstraintValidatorContext.ConstraintViolationBuilder violationBuilder;

    private OtpProperties otpProperties;
    private OtpCodeValidator validator;

    @BeforeEach
    void setUp() {
        otpProperties = new OtpProperties();
        otpProperties.setLength(6);
        validator = new OtpCodeValidator(otpProperties);

        // Stubbed leniently: only the invalid-value tests below actually reach
        // this call (production code only touches the context when the value
        // fails validation), but declaring it once here — rather than
        // per-test — keeps every "isInvalid" test from repeating the same
        // three lines. Without lenient(), MockitoExtension's strict stubbing
        // would fail the valid-value tests that never touch context at all.
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
        // Same length as a real code, but spaces aren't digits.
        assertThat(validator.isValid("      ", context)).isFalse();
    }

    @Test
    void isValid_leadingMinusSignAtTheCorrectLengthIsInvalid() {
        assertThat(validator.isValid("-12345", context)).isFalse();
    }

    @Test
    void isValid_unicodeDecimalDigitsThatAreNotAsciiCurrentlyPass() {
        // See the class-level note: Character.isDigit is Unicode-aware.
        // This is documenting real, current behavior - not asserting it's
        // desirable.
        String arabicIndicSix = "١٢٣٤٥٦";
        assertThat(arabicIndicSix).hasSize(6);

        assertThat(validator.isValid(arabicIndicSix, context)).isTrue();
    }

    @Test
    void isValid_usesTheConfiguredLengthNotAHardcodedSix() {
        otpProperties.setLength(4);

        assertThat(validator.isValid("1234", context)).isTrue();
        assertThat(validator.isValid("123456", context)).isFalse();
    }

    @Test
    void isValid_invalidValueDisablesDefaultViolationAndBuildsTheConfiguredLengthMessage() {
        when(context.buildConstraintViolationWithTemplate(anyString())).thenReturn(violationBuilder);

        boolean result = validator.isValid("abc", context);

        assertThat(result).isFalse();
        verify(context).disableDefaultConstraintViolation();
        verify(context).buildConstraintViolationWithTemplate("Code must be 6 digits.");
        verify(violationBuilder).addConstraintViolation();
    }

    @Test
    void isValid_violationMessageReflectsANonDefaultConfiguredLength() {
        otpProperties.setLength(4);
        when(context.buildConstraintViolationWithTemplate(anyString())).thenReturn(violationBuilder);

        validator.isValid("12345", context); // 5 digits, configured length is 4

        verify(context).buildConstraintViolationWithTemplate("Code must be 4 digits.");
    }

    @Test
    void isValid_nullValueBuildsAViolationTheSameWayAnyOtherInvalidValueDoes() {
        // Sanity check that the null branch goes through the same
        // context-population path as every other invalid case, rather than
        // short-circuiting past it (the "&&" chain means value.length() is
        // never reached for a null value - only the first condition fails).
        when(context.buildConstraintViolationWithTemplate(anyString())).thenReturn(violationBuilder);

        assertThat(validator.isValid(null, context)).isFalse();

        verify(context).disableDefaultConstraintViolation();
    }
}