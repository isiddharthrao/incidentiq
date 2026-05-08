package com.incidentmgmt.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CommentDto {

    @NotBlank(message = "Comment cannot be empty.")
    @Size(max = 5000, message = "Comment can be at most 5000 characters.")
    private String text;
}
