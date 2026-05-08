package com.incidentmgmt.service;

import com.incidentmgmt.config.CustomUserDetails;
import com.incidentmgmt.entity.Incident;
import com.incidentmgmt.pdf.IncidentReportGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PdfService {

    private final IncidentService incidentService;
    private final IncidentReportGenerator reportGenerator;

    /**
     * Visibility-checked PDF generation. Throws AccessDeniedException for
     * Reporters trying to download someone else's incident, just like the
     * detail page does.
     */
    @Transactional(readOnly = true)
    public byte[] generateIncidentReport(Long incidentId, CustomUserDetails currentUser) {
        Incident incident = incidentService.findVisibleDetail(incidentId, currentUser);
        return reportGenerator.generate(incident);
    }

    public String filenameFor(Long incidentId) {
        return "incident-report-INC-" + incidentId + ".pdf";
    }
}
