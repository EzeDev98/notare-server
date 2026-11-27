package com.notare.feature.transcription.engine.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.notare.feature.transcription.dto.response.TranscriptionResult;
import com.notare.feature.transcription.engine.TranscriptionEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.time.Duration;

/** Speech-to-text through Deepgram. */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "notare.transcription", name = "engine", havingValue = "deepgram")
public class DeepgramTranscriptionEngine implements TranscriptionEngine {

    private static final int MAX_ATTEMPTS = 3;
    private static final long[] BACKOFF_MILLIS = {1_000, 3_000};

    private final RestClient restClient;
    private final DeepgramProperties properties;

    public DeepgramTranscriptionEngine(DeepgramProperties properties) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new IllegalStateException(
                    "notare.transcription.deepgram.api-key must be set when notare.transcription.engine=deepgram");
        }
        this.properties = properties;

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(properties.getReadTimeoutSeconds()));

        this.restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public TranscriptionResult transcribe(Resource audio, String contentType) {
        byte[] bytes;
        try (InputStream in = audio.getInputStream()) {
            bytes = in.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Could not read the recording.", e);
        }

        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return callDeepgram(bytes, contentType);
            } catch (HttpClientErrorException e) {
                if (e.getStatusCode().value() != 429) {
                    throw new IllegalStateException(
                            "Transcription was rejected (HTTP " + e.getStatusCode().value() + ").", e);
                }
                lastFailure = e;
            } catch (HttpServerErrorException | ResourceAccessException e) {
                lastFailure = e;
            }

            if (attempt < MAX_ATTEMPTS) {
                log.warn("Deepgram attempt {} of {} failed, retrying", attempt, MAX_ATTEMPTS);
                sleep(BACKOFF_MILLIS[attempt - 1]);
            }
        }
        throw new IllegalStateException("The transcription service is unavailable. Please try again.", lastFailure);
    }

    private TranscriptionResult callDeepgram(byte[] bytes, String contentType) {
        MediaType mediaType = contentType == null || contentType.isBlank()
                ? MediaType.APPLICATION_OCTET_STREAM
                : MediaType.parseMediaType(contentType);

        JsonNode body = restClient.post()
                .uri(uri -> uri.path("/v1/listen")
                        .queryParam("model", properties.getModel())
                        .queryParam("smart_format", true)
                        .queryParam("detect_language", true)
                        .queryParam("mip_opt_out", properties.isMipOptOut())
                        .build())
                .header(HttpHeaders.AUTHORIZATION, "Token " + properties.getApiKey())
                .contentType(mediaType)
                .body(bytes)
                .retrieve()
                .body(JsonNode.class);

        if (body == null) {
            throw new IllegalStateException("The transcription service returned an empty response.");
        }

        JsonNode channel = body.path("results").path("channels").path(0);
        JsonNode alternative = channel.path("alternatives").path(0);

        String transcript = alternative.path("transcript").asText("");
        double confidence = alternative.path("confidence").asDouble(0.0);

        String language = channel.path("detected_language").asText(null);
        if (language == null) {
            language = body.path("metadata").path("detected_language").asText(null);
        }
        if (language != null && language.length() > 16) {
            language = language.substring(0, 16);
        }

        return new TranscriptionResult(transcript, confidence, language);
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
