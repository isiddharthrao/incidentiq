package com.incidentmgmt.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.incidentmgmt.entity.AiCallLog;
import com.incidentmgmt.repository.AiCallLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;

/**
 * Thin wrapper around the Gemini REST API.
 *
 * Endpoint: POST https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={api_key}
 *
 * - All Gemini calls go through here.
 * - Every call writes a row to ai_call_log (success or failure) so the audit
 *   trail is the demonstrable proof that AI is being invoked.
 * - Catches every exception. Callers receive Optional.empty() when AI is down,
 *   so the user-facing flow degrades gracefully instead of breaking.
 */
@Component
@Slf4j
public class GeminiClient {

    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models";

    private final RestClient restClient;
    private final AiCallLogRepository aiCallLogRepository;
    private final String apiKey;
    private final String model;
    private final Double temperature;
    private final boolean configured;

    public GeminiClient(@Value("${app.gemini.api-key:}") String apiKey,
                        @Value("${app.gemini.model:gemini-2.0-flash}") String model,
                        @Value("${app.gemini.temperature:0.3}") Double temperature,
                        AiCallLogRepository aiCallLogRepository) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model;
        this.temperature = temperature;
        this.aiCallLogRepository = aiCallLogRepository;
        this.configured = !this.apiKey.isBlank() && !"CHANGE_ME".equals(this.apiKey);

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(30_000);

        this.restClient = RestClient.builder()
                .baseUrl(BASE_URL)
                .requestFactory(factory)
                .build();

        if (configured) {
            String preview = this.apiKey.length() >= 4 ? this.apiKey.substring(0, 4) + "…" : "(too short)";
            log.info("GeminiClient initialised — model={}, key prefix='{}'", model, preview);
        } else {
            log.warn("GeminiClient: app.gemini.api-key is not set — AI features will return empty responses.");
        }
    }

    public boolean isAvailable() {
        return configured;
    }

    /**
     * Sends one prompt to Gemini.
     *
     * @param callType short tag stored on AiCallLog (e.g. AUTO_CATEGORIZE, PING)
     * @param prompt   full prompt text
     * @return text response, trimmed; or empty if the call failed or AI is unconfigured
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<String> ask(String callType, String prompt) {
        long start = System.currentTimeMillis();
        AiCallLog logEntry = AiCallLog.builder()
                .callType(callType)
                .promptSummary(truncate(prompt, 500))
                .success(false)
                .build();

        if (!configured) {
            logEntry.setErrorMsg("API key not configured");
            logEntry.setLatencyMs(0L);
            aiCallLogRepository.save(logEntry);
            return Optional.empty();
        }

        try {
            GeminiRequest body = new GeminiRequest(
                    List.of(new Content(List.of(new Part(prompt)))),
                    new GenerationConfig(temperature)
            );

            GeminiResponse response = restClient.post()
                    .uri("/{model}:generateContent?key={key}", model, apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(GeminiResponse.class);

            long elapsed = System.currentTimeMillis() - start;
            String text = extractText(response);

            if (text == null || text.isBlank()) {
                logEntry.setLatencyMs(elapsed);
                logEntry.setErrorMsg("Empty response (possibly safety-filtered)");
                aiCallLogRepository.save(logEntry);
                log.warn("Gemini call {} returned empty content after {}ms", callType, elapsed);
                return Optional.empty();
            }

            logEntry.setLatencyMs(elapsed);
            logEntry.setSuccess(true);
            logEntry.setResponseSummary(truncate(text, 500));
            aiCallLogRepository.save(logEntry);
            log.debug("Gemini call {} ok in {}ms", callType, elapsed);
            return Optional.of(text.trim());
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            logEntry.setLatencyMs(elapsed);
            logEntry.setErrorMsg(truncate(e.getMessage(), 500));
            aiCallLogRepository.save(logEntry);
            log.warn("Gemini call {} failed after {}ms: {}", callType, elapsed, e.getMessage());
            return Optional.empty();
        }
    }

    private String extractText(GeminiResponse response) {
        if (response == null || response.candidates() == null || response.candidates().isEmpty()) {
            return null;
        }
        Candidate first = response.candidates().get(0);
        if (first == null || first.content() == null || first.content().parts() == null
                || first.content().parts().isEmpty()) {
            return null;
        }
        return first.content().parts().get(0).text();
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    // --- Gemini wire format (records auto-handled by Jackson in Spring Boot 3) ---

    public record GeminiRequest(List<Content> contents, GenerationConfig generationConfig) {}

    public record Content(List<Part> parts) {}

    public record Part(String text) {}

    public record GenerationConfig(Double temperature) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GeminiResponse(List<Candidate> candidates) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Candidate(Content content) {}
}
