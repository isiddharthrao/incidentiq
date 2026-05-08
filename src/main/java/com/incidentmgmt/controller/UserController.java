package com.incidentmgmt.controller;

import com.incidentmgmt.dto.UserCreateDto;
import com.incidentmgmt.dto.UserEditDto;
import com.incidentmgmt.entity.Role;
import com.incidentmgmt.entity.User;
import com.incidentmgmt.exception.DuplicateUsernameException;
import com.incidentmgmt.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

    private final UserService userService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("users", userService.findAll());
        return "user/list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("userForm", new UserCreateDto());
        model.addAttribute("roles", Role.values());
        model.addAttribute("editing", false);
        return "user/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("userForm") UserCreateDto form,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes ra) {
        if (bindingResult.hasErrors()) {
            return renderForm(model, false);
        }
        try {
            userService.create(form);
        } catch (DuplicateUsernameException e) {
            bindingResult.rejectValue("username", "duplicate", e.getMessage());
            return renderForm(model, false);
        }
        ra.addFlashAttribute("flash", "User created.");
        return "redirect:/users";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        User user = userService.findById(id);
        UserEditDto dto = new UserEditDto();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setRole(user.getRole());
        dto.setFullName(user.getFullName());
        dto.setEmail(user.getEmail());
        dto.setEnabled(user.isEnabled());
        // password intentionally left blank
        model.addAttribute("userForm", dto);
        model.addAttribute("roles", Role.values());
        model.addAttribute("editing", true);
        return "user/form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("userForm") UserEditDto form,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes ra) {
        if (bindingResult.hasErrors()) {
            return renderForm(model, true);
        }
        try {
            userService.update(id, form);
        } catch (DuplicateUsernameException e) {
            bindingResult.rejectValue("username", "duplicate", e.getMessage());
            return renderForm(model, true);
        } catch (IllegalArgumentException e) {
            bindingResult.rejectValue("password", "invalid", e.getMessage());
            return renderForm(model, true);
        }
        ra.addFlashAttribute("flash", "User updated.");
        return "redirect:/users";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, Authentication auth, RedirectAttributes ra) {
        try {
            userService.delete(id, auth.getName());
            ra.addFlashAttribute("flash", "User deleted.");
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("flashError", e.getMessage());
        }
        return "redirect:/users";
    }

    private String renderForm(Model model, boolean editing) {
        model.addAttribute("roles", Role.values());
        model.addAttribute("editing", editing);
        return "user/form";
    }
}
