package de.hbt.salat.order.persistence;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.stereotype.Repository;
import de.hbt.salat.employee.domain.Employee;
import de.hbt.salat.order.domain.Customerorder;
import de.hbt.salat.order.domain.CustomerorderSearchRow;

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
   * <p>{@code hide is null} counts as not hidden, the one spelling of {@link de.hbt.salat.common.Hiding}
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
   *
   * <p>The eager associations of an order come along in the same statement (#1222). Selected one
   * by one, they cost a statement per order for its responsibles and one per customer — the budget
   * dashboard asks for every order that has an active plan.
   */
  @Query("""
      select distinct c from Customerorder c
      left join fetch c.customer
      left join fetch c.responsibleHbt r
      left join fetch r.salatUser
      left join fetch c.respEmpHbtContract e
      left join fetch e.salatUser
      where c.sign in :signs
      """)
  List<Customerorder> findBySignIn(Collection<String> signs);

  @Query("""
      select distinct c from Customerorder c inner join fetch c.suborders s where s.invoice = 'Y'
      order by c.sign
  """)
  List<Customerorder> findAllInvoiceable();

  /**
   * Candidates for the object search of the command palette (#1157): every order whose sign,
   * descriptions or customer contain each of the words — hidden and ended ones included, because
   * the palette shows them, ranked lower and marked. A missing word is {@code null} and no condition.
   *
   * <p>The order puts hidden orders and ended ones last, so that the limit cuts those first. It is an
   * order, not a filter; whether a row counts as hidden or ended is decided in Java again, by
   * {@link de.hbt.salat.common.Hiding} and {@link de.hbt.salat.common.Validity} — {@code hide = true} is false
   * for {@code null}, and an open end is never before today.
   */
  @Query("""
      select new de.hbt.salat.order.domain.CustomerorderSearchRow(c.id, c.sign, c.shortdescription,
          c.description, cu.id, cu.shortname, cu.name, c.hide, c.untilDate)
      from Customerorder c join c.customer cu
      where (lower(c.sign) like :word1 escape '!' or lower(c.shortdescription) like :word1 escape '!'
          or lower(c.description) like :word1 escape '!' or lower(cu.shortname) like :word1 escape '!'
          or lower(cu.name) like :word1 escape '!')
      and (:word2 is null or lower(c.sign) like :word2 escape '!'
          or lower(c.shortdescription) like :word2 escape '!' or lower(c.description) like :word2 escape '!'
          or lower(cu.shortname) like :word2 escape '!' or lower(cu.name) like :word2 escape '!')
      and (:word3 is null or lower(c.sign) like :word3 escape '!'
          or lower(c.shortdescription) like :word3 escape '!' or lower(c.description) like :word3 escape '!'
          or lower(cu.shortname) like :word3 escape '!' or lower(cu.name) like :word3 escape '!')
      order by case when c.hide = true then 1 else 0 end,
          case when c.untilDate < :today then 1 else 0 end,
          c.sign
      """)
  List<CustomerorderSearchRow> findPaletteCandidates(String word1, String word2, String word3,
      LocalDate today, Pageable page);

}
