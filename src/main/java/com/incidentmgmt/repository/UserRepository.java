package com.incidentmgmt.repository;

import com.incidentmgmt.entity.Role;
import com.incidentmgmt.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    long countByRole(Role role);

    java.util.List<User> findByRoleInAndEnabledTrueOrderByUsername(java.util.List<Role> roles);
}
