package com.incidentmgmt.controller;

import com.incidentmgmt.config.CustomUserDetails;
import com.incidentmgmt.dto.AiSuggestionRequestDto;
import com.incidentmgmt.dto.AiSuggestionResultDto;
import com.incidentmgmt.dto.CommentDto;
import com.incidentmgmt.dto.IncidentCloseDto;
import com.incidentmgmt.dto.IncidentCreateDto;
import com.incidentmgmt.dto.SimilarIncident;
import com.incidentmgmt.entity.Category;
import com.incidentmgmt.entity.Incident;
import com.incidentmgmt.entity.IncidentStatus;
import com.incidentmgmt.entity.Priority;
import com.incidentmgmt.entity.Role;
import com.incidentmgmt.service.AiService;
import com.incidentmgmt.service.IncidentService;
import com.incidentmgmt.service.PdfService;
import com.incidentmgmt.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/incidents")
@RequiredArgsConstructor
public class IncidentController {

    private final IncidentService incidentService;
    private final UserService userService;
    private final AiService aiService;
    private final PdfService pdfService;

    @GetMapping
    public String list(@AuthenticationPrincipal CustomUserDetails current, Model model) {
        model.addAttribute("incidents", incidentService.findVisibleTo(current));
        return "incident/list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("incidentForm", new IncidentCreateDto());
        addCreateFormReferenceData(model);
        return "incident/form";
    }

    @PostMapping
    public String create(@AuthenticationPrincipal CustomUserDetails current,
                         @Valid @ModelAttribute("incidentForm") IncidentCreateDto form,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes ra) {
        if (bindingResult.hasErrors()) {
            addCreateFormReferenceData(model);
            return "incident/form";
        }
        Incident saved = incidentService.create(form, current.getId());
        ra.addFlashAttribute("flash", "Incident INC-" + saved.getId() + " created.");
        return "redirect:/incidents/" + saved.getId();
    }

    /**
     * AJAX endpoint hit from the create-form's "Suggest with AI" button.
     * Returns JSON with one or both fields populated. CSRF arrives via
     * X-CSRF-TOKEN header so the JSON body stays clean.
     */
    @PostMapping(value = "/ai/suggest", consumes = "application/json", produces = "application/json")
    @ResponseBody
    public AiSuggestionResultDto aiSuggest(@RequestBody AiSuggestionRequestDto req) {
        if (req == null || req.title() == null || req.description() == null
                || req.title().isBlank() || req.description().isBlank()) {
            return new AiSuggestionResultDto(null, null);
        }
        Optional<Category> cat = aiService.suggestCategory(req.title(), req.description());
        Optional<Priority> pri = aiService.suggestPriority(req.title(), req.description());
        return new AiSuggestionResultDto(
                cat.map(Enum::name).orElse(null),
                pri.map(Enum::name).orElse(null)
        );
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id,
                         @AuthenticationPrincipal CustomUserDetails current,
                         Model model) {
        Incident incident = incidentService.findVisibleDetail(id, current);
        model.addAttribute("incident", incident);
        if (!model.containsAttribute("commentForm")) {
            model.addAttribute("commentForm", new CommentDto());
        }
        if (current.getRole() != Role.REPORTER) {
            model.addAttribute("assignableUsers", userService.findAssignable());
            model.addAttribute("statuses", IncidentStatus.values());
            model.addAttribute("similarIncidents", incidentService.findSimilarForDisplay(incident));
        }
        return "incident/detail";
    }

    @PostMapping("/{id}/comment")
    public String addComment(@PathVariable Long id,
                             @AuthenticationPrincipal CustomUserDetails current,
                             @Valid @ModelAttribute("commentForm") CommentDto form,
                             BindingResult bindingResult,
                             RedirectAttributes ra) {
        incidentService.findVisibleDetail(id, current);

        if (bindingResult.hasErrors()) {
            ra.addFlashAttribute("flashError", bindingResult.getFieldError("text").getDefaultMessage());
            return "redirect:/incidents/" + id;
        }
        try {
            incidentService.addComment(id, current.getId(), form.getText());
            ra.addFlashAttribute("flash", "Comment added.");
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("flashError", e.getMessage());
        }
        return "redirect:/incidents/" + id;
    }

