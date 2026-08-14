package com.pos.enterprise.repository;

import com.pos.enterprise.entity.Terminal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TerminalRepository extends JpaRepository<Terminal, Long> {
    Optional<Terminal> findByTerminalId(String terminalId);
    Page<Terminal> findByOrganizationId(Long organizationId, Pageable pageable);
    List<Terminal> findByOrganizationIdAndActiveTrue(Long organizationId);
    boolean existsByTerminalId(String terminalId);
}
