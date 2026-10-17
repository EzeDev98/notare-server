package com.notare.feature.note.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Note text may not exceed notare.note.max-content-length (step 17j, B-19). One limit for every
 * request that carries note content; @Size could not be used because an annotation attribute
 * cannot read application.properties.
 */
@Documented
@Constraint(validatedBy = MaxNoteLengthValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface MaxNoteLength {

    String message() default "Content is too long.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
