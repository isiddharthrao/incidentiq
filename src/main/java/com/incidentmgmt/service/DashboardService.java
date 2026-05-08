package com.incidentmgmt.service;

import com.incidentmgmt.config.CustomUserDetails;
import com.incidentmgmt.dto.ActivityItem;
import com.incidentmgmt.dto.DashboardData;
import com.incidentmgmt.entity.*;
import com.incidentmgmt.repository.AiCallLogRepository;
import com.incidentmgmt.repository.IncidentRepository;
import com.incidentmgmt.repository.IncidentUpdateRepository;
import com.incidentmgmt.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private final IncidentRepository incidentRepository;
    private final IncidentUpdateRepository incidentUpdateRepository;
    private final UserRepository userRepository;
    private final AiCallLogRepository aiCallLogRepository;

    public DashboardData compute(CustomUserDetails currentUser) {
        Role role = currentUser.getRole();
        Long uid = currentUser.getId();
        boolean reporterScope = role == Role.REPORTER;

        String scopeLabel = reporterScope ? "Your incidents" : "Org-wide";

        // --- Counts ---
        long openCount = reporterScope
                ? incidentRepository.countByStatusAndReporter_Id(IncidentStatus.OPEN, uid)
                + incidentRepository.countByStatusAndReporter_Id(IncidentStatus.IN_PROGRESS, uid)
                : incidentRepository.countByStatus(IncidentStatus.OPEN)
                + incidentRepository.countByStatus(IncidentStatus.IN_PROGRESS);

        long resolvedCount = reporterScope
                ? incidentRepository.countByStatusAndReporter_Id(IncidentStatus.RESOLVED, uid)
                + incidentRepository.countByStatusAndReporter_Id(IncidentStatus.CLOSED, uid)
                : incidentRepository.countByStatus(IncidentStatus.RESOLVED)
                + incidentRepository.countByStatus(IncidentStatus.CLOSED);

        long totalIncidents = reporterScope
                ? incidentRepository.countByReporter_Id(uid)
                : incidentRepository.count();

        Long myAssignedOpen = (role == Role.ENGINEER)
                ? incidentRepository.countByStatusAndAssignee_Id(IncidentStatus.OPEN, uid)
                + incidentRepository.countByStatusAndAssignee_Id(IncidentStatus.IN_PROGRESS, uid)
                : null;

        // --- Group counts (zero-filled) ---
        Map<Priority, Long> byPriority = aggregate(
                reporterScope
                        ? incidentRepository.countGroupedByPriorityForReporter(uid)
                        : incidentRepository.countGroupedByPriority(),
                Priority.class);

        Map<IncidentStatus, Long> byStatus = aggregate(
                reporterScope
                        ? incidentRepository.countGroupedByStatusForReporter(uid)
                        : incidentRepository.countGroupedByStatus(),
                IncidentStatus.class);

        Map<Category, Long> byCategory = aggregate(
                reporterScope
                        ? incidentRepository.countGroupedByCategoryForReporter(uid)
                        : incidentRepository.countGroupedByCategory(),
                Category.class);

        // --- Average resolution time ---
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime weekAgo = now.minusDays(7);
        LocalDateTime twoWeeksAgo = now.minusDays(14);

        Double thisWeekMin = reporterScope
                ? incidentRepository.avgResolutionMinutesForReporter(uid, weekAgo, now)
                : incidentRepository.avgResolutionMinutes(weekAgo, now);
        Double lastWeekMin = reporterScope
                ? incidentRepository.avgResolutionMinutesForReporter(uid, twoWeeksAgo, weekAgo)
                : incidentRepository.avgResolutionMinutes(twoWeeksAgo, weekAgo);

        Double thisWeekHours = thisWeekMin == null ? null : Math.round(thisWeekMin / 60.0 * 10) / 10.0;
        Double lastWeekHours = lastWeekMin == null ? null : Math.round(lastWeekMin / 60.0 * 10) / 10.0;

        // --- Activity feed ---
        List<ActivityItem> activity = buildActivityFeed(reporterScope, uid);

        // --- Admin extras ---
        Long totalUsers = (role == Role.ADMIN) ? userRepository.count() : null;
        Long aiCallsTotal = (role == Role.ADMIN) ? aiCallLogRepository.count() : null;
        Long aiCallsSuccess = (role == Role.ADMIN) ? aiCallLogRepository.countBySuccessTrue() : null;

        return new DashboardData(
                scopeLabel,
                openCount,
                myAssignedOpen,
                totalIncidents,
                resolvedCount,
                byPriority,
                byStatus,
                byCategory,
                thisWeekHours,
                lastWeekHours,
                activity,
                totalUsers,
                aiCallsTotal,
                aiCallsSuccess
        );
    }

    private <E extends Enum<E>> Map<E, Long> aggregate(List<Object[]> rows, Class<E> enumClass) {
        Map<E, Long> result = new EnumMap<>(enumClass);
        for (E e : enumClass.getEnumConstants()) {
            result.put(e, 0L);
        }
        for (Object[] row : rows) {
            @SuppressWarnings("unchecked")
            E key = (E) row[0];
            Long count = ((Number) row[1]).longValue();
            result.put(key, count);
        }
        return result;
    }

    private List<ActivityItem> buildActivityFeed(boolean reporterScope, Long uid) {
        List<Incident> recentIncidents = reporterScope
                ? incidentRepository.findTop10ByReporter_IdOrderByCreatedAtDesc(uid)
                : incidentRepository.findTop10ByOrderByCreatedAtDesc();

        List<IncidentUpdate> recentComments = reporterScope
                ? incidentUpdateRepository.findTop10ByIncident_Reporter_IdOrderByCreatedAtDesc(uid)
                : incidentUpdateRepository.findTop10ByOrderByCreatedAtDesc();

        List<ActivityItem> items = new ArrayList<>();
        for (Incident i : recentIncidents) {
            items.add(new ActivityItem(
                    "CREATED",
                    i.getReporter().getUsername() + " reported \"" + i.getTitle() + "\"",
                    i.getId(),
                    i.getCreatedAt()
            ));
        }
        for (IncidentUpdate u : recentComments) {
            items.add(new ActivityItem(
                    "COMMENT",
                    u.getAuthor().getUsername() + " commented on INC-" + u.getIncident().getId(),
                    u.getIncident().getId(),
                    u.getCreatedAt()
            ));
        }
        items.sort(Comparator.comparing(ActivityItem::when).reversed());
        return items.stream().limit(10).toList();
    }
}
