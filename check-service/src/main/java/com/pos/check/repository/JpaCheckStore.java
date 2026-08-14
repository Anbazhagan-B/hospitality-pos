package com.pos.check.repository;

import com.pos.check.entity.Check;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Postgres/JPA implementation - the existing behaviour, unchanged.
 *
 * <p>Active by default, so nothing changes unless {@code pos.check.store} is
 * explicitly set to {@code dynamodb}.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "pos.check.store", havingValue = "jpa", matchIfMissing = true)
public class JpaCheckStore implements CheckStore {

    private final CheckRepository checkRepository;

    @Override
    public Check save(Check check) {
        if (check.getCheckNumber() == null) {
            // Retains the original timestamp scheme so this class stays a
            // faithful record of existing behaviour. It collides when two
            // replicas open a check in the same millisecond, and since the
            // check number is the Kafka partition key a collision merges two
            // customers' orders onto one kitchen ticket. Fixing it properly
            // needs a database sequence - which is the work the DynamoDB path
            // already does with a server-side atomic counter.
            check.setCheckNumber("CHK-" + System.currentTimeMillis());
        }
        return checkRepository.save(check);
    }

    @Override
    public Optional<Check> findById(Long id) {
        return checkRepository.findById(id);
    }

    @Override
    public Optional<Check> findByCheckNumber(String checkNumber) {
        return checkRepository.findByCheckNumber(checkNumber);
    }

    @Override
    public Page<Check> findByOrganizationId(Long organizationId, Pageable pageable) {
        return checkRepository.findByOrganizationId(organizationId, pageable);
    }

    @Override
    public List<Check> findByOrganizationAndStatus(Long organizationId, Check.CheckStatus status) {
        return checkRepository.findByOrganizationAndStatus(organizationId, status);
    }
}
