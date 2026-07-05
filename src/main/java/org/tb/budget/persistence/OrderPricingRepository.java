package org.tb.budget.persistence;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.tb.budget.domain.OrderPricing;

@Repository
public interface OrderPricingRepository
    extends CrudRepository<OrderPricing, Long>, PagingAndSortingRepository<OrderPricing, Long> {

    List<OrderPricing> findByCustomerorderSign(String customerorderSign);

    @Query("SELECT p FROM OrderPricing p WHERE p.customerorderSign = :co"
        + " AND p.suborderSign = :so AND p.employeeSign = :emp"
        + " AND p.validFrom <= :date AND p.validUntil >= :date")
    List<OrderPricing> findEffectiveEmployeeSpecific(
        @Param("co") String customerorderSign,
        @Param("so") String suborderSign,
        @Param("emp") String employeeSign,
        @Param("date") LocalDate date);

    @Query("SELECT p FROM OrderPricing p WHERE p.customerorderSign = :co"
        + " AND p.suborderSign = :so AND p.employeeSign IS NULL"
        + " AND p.validFrom <= :date AND p.validUntil >= :date")
    List<OrderPricing> findEffectiveSuborderWide(
        @Param("co") String customerorderSign,
        @Param("so") String suborderSign,
        @Param("date") LocalDate date);

    @Query("SELECT p FROM OrderPricing p WHERE p.customerorderSign = :co"
        + " AND p.suborderSign IS NULL AND p.employeeSign IS NULL"
        + " AND p.validFrom <= :date AND p.validUntil >= :date")
    List<OrderPricing> findEffectiveOrderWide(
        @Param("co") String customerorderSign,
        @Param("date") LocalDate date);

}
