package com.pos.check.repository;

import com.pos.check.entity.Check;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CheckRepository extends JpaRepository<Check, Long> {
    Optional<Check> findByCheckNumber(String checkNumber);

    Page<Check> findByEmployeeId(Long employeeId, Pageable pageable);

    Page<Check> findByTerminalId(Long terminalId, Pageable pageable);

    Page<Check> findByOrganizationId(Long organizationId, Pageable pageable);

    List<Check> findByStatus(Check.CheckStatus status);

    @Query("SELECT c FROM Check c WHERE c.organizationId = :orgId AND c.status = :status")
    List<Check> findByOrganizationAndStatus(Long orgId, Check.CheckStatus status);

    @Query("SELECT c FROM Check c WHERE c.openedAt BETWEEN :startDate AND :endDate")
    List<Check> findByDateRange(LocalDateTime startDate, LocalDateTime endDate);

    @Query("SELECT c FROM Check c WHERE c.organizationId = :orgId AND c.openedAt BETWEEN :startDate AND :endDate")
    List<Check> findByOrganizationAndDateRange(Long orgId, LocalDateTime startDate, LocalDateTime endDate);
}
