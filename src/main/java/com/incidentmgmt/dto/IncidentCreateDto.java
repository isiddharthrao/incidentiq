package com.incidentmgmt.dto;

import com.incidentmgmt.entity.Category;
import com.incidentmgmt.entity.Priority;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class IncidentCreateDto {

    @NotBlank(message = "Title is required.")
    @Size(max = 200, message = "Title can be at most 200 characters.")
    private String title;

    @NotBlank(message = "Description is required.")
    @Size(max = 5000, message = "Description can be at most 5000 characters.")
    private String description;

    @NotNull(message = "Category is required.")
    private Category category;

    @NotNull(message = "Priority is required.")
    private Priority priority;

    /**
     * What the AI initially suggested (set by the create form's hidden inputs
     * after the user clicks "Suggest with AI"). Stored alongside the final
     * value so analytics can show how often users overrode the AI.
     * Null when the user didn't use AI suggestion at all.
     */
    private Category categoryAiSuggestion;

    private Priority priorityAiSuggestion;
}
