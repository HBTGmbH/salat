package org.tb.order.persistence;

import java.util.Collection;
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

  /**
   * The ids of every order this employee is responsible for — in either of the two roles an order knows (#1092).
   * {@code responsibleHbt} is a list, {@code respEmpHbtContract} a single employee, and whoever reads a booking
   * because of a responsibility is entitled through both alike.
   */
  @Query("""
      select distinct c.id from Customerorder c
      left join c.responsibleHbt r
      where r.id = :employeeId or c.respEmpHbtContract.id = :employeeId
      """)
  List<Long> findIdsByResponsibleEmployee(long employeeId);

  @Query("select c.sign from Customerorder c where c.customer.segment.id = :segmentId")
  List<String> findSignsByCustomerSegmentId(long segmentId);

  @Query("select c.sign from Customerorder c join c.responsibleHbt e where e.id = :responsibleHbtId")
  List<String> findSignsByResponsibleHbt(long responsibleHbtId);

  /**
   * Every employee who is responsible for at least one customer order — the choices of the
   * "responsible" filter. An order may have several responsibles, hence the distinct.
   * Responsible employees for hidden customer orders and hidden employees are left aside.
   *
   * <p>{@code hide is null} counts as not hidden, the one spelling of {@link org.tb.common.Hiding}
   * (#1104) — a {@code hide != true} would drop such a row, although nobody hid it.
   */
  @Query("""
      select distinct e from Customerorder c join c.responsibleHbt e
      where (c.hide is null or c.hide = false) and (e.hide is null or e.hide = false)
      order by e.sign
      """)
  List<Employee> findAllVisibleResponsibleHbt();

  /**
   * Same, but restricted to the orders of one customer segment — the choices of the "responsible"
   * filter once a segment is chosen (#952). Without the restriction the two filters can be combined
   * into a selection that no order can match.
   */
  @Query("""
      select distinct e from Customerorder c join c.responsibleHbt e
      where (c.hide is null or c.hide = false) and (e.hide is null or e.hide = false)
      and c.customer.segment.id = :segmentId
      order by e.sign
      """)
  List<Employee> findVisibleResponsibleHbtByCustomerSegmentId(long segmentId);

  List<Customerorder> findAllByCustomerId(long customerId);

  Optional<Customerorder> findBySign(String sign);

  /**
   * The orders behind a set of signs, hidden and expired ones included. Records that refer to an
   * order by sign rather than by id outlive it, and labelling them must not depend on the order
   * still being offered anywhere.
   */
  List<Customerorder> findBySignIn(Collection<String> signs);

  @Query("""
      select distinct c from Customerorder c inner join fetch c.suborders s where s.invoice = 'Y'
      order by c.sign
  """)
  List<Customerorder> findAllInvoiceable();

}
