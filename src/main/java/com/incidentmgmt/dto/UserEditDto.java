package com.incidentmgmt.dto;

import com.incidentmgmt.entity.Role;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UserEditDto {

    private Long id;

    @NotBlank(message = "Username is required.")
    @Size(min = 3, max = 50, message = "Username must be 3-50 characters.")
    @Pattern(regexp = "^[a-zA-Z0-9_.-]+$", message = "Username can only contain letters, numbers, '.', '_', '-'.")
    private String username;

    // Optional on edit: blank means leave password unchanged.
    // Length is validated in the service (only when non-blank).
    private String password;

    @NotNull(message = "Role is required.")
    private Role role;

    @Size(max = 100)
    private String fullName;

    @Email(message = "Email must be valid.")
    @Size(max = 120)
    private String email;

    private boolean enabled;
}
