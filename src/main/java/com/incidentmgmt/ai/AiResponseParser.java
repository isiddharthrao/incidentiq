package com.incidentmgmt.ai;

import com.incidentmgmt.entity.Category;
import com.incidentmgmt.entity.Priority;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Parses Gemini text responses into our enums. The model occasionally adds
 * prose ("Category: APPLICATION.") so we substring-match instead of requiring
 * exact equality. If nothing matches, returns empty so the caller can fall back.
 */
@Component
public class AiResponseParser {

    public Optional<Category> parseCategory(String response) {
        if (response == null) {
            return Optional.empty();
        }
        String cleaned = response.trim().toUpperCase();
        for (Category c : Category.values()) {
            if (cleaned.contains(c.name())) {
                return Optional.of(c);
            }
        }
        return Optional.empty();
    }

    public Optional<Priority> parsePriority(String response) {
        if (response == null) {
            return Optional.empty();
        }
        String cleaned = response.trim().toUpperCase();
        for (Priority p : Priority.values()) {
            if (cleaned.contains(p.name())) {
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }
}
