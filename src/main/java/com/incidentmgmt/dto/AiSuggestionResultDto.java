package com.incidentmgmt.dto;

/**
 * JSON response for /incidents/ai/suggest. Either field may be null when the AI
 * call failed or when the model returned a token we couldn't parse to an enum.
 */
public record AiSuggestionResultDto(String category, String priority) {
}
