package org.tb.order.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.stereotype.Repository;
import org.tb.employee.domain.Employee;
import org.tb.order.domain.Customerorder;

@Repository
public interface CustomerorderRepository extends PagingAndSortingRepository<Customerorder, Long>,
    JpaSpecificationExecutor<Customerorder>, CrudRepository<Customerorder, Long> {

  @Query("select c from Customerorder c join c.responsibleHbt e where e.id = :responsibleHbtId")
  List<Customerorder> findAllByResponsibleHbt(long responsibleHbtId);

  @Query("select c.sign from Customerorder c where c.customer.segment.id = :segmentId")
  List<String> findSignsByCustomerSegmentId(long segmentId);

  @Query("select c.sign from Customerorder c join c.responsibleHbt e where e.id = :responsibleHbtId")
  List<String> findSignsByResponsibleHbt(long responsibleHbtId);

  /**
   * Every employee who is responsible for at least one customer order — the choices of the
   * "responsible" filter. An order may have several responsibles, hence the distinct.
   */
  @Query("select distinct e from Customerorder c join c.responsibleHbt e order by e.sign")
  List<Employee> findDistinctResponsibleHbt();

  List<Customerorder> findAllByCustomerId(long customerId);

  Optional<Customerorder> findBySign(String sign);

  @Query("""
      select c from Customerorder c where (c.hide is null or c.hide = false)
      or (c.fromDate <= :date and (c.untilDate is null or c.untilDate >= :date))
      order by c.sign
  """)
  List<Customerorder> findAllValidAtAndNotHidden(LocalDate date);

  @Query("""
      select distinct c from Customerorder c inner join fetch c.suborders s where s.invoice = 'Y'
      order by c.sign
  """)
  List<Customerorder> findAllInvoiceable();

}
