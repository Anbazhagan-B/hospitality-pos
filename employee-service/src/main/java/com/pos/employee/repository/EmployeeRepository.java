package com.pos.employee.repository;

import com.pos.employee.entity.Employee;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    Optional<Employee> findByUsername(String username);
    Optional<Employee> findByEmail(String email);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    Page<Employee> findByOrganizationId(Long organizationId, Pageable pageable);
    List<Employee> findByOrganizationId(Long organizationId);
    Page<Employee> findByActiveTrue(Pageable pageable);
}