    @PostMapping("/{id}/assign")
    @PreAuthorize("hasAnyRole('ENGINEER','ADMIN')")
    public String assign(@PathVariable Long id,
                         @RequestParam(required = false, name = "assigneeId") String assigneeIdRaw,
                         RedirectAttributes ra) {
        Long assigneeId = (assigneeIdRaw == null || assigneeIdRaw.isBlank())
                ? null
                : Long.parseLong(assigneeIdRaw);
        try {
            incidentService.assign(id, assigneeId);
            ra.addFlashAttribute("flash", assigneeId == null ? "Incident unassigned." : "Incident assigned.");
        } catch (IllegalStateException | IllegalArgumentException e) {
            ra.addFlashAttribute("flashError", e.getMessage());
        }
        return "redirect:/incidents/" + id;
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ENGINEER','ADMIN')")
    public String changeStatus(@PathVariable Long id,
                               @RequestParam IncidentStatus status,
                               RedirectAttributes ra) {
        try {
            incidentService.changeStatus(id, status);
            ra.addFlashAttribute("flash", "Status updated to " + status + ".");
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("flashError", e.getMessage());
        }
        return "redirect:/incidents/" + id;
    }

    /**
     * Sync AI call. Loads similar past incidents (top 3 with non-blank
     * resolutions), feeds them to Gemini as RAG context, redirects back to
     * detail with the suggestion in a flash attribute.
     */
    @PostMapping("/{id}/suggest-resolution")
    @PreAuthorize("hasAnyRole('ENGINEER','ADMIN')")
    public String suggestResolution(@PathVariable Long id,
                                    @AuthenticationPrincipal CustomUserDetails current,
                                    RedirectAttributes ra) {
        Incident incident = incidentService.findVisibleDetail(id, current);
        if (incident.getStatus() == IncidentStatus.CLOSED) {
            ra.addFlashAttribute("flashError", "Incident is closed.");
            return "redirect:/incidents/" + id;
        }
        List<SimilarIncident> rag = incidentService.findSimilarForRag(incident);
        Optional<String> suggestion = aiService.suggestResolution(
                incident.getTitle(), incident.getDescription(), rag);
        if (suggestion.isPresent()) {
            ra.addFlashAttribute("aiResolutionSuggestion", suggestion.get());
            ra.addFlashAttribute("aiResolutionRagCount", rag.size());
        } else {
            ra.addFlashAttribute("aiResolutionFailed", true);
        }
        return "redirect:/incidents/" + id;
    }

    @GetMapping("/{id}/close")
    @PreAuthorize("hasAnyRole('ENGINEER','ADMIN')")
    public String closeForm(@PathVariable Long id,
                            @AuthenticationPrincipal CustomUserDetails current,
                            Model model) {
        Incident incident = incidentService.findVisibleDetail(id, current);
        if (incident.getStatus() == IncidentStatus.CLOSED) {
            return "redirect:/incidents/" + id;
        }
        model.addAttribute("incident", incident);
        model.addAttribute("closeForm", new IncidentCloseDto());
        return "incident/close";
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAnyRole('ENGINEER','ADMIN')")
    public String close(@PathVariable Long id,
                        @Valid @ModelAttribute("closeForm") IncidentCloseDto form,
                        BindingResult bindingResult,
                        @AuthenticationPrincipal CustomUserDetails current,
                        Model model,
                        RedirectAttributes ra) {
        if (bindingResult.hasErrors()) {
            Incident incident = incidentService.findVisibleDetail(id, current);
            model.addAttribute("incident", incident);
            return "incident/close";
        }
        incidentService.close(id, form.getResolutionNotes());
        ra.addFlashAttribute("flash", "Incident closed. AI is generating a thread summary.");
        return "redirect:/incidents/" + id;
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasRole('ADMIN')")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        incidentService.delete(id);
        ra.addFlashAttribute("flash", "Incident INC-" + id + " deleted.");
        return "redirect:/incidents";
    }

    /**
     * Streams an incident report as a PDF download. Visibility check
     * (REPORTER ↛ others' incidents) lives in PdfService → IncidentService.
     */
    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable Long id,
                                              @AuthenticationPrincipal CustomUserDetails current) {
        byte[] pdf = pdfService.generateIncidentReport(id, current);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + pdfService.filenameFor(id) + "\"")
                .contentLength(pdf.length)
                .body(pdf);
    }

    private void addCreateFormReferenceData(Model model) {
        model.addAttribute("categories", Category.values());
        model.addAttribute("priorities", Priority.values());
    }
}
