package org.tb.customer.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.stereotype.Repository;
import org.tb.customer.domain.Customer;

@Repository
public interface CustomerRepository extends PagingAndSortingRepository<Customer, Long>,
    CrudRepository<Customer, Long> {

  @Query("""
      select c from Customer c order by upper(c.shortname) asc
      """)
  List<Customer> findAllOrderByShortnameIgnoreCase();

  @Query("""
      select c from Customer c where (c.hide is null or c.hide = false) order by upper(c.shortname) asc
      """)
  List<Customer> findAllVisibleOrderByShortnameIgnoreCase();

}
