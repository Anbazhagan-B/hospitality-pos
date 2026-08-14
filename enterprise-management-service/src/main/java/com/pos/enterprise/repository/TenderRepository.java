package com.pos.enterprise.repository;

import com.pos.enterprise.entity.Tender;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TenderRepository extends JpaRepository<Tender, Long> {
    Page<Tender> findByOrganizationId(Long organizationId, Pageable pageable);
    List<Tender> findByOrganizationIdAndActiveTrue(Long organizationId);
}
