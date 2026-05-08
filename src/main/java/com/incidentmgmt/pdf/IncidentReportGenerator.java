package com.incidentmgmt.pdf;

import com.incidentmgmt.entity.Incident;
import com.incidentmgmt.entity.IncidentStatus;
import com.incidentmgmt.entity.IncidentUpdate;
import com.incidentmgmt.entity.Priority;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Div;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.element.Text;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class IncidentReportGenerator {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    // Palette mirrors main.css so PDF and web look like the same product.
    private static final DeviceRgb COLOR_PRIMARY = new DeviceRgb(37, 99, 235);
    private static final DeviceRgb COLOR_TEXT = new DeviceRgb(31, 41, 55);
    private static final DeviceRgb COLOR_MUTED = new DeviceRgb(107, 114, 128);
    private static final DeviceRgb COLOR_BORDER = new DeviceRgb(229, 231, 235);
    private static final DeviceRgb COLOR_BG_LIGHT = new DeviceRgb(245, 247, 250);
    private static final DeviceRgb COLOR_AI_BG = new DeviceRgb(240, 244, 255);
    private static final DeviceRgb COLOR_WHITE = new DeviceRgb(255, 255, 255);

    @Value("${app.system-name:IncidentIQ}")
    private String systemName;

    @Value("${app.organization-name:Demo Org}")
    private String orgName;

    public byte[] generate(Incident incident) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PdfWriter writer = new PdfWriter(baos);
             PdfDocument pdfDoc = new PdfDocument(writer);
             Document document = new Document(pdfDoc, PageSize.A4)) {

            document.setMargins(50, 50, 50, 50);

            buildHeader(document, incident);
            buildMetadataTable(document, incident);

            if (hasText(incident.getAiSummary())) {
                buildAiSummary(document, incident);
            }

            buildSection(document, "Description", incident.getDescription());

            if (hasText(incident.getResolutionNotes())) {
                buildSection(document, "Resolution", incident.getResolutionNotes());
            }

            buildThread(document, incident);
            buildFooter(document);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate PDF for INC-" + incident.getId(), e);
        }
        return baos.toByteArray();
    }

    private void buildHeader(Document doc, Incident incident) {
        doc.add(new Paragraph(systemName + " — " + orgName)
                .setFontSize(10)
                .setFontColor(COLOR_MUTED)
                .setMarginBottom(0));
        doc.add(new Paragraph("INC-" + incident.getId())
                .setFontSize(10)
                .setFontColor(COLOR_MUTED)
                .setMarginTop(0)
                .setMarginBottom(2));
        doc.add(new Paragraph(incident.getTitle())
                .setFontSize(20)
                .setBold()
                .setFontColor(COLOR_TEXT)
                .setMarginTop(2)
                .setMarginBottom(10));

        Paragraph badges = new Paragraph()
                .add(badgeText(incident.getStatus().name(), statusColor(incident.getStatus())))
                .add(new Text("  "))
                .add(badgeText(incident.getPriority().name(), priorityColor(incident.getPriority())))
                .add(new Text("  "))
                .add(badgeText(incident.getCategory().name(), COLOR_MUTED));
        doc.add(badges.setMarginBottom(16));

        addHorizontalRule(doc);
    }

    private void buildMetadataTable(Document doc, Incident incident) {
        Table table = new Table(UnitValue.createPercentArray(new float[]{30, 70}))
                .setWidth(UnitValue.createPercentValue(100))
                .setMarginTop(12)
                .setMarginBottom(12);

        addMetaRow(table, "Status", incident.getStatus().name());
        addMetaRow(table, "Priority", priorityWithAi(incident));
        addMetaRow(table, "Category", categoryWithAi(incident));
        addMetaRow(table, "Reporter", incident.getReporter().getUsername());
        addMetaRow(table, "Assignee",
                incident.getAssignee() != null ? incident.getAssignee().getUsername() : "—");
        addMetaRow(table, "Created", DATE_FMT.format(incident.getCreatedAt()));
        addMetaRow(table, "Updated", DATE_FMT.format(incident.getUpdatedAt()));
        if (incident.getResolvedAt() != null) {
            addMetaRow(table, "Resolved", DATE_FMT.format(incident.getResolvedAt()));
        }

        doc.add(table);
    }

    private void addMetaRow(Table table, String label, String value) {
        table.addCell(metaCell(label, true));
        table.addCell(metaCell(value, false));
    }

    private Cell metaCell(String text, boolean isLabel) {
        Cell cell = new Cell()
                .add(new Paragraph(text)
                        .setFontSize(11)
                        .setFontColor(isLabel ? COLOR_MUTED : COLOR_TEXT)
                        .setMarginTop(0).setMarginBottom(0))
                .setBorder(Border.NO_BORDER)
                .setPaddingTop(4)
                .setPaddingBottom(4);
        if (isLabel) {
            cell.setBold();
        }
        return cell;
    }

    private String priorityWithAi(Incident i) {
        String value = i.getPriority().name();
        if (i.getPriorityAiSuggestion() != null && i.getPriorityAiSuggestion() != i.getPriority()) {
            value += "  (AI suggested: " + i.getPriorityAiSuggestion() + ")";
        }
        return value;
    }

    private String categoryWithAi(Incident i) {
        String value = i.getCategory().name();
        if (i.getCategoryAiSuggestion() != null && i.getCategoryAiSuggestion() != i.getCategory()) {
            value += "  (AI suggested: " + i.getCategoryAiSuggestion() + ")";
        }
        return value;
    }

    private void buildAiSummary(Document doc, Incident incident) {
        Div panel = new Div()
                .setBackgroundColor(COLOR_AI_BG)
                .setBorderLeft(new SolidBorder(COLOR_PRIMARY, 3))
                .setPadding(12)
                .setMarginTop(8)
                .setMarginBottom(12);
        panel.add(new Paragraph("AI Summary")
                .setFontSize(11)
                .setBold()
                .setFontColor(COLOR_PRIMARY)
                .setMarginTop(0)
                .setMarginBottom(4));
        panel.add(new Paragraph(incident.getAiSummary())
                .setFontSize(11)
                .setFontColor(COLOR_TEXT)
                .setMarginTop(0)
                .setMarginBottom(0));
        doc.add(panel);
    }

    private void buildSection(Document doc, String title, String body) {
        doc.add(new Paragraph(title)
                .setFontSize(13)
                .setBold()
                .setFontColor(COLOR_TEXT)
                .setMarginTop(16)
                .setMarginBottom(4));
        doc.add(new Paragraph(body == null ? "" : body)
                .setFontSize(11)
                .setFontColor(COLOR_TEXT)
                .setMarginTop(0));
    }

    private void buildThread(Document doc, Incident incident) {
        doc.add(new Paragraph("Thread (" + incident.getUpdates().size() + ")")
                .setFontSize(13)
                .setBold()
                .setFontColor(COLOR_TEXT)
                .setMarginTop(16)
                .setMarginBottom(8));

        if (incident.getUpdates().isEmpty()) {
            doc.add(new Paragraph("No comments.")
                    .setFontSize(11)
                    .setFontColor(COLOR_MUTED));
            return;
        }

        for (IncidentUpdate u : incident.getUpdates()) {
            Div block = new Div()
                    .setBackgroundColor(COLOR_BG_LIGHT)
                    .setPadding(10)
                    .setMarginBottom(8);
            block.add(new Paragraph()
                    .add(new Text(u.getAuthor().getUsername()).setBold().setFontColor(COLOR_PRIMARY))
                    .add(new Text("   " + DATE_FMT.format(u.getCreatedAt())).setFontColor(COLOR_MUTED).setFontSize(10))
                    .setFontSize(11)
                    .setMarginTop(0)
                    .setMarginBottom(4));
            block.add(new Paragraph(u.getText())
                    .setFontSize(11)
                    .setFontColor(COLOR_TEXT)
                    .setMarginTop(0)
                    .setMarginBottom(0));
            doc.add(block);
        }
    }

    private void buildFooter(Document doc) {
        addHorizontalRule(doc);
        doc.add(new Paragraph("Generated by " + systemName + " on " + DATE_FMT.format(LocalDateTime.now()))
                .setFontSize(9)
                .setFontColor(COLOR_MUTED)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginTop(8));
    }

    private void addHorizontalRule(Document doc) {
        Table hr = new Table(UnitValue.createPercentArray(new float[]{100}))
                .setWidth(UnitValue.createPercentValue(100));
        hr.addCell(new Cell()
                .setBorder(Border.NO_BORDER)
                .setBorderTop(new SolidBorder(COLOR_BORDER, 0.5f))
                .setHeight(0.5f)
                .setPadding(0));
        doc.add(hr);
    }

    private Text badgeText(String label, DeviceRgb bg) {
        return new Text(" " + label + " ")
                .setBackgroundColor(bg)
                .setFontColor(COLOR_WHITE)
                .setFontSize(9)
                .setBold();
    }

    private DeviceRgb statusColor(IncidentStatus status) {
        return switch (status) {
            case OPEN -> new DeviceRgb(37, 99, 235);
            case IN_PROGRESS -> new DeviceRgb(245, 158, 11);
            case RESOLVED -> new DeviceRgb(22, 163, 74);
            case CLOSED -> new DeviceRgb(107, 114, 128);
        };
    }

    private DeviceRgb priorityColor(Priority p) {
        return switch (p) {
            case P1 -> new DeviceRgb(220, 38, 38);
            case P2 -> new DeviceRgb(249, 115, 22);
            case P3 -> new DeviceRgb(202, 138, 4);
            case P4 -> new DeviceRgb(99, 102, 241);
        };
    }

    private boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
