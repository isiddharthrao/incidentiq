package com.incidentmgmt.dto;

/**
 * Body of the JSON POST from the create-form's "Suggest with AI" button.
 */
public record AiSuggestionRequestDto(String title, String description) {
}
