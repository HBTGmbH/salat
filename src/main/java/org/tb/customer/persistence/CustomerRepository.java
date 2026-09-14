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

  /**
   * Wie {@link #findAllVisibleOrderByShortnameIgnoreCase()}, behält aber den Auftraggeber mit
   * {@code keepId} auch dann, wenn er ausgeblendet ist. Ein {@code keepId} von {@code null} trifft
   * auf keine Zeile zu und ergibt damit genau die Liste der sichtbaren Auftraggeber.
   */
  @Query("""
      select c from Customer c where (c.hide is null or c.hide = false) or c.id = :keepId
      order by upper(c.shortname) asc
      """)
  List<Customer> findAllVisibleOrWithIdOrderByShortnameIgnoreCase(Long keepId);

}
