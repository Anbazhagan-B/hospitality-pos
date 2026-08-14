package com.pos.enterprise.repository;

import com.pos.enterprise.entity.Organization;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OrganizationRepository extends JpaRepository<Organization, Long> {
    Optional<Organization> findByName(String name);
    Page<Organization> findByActiveTrue(Pageable pageable);
    boolean existsByName(String name);
}
