package org.tb.budget.persistence;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.stereotype.Repository;
import org.tb.budget.domain.OrderFlatRate;

@Repository
public interface OrderFlatRateRepository
    extends CrudRepository<OrderFlatRate, Long>, PagingAndSortingRepository<OrderFlatRate, Long> {

    List<OrderFlatRate> findAllByOrderByCustomerorderSignAscValidFromAsc();

    List<OrderFlatRate> findByCustomerorderSignOrderByValidFromAsc(String customerorderSign);

    List<OrderFlatRate> findByCustomerorderSignInOrderByIdAsc(Collection<String> customerorderSigns);

    /**
     * The customer orders the list view offers for filtering. Taken from the flat rates themselves
     * rather than from the selectable orders, for the reason
     * {@code OrderPricingRepository#findDistinctCustomerorderSigns} gives: a flat rate outlives its
     * order and has to stay reachable when the order is gone or hidden.
     */
    @Query("SELECT DISTINCT f.customerorderSign FROM OrderFlatRate f ORDER BY f.customerorderSign ASC")
    List<String> findDistinctCustomerorderSigns();

}
