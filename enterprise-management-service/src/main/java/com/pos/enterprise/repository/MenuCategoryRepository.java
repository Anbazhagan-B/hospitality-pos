package com.pos.enterprise.repository;

import com.pos.enterprise.entity.MenuCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MenuCategoryRepository extends JpaRepository<MenuCategory, Long> {
    Page<MenuCategory> findByOrganizationId(Long organizationId, Pageable pageable);
    List<MenuCategory> findByOrganizationIdAndActiveTrue(Long organizationId);
    List<MenuCategory> findByOrganizationIdAndParentIsNullAndActiveTrue(Long organizationId);
}
