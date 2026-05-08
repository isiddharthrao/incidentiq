package com.incidentmgmt.dto;

/**
 * Lightweight projection used to feed the resolution-suggestion prompt without
 * dragging the full Incident entity (and its lazy collections) into the AI layer.
 */
public record SimilarIncident(Long id, String title, String resolution) {
}
