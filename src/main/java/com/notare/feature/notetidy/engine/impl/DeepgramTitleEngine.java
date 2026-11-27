package com.notare.feature.notetidy.engine.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.notare.feature.notetidy.engine.TitleEngine;
import com.notare.feature.transcription.engine.impl.DeepgramProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/** Infers a note's title from Deepgram's summary and topics. */
@Component
@ConditionalOnProperty(prefix = "notare.tidy", name = "engine", havingValue = "deepgram")
public class DeepgramTitleEngine implements TitleEngine {

    private static final int MAX_TITLE_CHARS = 60;

    private static final List<Pattern> FILLER_OPENERS = List.of(
            Pattern.compile("^the speaker (?:discusses|talks about|describes|explains|mentions)\\s+", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^the (?:text|note|document|passage) (?:is about|discusses|describes|covers|outlines)\\s+", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^this (?:text|note|document) (?:is about|discusses|describes|covers|outlines)\\s+", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^the author (?:discusses|describes|explains|mentions)\\s+", Pattern.CASE_INSENSITIVE));

    private static final Pattern SENTENCE_END = Pattern.compile("(?<=[.!?])\\s+");

    private static final Pattern CLAUSE_END = Pattern.compile("\\s*(?:[,;:]|\\s[-\u2013\u2014]\\s)");

    private static final int MAX_TOPICS = 3;
    private static final int MAX_CLAUSE_WORDS = 8;

    private final RestClient restClient;
    private final DeepgramProperties deepgram;

    public DeepgramTitleEngine(DeepgramProperties deepgram) {
        if (deepgram.getApiKey() == null || deepgram.getApiKey().isBlank()) {
            throw new IllegalStateException(
                    "notare.transcription.deepgram.api-key must be set when notare.tidy.engine=deepgram");
        }
        this.deepgram = deepgram;

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(30));

        this.restClient = RestClient.builder()
                .baseUrl(deepgram.getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public Optional<String> inferTitle(String text) {
        JsonNode body = restClient.post()
                .uri(uri -> uri.path("/v1/read")
                        .queryParam("language", "en")
                        .queryParam("summarize", true)
                        .queryParam("topics", true)
                        .queryParam("mip_opt_out", deepgram.isMipOptOut())
                        .build())
                .header(HttpHeaders.AUTHORIZATION, "Token " + deepgram.getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("text", text))
                .retrieve()
                .body(JsonNode.class);
        if (body == null) {
            return Optional.empty();
        }

        JsonNode results = body.path("results");
        String sentence = firstSentence(results.path("summary").path("text").asText(""));

        if (words(sentence) >= 2 && sentence.length() <= MAX_TITLE_CHARS) {
            return Optional.of(tidy(sentence));
        }

        String topics = joinTopics(topTopics(results));
        if (!topics.isEmpty()) {
            return Optional.of(tidy(topics));
        }

        String clause = CLAUSE_END.split(sentence, 2)[0].strip();
        if (words(clause) >= 2 && words(clause) <= MAX_CLAUSE_WORDS && clause.length() <= MAX_TITLE_CHARS) {
            return Optional.of(tidy(clause));
        }
        return Optional.empty();
    }

    private static String firstSentence(String summary) {
        String sentence = SENTENCE_END.split(summary.strip(), 2)[0];
        for (Pattern opener : FILLER_OPENERS) {
            sentence = opener.matcher(sentence).replaceFirst("");
        }
        return sentence.strip();
    }

    private static List<String> topTopics(JsonNode results) {
        List<JsonNode> all = new ArrayList<>();
        for (JsonNode segment : results.path("topics").path("segments")) {
            segment.path("topics").forEach(all::add);
        }
        all.sort((a, b) -> Double.compare(
                b.path("confidence_score").asDouble(0), a.path("confidence_score").asDouble(0)));

        List<String> topics = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        for (JsonNode topic : all) {
            String name = topic.path("topic").asText("").strip();
            String key = name.toLowerCase(Locale.ROOT);
            if (!name.isEmpty() && !seen.contains(key)) {
                seen.add(key);
                topics.add(name);
            }
        }
        return topics;
    }

    private static String joinTopics(List<String> topics) {
        List<String> used = new ArrayList<>();
        for (String topic : topics) {
            if (used.size() == MAX_TOPICS) {
                break;
            }
            List<String> next = new ArrayList<>(used);
            next.add(topic);
            if (join(next).length() > MAX_TITLE_CHARS) {
                break;
            }
            used = next;
        }
        return join(used);
    }

    private static String join(List<String> parts) {
        if (parts.size() < 2) {
            return parts.isEmpty() ? "" : parts.get(0);
        }
        return String.join(", ", parts.subList(0, parts.size() - 1)) + " and " + parts.get(parts.size() - 1);
    }

    private static int words(String text) {
        return text.isBlank() ? 0 : text.strip().split("\\s+").length;
    }

    private static String tidy(String title) {
        String trimmed = title.strip().replaceAll("[\\s.,;:!?]+$", "");
        return trimmed.isEmpty() ? "" : Character.toUpperCase(trimmed.charAt(0)) + trimmed.substring(1);
    }
}
