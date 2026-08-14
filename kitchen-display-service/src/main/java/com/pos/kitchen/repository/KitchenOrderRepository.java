package com.pos.kitchen.repository;

import com.pos.kitchen.entity.KitchenOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface KitchenOrderRepository extends JpaRepository<KitchenOrder, Long> {
    Optional<KitchenOrder> findByCheckId(Long checkId);

    List<KitchenOrder> findByOrganizationIdAndStatusIn(Long organizationId, List<KitchenOrder.OrderStatus> statuses);

    List<KitchenOrder> findByOrganizationIdOrderByReceivedAtAsc(Long organizationId);

    @Query("SELECT ko FROM KitchenOrder ko WHERE ko.organizationId = :orgId AND ko.status IN :statuses ORDER BY ko.priority DESC, ko.receivedAt ASC")
    List<KitchenOrder> findActiveOrdersByOrganization(Long orgId, List<KitchenOrder.OrderStatus> statuses);
}
