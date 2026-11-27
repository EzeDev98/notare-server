package com.notare.feature.notetidy.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Settings for AI tidy. */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "notare.tidy")
public class NoteTidyProperties {

    private boolean enabled = false;

    private int idleMinutes = 10;

    private int batchSize = 20;

    private int maxAttempts = 3;

    private int minWordsForTitle = 15;

    private int dailyTitleCapPerUser = 50;

    private int maxChars = 20_000;

    private String engine = "stub";

    private String corrector = "stub";

    private Languagetool languagetool = new Languagetool();

    @Getter
    @Setter
    public static class Languagetool {

        private String url = "http://languagetool:8010";
    }
}
