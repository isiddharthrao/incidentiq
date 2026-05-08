package com.incidentmgmt.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "ai_call_log", indexes = {
        @Index(name = "idx_ai_log_created", columnList = "created_at"),
        @Index(name = "idx_ai_log_call_type", columnList = "call_type")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiCallLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "call_type", nullable = false, length = 30)
    private String callType;

    @Column(name = "prompt_summary", columnDefinition = "TEXT")
    private String promptSummary;

    @Column(name = "response_summary", columnDefinition = "TEXT")
    private String responseSummary;

    @Column(name = "latency_ms")
    private Long latencyMs;

    @Column(nullable = false)
    private boolean success;

    @Column(name = "error_msg", length = 500)
    private String errorMsg;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
