package com.notare.feature.notetidy.engine.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.notare.feature.notetidy.config.NoteTidyProperties;
import com.notare.feature.notetidy.engine.CorrectionResult;
import com.notare.feature.notetidy.engine.TextCorrector;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Corrections from a self-hosted LanguageTool (POST /v2/check), so note text
 * never leaves the server's own network.
 *
 * <p>Only spelling, casing, punctuation and typography are applied, and only
 * when the rule offers exactly one replacement. Grammar and style suggestions
 * reword text, so they are never applied. Code spans, URLs and email addresses
 * are left as written.
 */
@Component
@ConditionalOnProperty(prefix = "notare.tidy", name = "corrector", havingValue = "languagetool")
public class LanguageToolCorrector implements TextCorrector {

    private static final Set<String> AUTO_APPLY_CATEGORIES = Set.of("TYPOS", "CASING", "PUNCTUATION", "TYPOGRAPHY");

    private static final Pattern PROTECTED = Pattern.compile("`[^`]*`|https?://\\S+|www\\.\\S+|\\S+@\\S+\\.\\S+");

    private final RestClient restClient;

    public LanguageToolCorrector(NoteTidyProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(30));

        this.restClient = RestClient.builder()
                .baseUrl(properties.getLanguagetool().getUrl())
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public CorrectionResult correct(String text) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("text", text);
        form.add("language", "auto");
        form.add("preferredVariants", "en-US");

        JsonNode body = restClient.post()
                .uri("/v2/check")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(JsonNode.class);
        if (body == null) {
            throw new IllegalStateException("LanguageTool returned an empty response.");
        }

        String language = body.path("language").path("detectedLanguage").path("code").asText("");
        if (!language.startsWith("en")) {
            return new CorrectionResult(language, text);
        }

        List<int[]> protectedRanges = protectedRanges(text);
        List<Fix> fixes = new ArrayList<>();
        for (JsonNode match : body.path("matches")) {
            String category = match.path("rule").path("category").path("id").asText("");
            JsonNode replacements = match.path("replacements");
            if (!AUTO_APPLY_CATEGORIES.contains(category) || replacements.size() != 1) {
                continue;
            }
            int offset = match.path("offset").asInt(-1);
            int length = match.path("length").asInt(-1);
            if (offset < 0 || length < 0 || offset + length > text.length()) {
                continue;
            }
            if (overlaps(offset, length, protectedRanges)) {
                continue;
            }
            fixes.add(new Fix(offset, length, replacements.get(0).path("value").asText()));
        }

        // End to start, so each offset is still valid when it is applied.
        fixes.sort(Comparator.comparingInt(Fix::offset).reversed());
        StringBuilder corrected = new StringBuilder(text);
        int appliedFrom = Integer.MAX_VALUE;
        for (Fix fix : fixes) {
            if (fix.offset() + fix.length() > appliedFrom) {
                continue; // overlaps a fix already applied
            }
            corrected.replace(fix.offset(), fix.offset() + fix.length(), fix.value());
            appliedFrom = fix.offset();
        }
        return new CorrectionResult(language, corrected.toString());
    }

    private static List<int[]> protectedRanges(String text) {
        List<int[]> ranges = new ArrayList<>();
        Matcher matcher = PROTECTED.matcher(text);
        while (matcher.find()) {
            ranges.add(new int[]{matcher.start(), matcher.end()});
        }
        return ranges;
    }

    private static boolean overlaps(int offset, int length, List<int[]> ranges) {
        for (int[] range : ranges) {
            if (range[0] < offset + length && offset < range[1]) {
                return true;
            }
        }
        return false;
    }

    private record Fix(int offset, int length, String value) {
    }
}
