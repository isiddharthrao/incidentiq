package com.incidentmgmt.service;

import com.incidentmgmt.config.CustomUserDetails;
import com.incidentmgmt.dto.IncidentCreateDto;
import com.incidentmgmt.dto.SimilarIncident;
import com.incidentmgmt.entity.*;
import com.incidentmgmt.repository.IncidentRepository;
import com.incidentmgmt.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class IncidentService {

    private final IncidentRepository incidentRepository;
    private final UserRepository userRepository;
    private final AiService aiService;

    @Transactional(readOnly = true)
    public List<Incident> findVisibleTo(CustomUserDetails currentUser) {
        if (currentUser.getRole() == Role.REPORTER) {
            return incidentRepository.findByReporter_IdOrderByCreatedAtDesc(currentUser.getId());
        }
        return incidentRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public Incident findVisibleDetail(Long id, CustomUserDetails currentUser) {
        Incident incident = incidentRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new EntityNotFoundException("Incident not found: " + id));
        if (currentUser.getRole() == Role.REPORTER
                && !incident.getReporter().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("You can only view your own incidents.");
        }
        return incident;
    }

    /**
     * Top 5 lexically-similar past incidents for the detail-page sidebar.
     * Engineer/Admin only — reporters never see other people's incidents,
     * even as snippets, so the controller filters by role before calling this.
     */
    @Transactional(readOnly = true)
    public List<Incident> findSimilarForDisplay(Incident incident) {
        String query = (incident.getTitle() + " " + incident.getDescription()).trim();
        if (query.isBlank()) {
            return List.of();
        }
        return incidentRepository.findSimilar(incident.getId(), query, PageRequest.of(0, 5));
    }

    /**
     * Top 3 RAG-context past incidents for the resolution-suggestion prompt.
     * Only returns ones with a non-blank resolution — empty resolutions add
     * noise to the prompt without helping Gemini.
     */
    @Transactional(readOnly = true)
    public List<SimilarIncident> findSimilarForRag(Incident incident) {
        String query = (incident.getTitle() + " " + incident.getDescription()).trim();
        if (query.isBlank()) {
            return List.of();
        }
        return incidentRepository.findSimilar(incident.getId(), query, PageRequest.of(0, 3))
                .stream()
                .filter(i -> i.getResolutionNotes() != null && !i.getResolutionNotes().isBlank())
                .map(i -> new SimilarIncident(i.getId(), i.getTitle(), i.getResolutionNotes()))
                .toList();
    }

    public Incident create(IncidentCreateDto dto, Long reporterId) {
        User reporter = userRepository.findById(reporterId)
                .orElseThrow(() -> new EntityNotFoundException("Reporter not found: " + reporterId));
        Incident incident = Incident.builder()
                .title(dto.getTitle())
                .description(dto.getDescription())
                .category(dto.getCategory())
                .categoryAiSuggestion(dto.getCategoryAiSuggestion())
                .priority(dto.getPriority())
                .priorityAiSuggestion(dto.getPriorityAiSuggestion())
                .status(IncidentStatus.OPEN)
                .reporter(reporter)
                .build();
        return incidentRepository.save(incident);
    }

    public void addComment(Long incidentId, Long authorId, String text) {
        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new EntityNotFoundException("Incident not found: " + incidentId));
        if (incident.getStatus() == IncidentStatus.CLOSED) {
            throw new IllegalStateException("Cannot comment on a closed incident.");
        }
        User author = userRepository.findById(authorId)
                .orElseThrow(() -> new EntityNotFoundException("Author not found: " + authorId));
        IncidentUpdate update = IncidentUpdate.builder()
                .incident(incident)
                .author(author)
                .text(text)
                .build();
        incident.getUpdates().add(update);
    }

    public void assign(Long incidentId, Long assigneeId) {
        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new EntityNotFoundException("Incident not found: " + incidentId));
        if (incident.getStatus() == IncidentStatus.CLOSED) {
            throw new IllegalStateException("Cannot reassign a closed incident.");
        }
        if (assigneeId == null) {
            incident.setAssignee(null);
            return;
        }
        User assignee = userRepository.findById(assigneeId)
                .orElseThrow(() -> new EntityNotFoundException("Assignee not found: " + assigneeId));
        if (assignee.getRole() == Role.REPORTER) {
            throw new IllegalArgumentException("Reporters cannot be assigned to incidents.");
        }
        incident.setAssignee(assignee);
    }

    public void changeStatus(Long incidentId, IncidentStatus newStatus) {
        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new EntityNotFoundException("Incident not found: " + incidentId));
        if (newStatus == IncidentStatus.CLOSED) {
            throw new IllegalStateException("Use the close action — closing requires resolution notes.");
        }
        if (incident.getStatus() == IncidentStatus.CLOSED) {
            throw new IllegalStateException("Cannot change status of a closed incident.");
        }
        incident.setStatus(newStatus);
        if (newStatus == IncidentStatus.RESOLVED && incident.getResolvedAt() == null) {
            incident.setResolvedAt(LocalDateTime.now());
        }
    }

    /**
     * Closes the incident, then asks Gemini to summarize the full thread.
     * Loaded with details so the AI prompt has reporter, assignee, all comments.
     * AI failure is intentionally swallowed — closing must succeed regardless.
     */
    public void close(Long incidentId, String resolutionNotes) {
        Incident incident = incidentRepository.findByIdWithDetails(incidentId)
                .orElseThrow(() -> new EntityNotFoundException("Incident not found: " + incidentId));
        incident.setStatus(IncidentStatus.CLOSED);
        incident.setResolutionNotes(resolutionNotes);
        if (incident.getResolvedAt() == null) {
            incident.setResolvedAt(LocalDateTime.now());
        }
        Optional<String> summary = aiService.summarizeThread(incident);
        summary.ifPresent(incident::setAiSummary);
    }

    public void delete(Long incidentId) {
        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new EntityNotFoundException("Incident not found: " + incidentId));
        incidentRepository.delete(incident);
    }
}
