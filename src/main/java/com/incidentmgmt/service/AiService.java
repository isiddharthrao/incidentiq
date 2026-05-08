package com.incidentmgmt.service;

import com.incidentmgmt.ai.AiResponseParser;
import com.incidentmgmt.ai.GeminiClient;
import com.incidentmgmt.ai.PromptTemplates;
import com.incidentmgmt.dto.SimilarIncident;
import com.incidentmgmt.entity.Category;
import com.incidentmgmt.entity.Incident;
import com.incidentmgmt.entity.Priority;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * High-level AI features. Each method:
 *   - builds a prompt from PromptTemplates
 *   - delegates to GeminiClient (which logs + handles failures)
 *   - parses the response when the feature returns a structured value
 *
 * Returning Optional makes the contract explicit: every caller MUST decide what
 * to do when AI is unavailable. There is no exception path — degradation is
 * by design, not by accident.
 */
@Service
@RequiredArgsConstructor
public class AiService {

    private final GeminiClient geminiClient;
    private final PromptTemplates prompts;
    private final AiResponseParser parser;

    public boolean isAvailable() {
        return geminiClient.isAvailable();
    }

    public Optional<Category> suggestCategory(String title, String description) {
        return geminiClient.ask("AUTO_CATEGORIZE", prompts.categorize(title, description))
                .flatMap(parser::parseCategory);
    }

    public Optional<Priority> suggestPriority(String title, String description) {
        return geminiClient.ask("AUTO_PRIORITY", prompts.prioritize(title, description))
                .flatMap(parser::parsePriority);
    }

    public Optional<String> suggestResolution(String title, String description, List<SimilarIncident> similar) {
        return geminiClient.ask("RESOLUTION_SUGGEST", prompts.suggestResolution(title, description, similar));
    }

    public Optional<String> summarizeThread(Incident incident) {
        return geminiClient.ask("THREAD_SUMMARY", prompts.summarizeThread(incident));
    }
}
