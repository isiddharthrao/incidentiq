package com.incidentmgmt.repository;

import com.incidentmgmt.entity.Incident;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IncidentRepository extends JpaRepository<Incident, Long> {

    @EntityGraph(attributePaths = {"reporter", "assignee"})
    List<Incident> findAllByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = {"reporter", "assignee"})
    List<Incident> findByReporter_IdOrderByCreatedAtDesc(Long reporterId);

    // --- Dashboard counts ---

    long countByStatus(com.incidentmgmt.entity.IncidentStatus status);

    long countByStatusAndReporter_Id(com.incidentmgmt.entity.IncidentStatus status, Long reporterId);

    long countByStatusAndAssignee_Id(com.incidentmgmt.entity.IncidentStatus status, Long assigneeId);

    long countByReporter_Id(Long reporterId);

    @EntityGraph(attributePaths = {"reporter"})
    List<Incident> findTop10ByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = {"reporter"})
    List<Incident> findTop10ByReporter_IdOrderByCreatedAtDesc(Long reporterId);

    @Query("SELECT i.priority, COUNT(i) FROM Incident i GROUP BY i.priority")
    List<Object[]> countGroupedByPriority();

    @Query("SELECT i.priority, COUNT(i) FROM Incident i WHERE i.reporter.id = :reporterId GROUP BY i.priority")
    List<Object[]> countGroupedByPriorityForReporter(@Param("reporterId") Long reporterId);

    @Query("SELECT i.status, COUNT(i) FROM Incident i GROUP BY i.status")
    List<Object[]> countGroupedByStatus();

    @Query("SELECT i.status, COUNT(i) FROM Incident i WHERE i.reporter.id = :reporterId GROUP BY i.status")
    List<Object[]> countGroupedByStatusForReporter(@Param("reporterId") Long reporterId);

    @Query("SELECT i.category, COUNT(i) FROM Incident i GROUP BY i.category")
    List<Object[]> countGroupedByCategory();

    @Query("SELECT i.category, COUNT(i) FROM Incident i WHERE i.reporter.id = :reporterId GROUP BY i.category")
    List<Object[]> countGroupedByCategoryForReporter(@Param("reporterId") Long reporterId);

    /**
     * Average minutes from created_at to resolved_at for incidents resolved
     * in the given window. Native because JPQL has no TIMESTAMPDIFF equivalent.
     */
    @Query(value = """
            SELECT AVG(TIMESTAMPDIFF(MINUTE, created_at, resolved_at))
            FROM incident
            WHERE resolved_at IS NOT NULL AND resolved_at BETWEEN :start AND :end
            """, nativeQuery = true)
    Double avgResolutionMinutes(@Param("start") java.time.LocalDateTime start,
                                @Param("end") java.time.LocalDateTime end);

    @Query(value = """
            SELECT AVG(TIMESTAMPDIFF(MINUTE, created_at, resolved_at))
            FROM incident
            WHERE reporter_id = :reporterId
              AND resolved_at IS NOT NULL
              AND resolved_at BETWEEN :start AND :end
            """, nativeQuery = true)
    Double avgResolutionMinutesForReporter(@Param("reporterId") Long reporterId,
                                           @Param("start") java.time.LocalDateTime start,
                                           @Param("end") java.time.LocalDateTime end);

    /**
     * Eagerly load reporter, assignee, updates, and each update's author for the
     * detail page. Without this, lazy collections would blow up under
     * spring.jpa.open-in-view=false during template rendering.
     */
    @EntityGraph(attributePaths = {"reporter", "assignee", "updates", "updates.author"})
    @Query("SELECT i FROM Incident i WHERE i.id = :id")
    Optional<Incident> findByIdWithDetails(@Param("id") Long id);

    /**
     * Lexical similarity search via MySQL FULLTEXT index (idx_incident_fts).
     * Scores rows on title+description match strength against the query, drops
     * non-matches (score > 0), and returns the top N (controlled via Pageable).
     * Native query because JPQL has no FULLTEXT primitives.
     */
    @Query(value = """
            SELECT i.* FROM incident i
            WHERE i.id != :excludeId
              AND MATCH(i.title, i.description) AGAINST (:query IN NATURAL LANGUAGE MODE) > 0
            ORDER BY MATCH(i.title, i.description) AGAINST (:query IN NATURAL LANGUAGE MODE) DESC
            """, nativeQuery = true)
    java.util.List<Incident> findSimilar(@Param("excludeId") Long excludeId,
                                         @Param("query") String query,
                                         org.springframework.data.domain.Pageable pageable);
}
