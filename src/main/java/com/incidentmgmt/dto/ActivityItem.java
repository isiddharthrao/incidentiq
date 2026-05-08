package com.incidentmgmt.dto;

import java.time.LocalDateTime;

/**
 * One row in the dashboard's "recent activity" feed.
 * type is CREATED or COMMENT — used for icon/styling in the template.
 */
public record ActivityItem(
        String type,
        String text,
        Long incidentId,
        LocalDateTime when
) {
}
