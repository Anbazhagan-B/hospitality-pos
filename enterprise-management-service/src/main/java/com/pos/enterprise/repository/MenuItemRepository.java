package com.pos.enterprise.repository;

import com.pos.enterprise.entity.MenuItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {
    Page<MenuItem> findByOrganizationId(Long organizationId, Pageable pageable);
    Page<MenuItem> findByOrganizationIdAndActiveTrue(Long organizationId, Pageable pageable);
    List<MenuItem> findByOrganizationIdAndActiveTrue(Long organizationId);
    Page<MenuItem> findByCategoryId(Long categoryId, Pageable pageable);

    @Query("SELECT m FROM MenuItem m WHERE m.organizationId = :orgId AND m.category.id = :categoryId AND m.active = true")
    List<MenuItem> findActiveByOrganizationAndCategory(Long orgId, Long categoryId);
}
