package com.incidentmgmt.controller;

import com.incidentmgmt.ai.GeminiClient;
import com.incidentmgmt.repository.AiCallLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Optional;

/**
 * Admin-only AI diagnostics. Phase 6 ships only the connectivity ping;
 * phase 7 will add deeper triage tools.
 */
@Controller
@RequestMapping("/admin/ai")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AiAdminController {

    private final GeminiClient geminiClient;
    private final AiCallLogRepository aiCallLogRepository;

    @GetMapping("/ping")
    public String pingPage(Model model) {
        model.addAttribute("aiAvailable", geminiClient.isAvailable());
        model.addAttribute("recentCalls", aiCallLogRepository.findTop20ByOrderByCreatedAtDesc());
        return "admin/ai-ping";
    }

    @PostMapping("/ping")
    public String runPing(Model model) {
        Optional<String> response = geminiClient.ask(
                "PING",
                "Reply with exactly the words 'hello from gemini' and nothing else."
        );
        model.addAttribute("aiAvailable", geminiClient.isAvailable());
        model.addAttribute("pingResponse", response.orElse(null));
        model.addAttribute("pingFailed", response.isEmpty());
        model.addAttribute("recentCalls", aiCallLogRepository.findTop20ByOrderByCreatedAtDesc());
        return "admin/ai-ping";
    }
}
