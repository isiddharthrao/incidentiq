package com.incidentmgmt.ai;

import com.incidentmgmt.dto.SimilarIncident;
import com.incidentmgmt.entity.Category;
import com.incidentmgmt.entity.Incident;
import com.incidentmgmt.entity.IncidentUpdate;
import com.incidentmgmt.entity.Priority;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * All Gemini prompts live here. Single place to tune wording, version, or A/B-test.
 */
@Component
public class PromptTemplates {

    public String categorize(String title, String description) {
        String allowed = Arrays.stream(Category.values())
                .map(Enum::name)
                .collect(Collectors.joining(", "));
        return """
                You are an IT incident triage assistant. Pick the single best category for the incident below.

                Allowed categories (return EXACTLY one of these tokens, in uppercase, with no other text or punctuation):
                %s

                Title: %s

                Description:
                %s

                Category:
                """.formatted(allowed, title, description);
    }

    public String prioritize(String title, String description) {
        String allowed = Arrays.stream(Priority.values())
                .map(Enum::name)
                .collect(Collectors.joining(", "));
        return """
                You are an IT incident triage assistant. Assign one priority to the incident below.

                Severity guide:
                - P1: production outage, all/most users affected, security incident, data loss
                - P2: major feature broken, large user impact, urgent fix needed
                - P3: minor feature broken or degraded, small impact, can wait until business hours
                - P4: cosmetic, suggestion, no user impact

                Return EXACTLY one of: %s — and nothing else.

                Title: %s

                Description:
                %s

                Priority:
                """.formatted(allowed, title, description);
    }

    public String suggestResolution(String title, String description, List<SimilarIncident> similar) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are an experienced IT engineer. Suggest concrete resolution steps for the incident below.\n\n");
        sb.append("Incident:\n");
        sb.append("Title: ").append(title).append("\n");
        sb.append("Description:\n").append(description).append("\n\n");

        if (similar != null && !similar.isEmpty()) {
            sb.append("For reference, here are past incidents with similar wording and how they were resolved:\n\n");
            for (int i = 0; i < similar.size(); i++) {
                SimilarIncident s = similar.get(i);
                sb.append("--- Past incident ").append(i + 1).append(" ---\n");
                sb.append("Title: ").append(s.title()).append("\n");
                sb.append("Resolution: ").append(s.resolution() == null ? "(not recorded)" : s.resolution()).append("\n\n");
            }
        }

        sb.append("Provide 3-5 concrete, actionable resolution steps. Be specific. Plain text. No markdown headers.\n");
        return sb.toString();
    }

    public String summarizeThread(Incident incident) {
        StringBuilder sb = new StringBuilder();
        sb.append("Summarize the IT incident below in 3-4 sentences. Cover: what happened, what was investigated, the root cause, and how it was resolved.\n\n");
        sb.append("Title: ").append(incident.getTitle()).append("\n");
        sb.append("Description:\n").append(incident.getDescription()).append("\n\n");

        if (!incident.getUpdates().isEmpty()) {
            sb.append("Investigation thread (chronological):\n");
            for (IncidentUpdate u : incident.getUpdates()) {
                sb.append("- ").append(u.getAuthor().getUsername()).append(": ").append(u.getText()).append("\n");
            }
            sb.append("\n");
        }

        if (incident.getResolutionNotes() != null && !incident.getResolutionNotes().isBlank()) {
            sb.append("Resolution notes:\n").append(incident.getResolutionNotes()).append("\n\n");
        }

        sb.append("Summary (3-4 sentences, plain text, no preamble):\n");
        return sb.toString();
    }
}
