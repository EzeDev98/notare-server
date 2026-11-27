package com.notare.feature.notetidy.engine;

/**
 * Fixes spelling, casing, punctuation and typography. Never rewords, adds or
 * removes content.
 */
public interface TextCorrector {

    CorrectionResult correct(String text);
}
