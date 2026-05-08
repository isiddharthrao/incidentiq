package com.incidentmgmt.repository;

import com.incidentmgmt.entity.IncidentUpdate;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IncidentUpdateRepository extends JpaRepository<IncidentUpdate, Long> {

    @EntityGraph(attributePaths = {"author", "incident"})
    List<IncidentUpdate> findTop10ByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = {"author", "incident"})
    List<IncidentUpdate> findTop10ByIncident_Reporter_IdOrderByCreatedAtDesc(Long reporterId);
}
