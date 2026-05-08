package com.incidentmgmt.service;

import com.incidentmgmt.dto.UserCreateDto;
import com.incidentmgmt.dto.UserEditDto;
import com.incidentmgmt.entity.Role;
import com.incidentmgmt.entity.User;
import com.incidentmgmt.exception.DuplicateUsernameException;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.incidentmgmt.repository.UserRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public List<User> findAll() {
        return userRepository.findAll(Sort.by("username"));
    }

    @Transactional(readOnly = true)
    public List<User> findAssignable() {
        return userRepository.findByRoleInAndEnabledTrueOrderByUsername(
                List.of(Role.ENGINEER, Role.ADMIN));
    }

    @Transactional(readOnly = true)
    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + id));
    }

    public User create(UserCreateDto dto) {
        if (userRepository.existsByUsername(dto.getUsername())) {
            throw new DuplicateUsernameException(dto.getUsername());
        }
        User user = User.builder()
                .username(dto.getUsername())
                .password(passwordEncoder.encode(dto.getPassword()))
                .role(dto.getRole())
                .fullName(dto.getFullName())
                .email(dto.getEmail())
                .enabled(dto.isEnabled())
                .build();
        return userRepository.save(user);
    }

    public User update(Long id, UserEditDto dto) {
        User user = findById(id);
        boolean usernameChanged = !user.getUsername().equals(dto.getUsername());
        if (usernameChanged && userRepository.existsByUsername(dto.getUsername())) {
            throw new DuplicateUsernameException(dto.getUsername());
        }
        user.setUsername(dto.getUsername());
        user.setRole(dto.getRole());
        user.setFullName(dto.getFullName());
        user.setEmail(dto.getEmail());
        user.setEnabled(dto.isEnabled());
        if (StringUtils.hasText(dto.getPassword())) {
            if (dto.getPassword().length() < 6 || dto.getPassword().length() > 50) {
                throw new IllegalArgumentException("Password must be 6-50 characters.");
            }
            user.setPassword(passwordEncoder.encode(dto.getPassword()));
        }
        return user;
    }

    public void delete(Long id, String currentUsername) {
        User user = findById(id);
        if (user.getUsername().equals(currentUsername)) {
            throw new IllegalStateException("You cannot delete your own account.");
        }
        if (user.getRole() == Role.ADMIN && userRepository.countByRole(Role.ADMIN) <= 1) {
            throw new IllegalStateException("Cannot delete the last administrator.");
        }
        userRepository.delete(user);
    }
}
