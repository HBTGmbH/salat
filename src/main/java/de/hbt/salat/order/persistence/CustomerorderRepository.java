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
import de.hbt.salat.order.domain.Customerorder;
import de.hbt.salat.order.domain.CustomerorderOption;
import de.hbt.salat.order.domain.CustomerorderSearchRow;
import de.hbt.salat.order.domain.ResponsibleOption;

@Repository
public interface CustomerorderRepository extends PagingAndSortingRepository<Customerorder, Long>,
    JpaSpecificationExecutor<Customerorder>, CrudRepository<Customerorder, Long> {

  @Query("select c from Customerorder c join c.responsibleHbt e where e.id = :responsibleHbtId")
  List<Customerorder> findAllByResponsibleHbt(long responsibleHbtId);

  /**
   * The orders a login is responsible for, through the employees it belongs to (#1386), as options.
   * Hidden ones are left out: a hidden order grants nothing.
   */
  @Query("""
      select distinct new de.hbt.salat.order.domain.CustomerorderOption(c.id, c.sign, c.shortdescription,
          c.description, cu.shortname, cu.name, c.hide)
      from Customerorder c join c.responsibleHbt e left join c.customer cu
      where e.salatUser.id = :salatUserId and (c.hide is null or c.hide = false)
      order by c.sign
      """)
  List<CustomerorderOption> findResponsibleOptionsBySalatUserId(long salatUserId);

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

  @Query("select c.id from Customerorder c where c.customer.segment.id = :segmentId")
  List<Long> findIdsByCustomerSegmentId(long segmentId);

  /**
   * The ids of the orders listing this employee among {@code responsibleHbt} — that role only,
   * unlike {@link #findIdsByResponsibleEmployee}.
   */
  @Query("select c.id from Customerorder c join c.responsibleHbt e where e.id = :responsibleHbtId")
  List<Long> findIdsByResponsibleHbt(long responsibleHbtId);

  /**
   * Every employee who is responsible for at least one customer order — the choices of the
   * "responsible" filter. An order may have several responsibles, hence the distinct.
   * Responsible employees for hidden customer orders and hidden employees are left aside.
   *
   * <p>{@code hide is null} counts as not hidden, the one spelling of {@link de.hbt.salat.common.Hiding}
   * (#1104) — a {@code hide != true} would drop such a row, although nobody hid it.
   */
  @Query("""
      select distinct new de.hbt.salat.order.domain.ResponsibleOption(
             e.id, e.sign, concat(e.firstname, ' ', e.lastname), e.hide)
      from Customerorder c join c.responsibleHbt e
      where (c.hide is null or c.hide = false) and (e.hide is null or e.hide = false)
      order by e.sign
      """)
  List<ResponsibleOption> findAllVisibleResponsibleHbt();

  /**
   * Same, but restricted to the orders of one customer segment — the choices of the "responsible"
   * filter once a segment is chosen (#952). Without the restriction the two filters can be combined
   * into a selection that no order can match.
   */
  @Query("""
      select distinct new de.hbt.salat.order.domain.ResponsibleOption(
             e.id, e.sign, concat(e.firstname, ' ', e.lastname), e.hide)
      from Customerorder c join c.responsibleHbt e
      where (c.hide is null or c.hide = false) and (e.hide is null or e.hide = false)
      and c.customer.segment.id = :segmentId
      order by e.sign
      """)
  List<ResponsibleOption> findVisibleResponsibleHbtByCustomerSegmentId(long segmentId);

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

  /**
   * The orders the invoice page offers: every order with at least one invoiceable suborder (#1283).
   * The {@code exists} keeps it at one row per order; fetching the suborders instead multiplied the
   * rows by them and made the database deduplicate and sort the wide rows in a temporary table. The
   * suborders of the chosen order are loaded on their own.
   */
  @Query("""
      select new de.hbt.salat.order.domain.CustomerorderOption(c.id, c.sign, c.shortdescription,
          c.description, cu.shortname, cu.name, c.hide)
      from Customerorder c left join c.customer cu
      where exists (select 1 from Suborder s where s.customerorder = c and s.invoice = 'Y')
      order by c.sign
      """)
  List<CustomerorderOption> findAllInvoiceable();

  /**
   * The orders with these ids as a select offers them, hidden and expired ones included — for the
   * modules that refer to an order by id and only need to name it (#1212).
   */
  @Query("""
      select new de.hbt.salat.order.domain.CustomerorderOption(c.id, c.sign, c.shortdescription,
          c.description, cu.shortname, cu.name, c.hide)
      from Customerorder c left join c.customer cu
      where c.id in :ids
      order by c.sign
      """)
  List<CustomerorderOption> findOptionsByIdIn(Collection<Long> ids);

  /**
   * The orders a select offers, as options: everything not hidden, plus the order {@code keepId} even if
   * it is hidden (#1343). {@code keepId} may be {@code null}.
   */
  @Query("""
      select new de.hbt.salat.order.domain.CustomerorderOption(c.id, c.sign, c.shortdescription,
          c.description, cu.shortname, cu.name, c.hide)
      from Customerorder c left join c.customer cu
      where c.hide is null or c.hide = false or c.id = :keepId
      order by c.sign
      """)
  List<CustomerorderOption> findSelectableOptions(Long keepId);

  /**
   * The orders a select for something new offers, as options (#1386, ADR-0029): neither hidden nor
   * inactive on {@code today}, plus the order {@code keepId} whatever it is. {@code keepId} may be
   * {@code null}.
   */
  @Query("""
      select new de.hbt.salat.order.domain.CustomerorderOption(c.id, c.sign, c.shortdescription,
          c.description, cu.shortname, cu.name, c.hide)
      from Customerorder c left join c.customer cu
      where ((c.hide is null or c.hide = false) and (c.untilDate is null or c.untilDate >= :today))
         or c.id = :keepId
      order by c.sign
      """)
  List<CustomerorderOption> findCreatableOptions(Long keepId, LocalDate today);

  /** Like {@link #findOptionsByIdIn}, for a caller that knows the orders by sign (#1334). */
  @Query("""
      select new de.hbt.salat.order.domain.CustomerorderOption(c.id, c.sign, c.shortdescription,
          c.description, cu.shortname, cu.name, c.hide)
      from Customerorder c left join c.customer cu
      where c.sign in :signs
      order by c.sign
      """)
  List<CustomerorderOption> findOptionsBySignIn(Collection<String> signs);

  @Query("select c.id from Customerorder c where c.sign = :sign")
  Optional<Long> findIdBySign(String sign);

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
