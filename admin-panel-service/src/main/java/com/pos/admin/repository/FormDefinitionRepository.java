package com.pos.admin.repository;

import com.pos.admin.entity.FormDefinition;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FormDefinitionRepository extends JpaRepository<FormDefinition, Long> {
    Optional<FormDefinition> findByName(String name);

    boolean existsByName(String name);

    Page<FormDefinition> findByActiveTrue(Pageable pageable);

    List<FormDefinition> findByOrganizationIdAndActiveTrue(Long organizationId);

    Optional<FormDefinition> findByEntityTypeAndActiveTrue(String entityType);
}
