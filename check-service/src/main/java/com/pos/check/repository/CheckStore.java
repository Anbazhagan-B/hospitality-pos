package com.pos.check.repository;

import com.pos.check.entity.Check;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

/**
 * Persistence port for checks.
 *
 * <p>{@link Check} stays the domain model on both sides. Its JPA annotations are
 * inert outside a persistence context, and the behaviour that matters -
 * {@code addItem}, {@code recalculateTotals} - lives on the object itself rather
 * than in whichever store is behind this interface.
 *
 * <p>Two implementations exist so the migration can be done as a strangler
 * rather than a cutover: {@code JpaCheckStore} preserves the existing Postgres
 * behaviour, {@code DynamoDbCheckStore} is the new path, and
 * {@code pos.check.store} selects between them per environment. That means
 * rolling back is a config change, not a redeploy.
 *
 * <p>The interface is deliberately narrow - only what {@code CheckService}
 * actually calls. The wider JPA repository exposed several finders nothing used,
 * and each one would have become a DynamoDB access pattern requiring its own
 * index. In DynamoDB, unused query methods are not free.
 */
public interface CheckStore {

    /**
     * Persists a check, assigning its id and check number if they are not set.
     *
     * <p>Both are allocated here, together, from a single sequence value. An
     * earlier version exposed a separate {@code nextCheckNumber()} that the
     * service called before saving, which drew from the sequence twice and
     * produced a check with id 2 numbered {@code CHK-1}. Since the DynamoDB
     * store resolves a check number by decoding the id out of it, that
     * mismatch silently broke lookup by number. Allocating both in one place
     * makes the invariant impossible to violate from outside.
     */
    Check save(Check check);

    Optional<Check> findById(Long id);

    Optional<Check> findByCheckNumber(String checkNumber);

    Page<Check> findByOrganizationId(Long organizationId, Pageable pageable);

    List<Check> findByOrganizationAndStatus(Long organizationId, Check.CheckStatus status);
}
