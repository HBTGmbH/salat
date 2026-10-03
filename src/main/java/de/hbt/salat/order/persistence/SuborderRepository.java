package de.hbt.salat.order.persistence;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import de.hbt.salat.order.domain.Suborder;
import de.hbt.salat.order.domain.SuborderSearchRow;
import de.hbt.salat.order.domain.SuborderSignRow;

@Repository
public interface SuborderRepository extends CrudRepository<Suborder, Long>, JpaSpecificationExecutor<Suborder> {

  @Query("""
    select distinct so from Suborder so
    inner join fetch so.customerorder co
    where so.standard = true and (so.untilDate is null or so.untilDate >= :refDate)
    order by co.sign asc, so.sign asc
  """)
  List<Suborder> findAllStandardSubordersByUntilDateGreaterThanEqual(LocalDate refDate);

  @Query("""
    select distinct so from Employeeorder eo
    inner join eo.suborder so
    inner join fetch so.customerorder co
    where eo.employeecontract.id = :employeecontractId
    order by co.sign asc, so.sign asc
  """)
  List<Suborder> findAllByEmployeecontractId(long employeecontractId);

  @Query("""
    select distinct so from Employeeorder eo
    inner join eo.suborder so
    inner join fetch so.customerorder co
    where eo.employeecontract.id = :employeecontractId
    and eo.fromDate <= :date and (eo.untilDate is null or eo.untilDate >= :date)
    order by co.sign asc, so.sign asc
  """)
  List<Suborder> findAllByEmployeecontractIdAndEmployeeorderValidAt(long employeecontractId, LocalDate date);

  @Query("""
    select distinct so from Employeeorder eo
    inner join eo.suborder so
    inner join fetch so.customerorder co
    where eo.employeecontract.id = :employeecontractId
    and so.customerorder.id = :customerorderId
    and eo.fromDate <= :date and (eo.untilDate is null or eo.untilDate >= :date)
    order by co.sign asc, so.sign asc
  """)
  List<Suborder> findAllByEmployeecontractIdAndCustomerorderIdAndEmployeeorderValidAt(long employeecontractId, long customerorderId, LocalDate date);

  List<Suborder> findAllByCustomerorderId(long customerorderId, Sort sort);

  /**
   * The suborders of the given customer orders, hidden ones included, in one statement (#1222).
   * The order comes along in the same statement: {@code Suborder#getCompleteOrderSign()} reads it
   * for every suborder, and the eager association would otherwise select it once per order.
   * Callers must not pass an empty collection — {@code IN ()} is not valid SQL.
   */
  @Query("""
      select s from Suborder s join fetch s.customerorder c
      where c.sign in :customerorderSigns
      """)
  List<Suborder> findAllByCustomerorderSigns(Collection<String> customerorderSigns);

  /** Like {@link #findAllByCustomerorderSigns}, by the ids of the orders (#1205). */
  @Query("""
      select s from Suborder s join fetch s.customerorder c
      where c.id in :customerorderIds
      """)
  List<Suborder> findAllByCustomerorderIds(Collection<Long> customerorderIds);

  /**
   * Candidates for the object search of the command palette (#1157): every suborder whose sign or
   * short description, whose order's sign or short description, or whose customer contains each of
   * the words. A missing word is {@code null} and no condition. The long description is a
   * {@code @Lob} and cannot go through {@code lower} (#665).
   *
   * <p>{@code employeeId} narrows the result to the suborders that person may book today, through an
   * employee order of one of their contracts; {@code null} leaves every suborder in.
   *
   * <p>A suborder counts as hidden when it or its order is — the flag is not inherited in the data,
   * but a suborder under a hidden order is no more on offer than the order itself. The order puts
   * hidden and ended suborders last, so the limit cuts those first; whether a row is hidden or ended
   * is decided in Java again, by {@link de.hbt.salat.common.Hiding} and {@link de.hbt.salat.common.Validity}.
   */
  @Query("""
      select new de.hbt.salat.order.domain.SuborderSearchRow(s.id, s.sign, s.shortdescription, p.id,
          c.id, c.sign, c.shortdescription, cu.shortname, s.hide, c.hide, s.untilDate)
      from Suborder s join s.customerorder c join c.customer cu left join s.parentorder p
      where (lower(s.sign) like :word1 escape '!' or lower(s.shortdescription) like :word1 escape '!'
          or lower(c.sign) like :word1 escape '!' or lower(c.shortdescription) like :word1 escape '!'
          or lower(cu.shortname) like :word1 escape '!')
      and (:word2 is null or lower(s.sign) like :word2 escape '!'
          or lower(s.shortdescription) like :word2 escape '!' or lower(c.sign) like :word2 escape '!'
          or lower(c.shortdescription) like :word2 escape '!' or lower(cu.shortname) like :word2 escape '!')
      and (:word3 is null or lower(s.sign) like :word3 escape '!'
          or lower(s.shortdescription) like :word3 escape '!' or lower(c.sign) like :word3 escape '!'
          or lower(c.shortdescription) like :word3 escape '!' or lower(cu.shortname) like :word3 escape '!')
      and (:employeeId is null or exists (
          select eo.id from Employeeorder eo
          where eo.suborder = s and eo.employeecontract.employee.id = :employeeId
          and eo.fromDate <= :today and (eo.untilDate is null or eo.untilDate >= :today)))
      order by case when (s.hide = true or c.hide = true) then 1 else 0 end,
          case when s.untilDate < :today then 1 else 0 end,
          c.sign, s.sign
      """)
  List<SuborderSearchRow> findPaletteCandidates(String word1, String word2, String word3,
      Long employeeId, LocalDate today, Pageable page);

  /** Sign and parent of each suborder, one step of the parent chain for many suborders at once. */
  @Query("""
      select new de.hbt.salat.order.domain.SuborderSignRow(s.id, s.sign, p.id)
      from Suborder s left join s.parentorder p
      where s.id in :ids
      """)
  List<SuborderSignRow> findSignRows(Collection<Long> ids);
}
