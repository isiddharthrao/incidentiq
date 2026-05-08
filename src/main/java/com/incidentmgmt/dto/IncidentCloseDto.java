package com.incidentmgmt.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class IncidentCloseDto {

    @NotBlank(message = "Resolution notes are required to close an incident.")
    @Size(max = 5000, message = "Resolution notes can be at most 5000 characters.")
    private String resolutionNotes;
}
