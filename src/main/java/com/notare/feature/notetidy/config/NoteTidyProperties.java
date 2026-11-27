package com.notare.feature.notetidy.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Settings for AI tidy (notare.tidy.*). Ships off, with both engines on stub. */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "notare.tidy")
public class NoteTidyProperties {

    /** Master switch. When false the job does not exist. */
    private boolean enabled = false;

    /** How long a note must be left alone before it is picked. */
    private int idleMinutes = 10;

    /** Most notes picked per run. */
    private int batchSize = 20;

    /** Failed runs in a row before a note is left alone for good. */
    private int maxAttempts = 3;

    /** Below this a note is corrected but never given a title. */
    private int minWordsForTitle = 15;

    /** Most titles inferred per user per day, to cap the title engine's cost. */
    private int dailyTitleCapPerUser = 50;

    /** Longer notes are left alone: one LanguageTool request, and one title request, per note. */
    private int maxChars = 20_000;

    /** Title engine: stub or deepgram. */
    private String engine = "stub";

    /** Corrector: stub or languagetool. */
    private String corrector = "stub";

    private Languagetool languagetool = new Languagetool();

    @Getter
    @Setter
    public static class Languagetool {

        /** Self-hosted LanguageTool, internal network only. */
        private String url = "http://languagetool:8010";
    }
}
