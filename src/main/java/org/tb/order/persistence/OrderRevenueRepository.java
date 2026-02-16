package org.tb.order.persistence;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.tb.order.domain.OrderRevenue;

@Repository
public interface OrderRevenueRepository extends CrudRepository<OrderRevenue, Long> {
    Optional<OrderRevenue> findByCustomerorderIdAndDateAndType(Long suborderId, LocalDate date, String type);
}
