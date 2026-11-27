package com.notare.feature.notetidy.engine;

/** A corrector's result: the detected language and the corrected text. */
public record CorrectionResult(String languageCode, String text) {

    public boolean isEnglish() {
        return languageCode != null && languageCode.startsWith("en");
    }
}
