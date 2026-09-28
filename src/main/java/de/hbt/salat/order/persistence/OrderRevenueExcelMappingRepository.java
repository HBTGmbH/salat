package de.hbt.salat.order.persistence;

import java.util.List;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import de.hbt.salat.order.domain.OrderRevenueExcelMapping;

@Repository
public interface OrderRevenueExcelMappingRepository extends CrudRepository<OrderRevenueExcelMapping, Long> {
    List<OrderRevenueExcelMapping> findAllByEmployeeId(Long employeeId);
}
