package com.incidentmgmt.dto;

import com.incidentmgmt.entity.Category;
import com.incidentmgmt.entity.IncidentStatus;
import com.incidentmgmt.entity.Priority;

import java.util.List;
import java.util.Map;

/**
 * Everything the dashboard template renders, computed once per request.
 *
 * @param scopeLabel              "Your incidents" / "Org-wide"
 * @param openCount               OPEN + IN_PROGRESS, in scope
 * @param myAssignedOpenCount     ENGINEER only — count of OPEN/IN_PROGRESS assigned to current user
 * @param totalIncidents          total in scope
 * @param resolvedIncidents       RESOLVED + CLOSED in scope
 * @param byPriority              all 4 priorities, zero-filled
 * @param byStatus                all 4 statuses, zero-filled
 * @param byCategory              all 7 categories, zero-filled
 * @param avgResolutionHoursThisWeek    null if no closures in window
 * @param avgResolutionHoursLastWeek    null if no closures in window
 * @param recentActivity          last 10 CREATED/COMMENT events in scope
 * @param totalUsers              ADMIN only — overall user count
 * @param aiCallsTotal            ADMIN only
 * @param aiCallsSuccess          ADMIN only
 */
public record DashboardData(
        String scopeLabel,
        long openCount,
        Long myAssignedOpenCount,
        long totalIncidents,
        long resolvedIncidents,
        Map<Priority, Long> byPriority,
        Map<IncidentStatus, Long> byStatus,
        Map<Category, Long> byCategory,
        Double avgResolutionHoursThisWeek,
        Double avgResolutionHoursLastWeek,
        List<ActivityItem> recentActivity,
        Long totalUsers,
        Long aiCallsTotal,
        Long aiCallsSuccess
) {

    public boolean hasAdminMetrics() {
        return totalUsers != null;
    }

    public boolean hasMyAssigned() {
        return myAssignedOpenCount != null;
    }

    public long maxPriorityCount() {
        return byPriority.values().stream().mapToLong(Long::longValue).max().orElse(0);
    }

    public long maxStatusCount() {
        return byStatus.values().stream().mapToLong(Long::longValue).max().orElse(0);
    }

    public long maxCategoryCount() {
        return byCategory.values().stream().mapToLong(Long::longValue).max().orElse(0);
    }

    public Long aiSuccessPercent() {
        if (aiCallsTotal == null || aiCallsTotal == 0) {
            return null;
        }
        return Math.round(100.0 * aiCallsSuccess / aiCallsTotal);
    }
}
