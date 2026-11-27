package com.notare.feature.notetidy.engine;

/** Fixes spelling, casing, punctuation and typography without rewording. */
public interface TextCorrector {

    CorrectionResult correct(String text);
}
