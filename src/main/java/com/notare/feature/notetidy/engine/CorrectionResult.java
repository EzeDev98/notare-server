package com.notare.feature.notetidy.engine;

/**
 * What a corrector returns.
 *
 * @param languageCode the detected language, for example "en-US"
 * @param text         the corrected text; equal to the input when nothing was fixed
 */
public record CorrectionResult(String languageCode, String text) {

    public boolean isEnglish() {
        return languageCode != null && languageCode.startsWith("en");
    }
}
