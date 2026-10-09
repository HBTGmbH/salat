package de.hbt.salat.employee.persistence;

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
import de.hbt.salat.employee.domain.Employeecontract;
import de.hbt.salat.employee.domain.PersonSearchRow;

@Repository
public interface EmployeecontractRepository extends PagingAndSortingRepository<Employeecontract, Long>,
    JpaSpecificationExecutor<Employeecontract>, CrudRepository<Employeecontract, Long> {

  @Query("select e from Employeecontract e where e.employee.id = :employeeId and e.validFrom <= :validAt and (e.validUntil >= :validAt or e.validUntil is null)")
  Optional<Employeecontract> findByEmployeeIdAndValidAt(long employeeId, LocalDate validAt);

  /** Die Verträge der Person, die den Zeitraum ganz oder teilweise abdecken, nach Beginn geordnet (#1450). */
  @Query("select e from Employeecontract e where e.employee.id = :employeeId and e.validFrom <= :until and (e.validUntil >= :from or e.validUntil is null) order by e.validFrom")
  List<Employeecontract> findAllByEmployeeIdAndValidBetween(long employeeId, LocalDate from, LocalDate until);

  @Query("""
    select ec from Employeecontract ec
    join ec.supervisors s
    where s.id = :supervisorId
    and (ec.hide is null or ec.hide = false)
    order by ec.employee.lastname asc, ec.validFrom asc
  """)
  List<Employeecontract> findAllSupervised(long supervisorId);

  List<Employeecontract> findAllByEmployeeId(Long employeeId);

  /** Die Person des Vertrags (#1414). */
  @Query("select e.employee.id from Employeecontract e where e.id = :employeecontractId")
  Optional<Long> findEmployeeIdById(long employeecontractId);

  /** Ob die Person nach {@code validUntil} einen Vertrag hat, für den schon freigegeben wurde (#1215). */
  @Query("""
      select count(e) > 0 from Employeecontract e
      where e.employee.id = :employeeId and e.validFrom > :validUntil and e.reportReleaseDate is not null
      """)
  boolean existsReleasedContractAfter(long employeeId, LocalDate validUntil);

  /**
   * {@code hide is null} zählt als nicht verborgen — die eine Schreibweise aus
   * {@link de.hbt.salat.common.Hiding} (#1104), hier als JPQL, weil ein {@code @Query} nur eine
   * Zeichenkette ist und die Klasse nicht aufrufen kann.
   */
  @Query("""
      select e from Employeecontract e where e.hide is null or e.hide = false
      """)
  List<Employeecontract> findAllNotHidden();

  /**
   * Candidates for the object search of the command palette (#1157): the contracts of every person
   * whose sign, first or last name contains each of the words, hidden and ended ones included and
   * ordered last. A missing word is {@code null} and no condition.
   *
   * <p>The visibility is the one of {@link de.hbt.salat.employee.auth.EmployeecontractAuthorization} with
   * READ, written out so the limit applies to readable rows only: everything for a manager
   * ({@code all}), the own contracts, and for a people lead those they supervise. The service checks
   * the kept rows against that class once more — this condition only narrows. The technical
   * administrator and anonymized persons ({@code ANON-…}, a sign that carries a database id) are no
   * one to find.
   */
  @Query("""
      select new de.hbt.salat.employee.domain.PersonSearchRow(ec.id, e.id, e.sign, e.firstname, e.lastname,
          ec.validFrom, ec.validUntil, ec.hide, e.hide)
      from Employeecontract ec join ec.employee e left join e.salatUser u
      where e.sign <> :adminSign and e.sign not like 'ANON-%'
      and (lower(e.sign) like :word1 escape '!' or lower(e.firstname) like :word1 escape '!'
          or lower(e.lastname) like :word1 escape '!')
      and (:word2 is null or lower(e.sign) like :word2 escape '!'
          or lower(e.firstname) like :word2 escape '!' or lower(e.lastname) like :word2 escape '!')
      and (:word3 is null or lower(e.sign) like :word3 escape '!'
          or lower(e.firstname) like :word3 escape '!' or lower(e.lastname) like :word3 escape '!')
      and (:all = true or u.loginname = :login
          or (:peopleLead = true and exists (
              select s.id from Employeecontract x join x.supervisors s join s.salatUser su
              where x = ec and su.loginname = :login)))
      order by case when (e.hide = true or ec.hide = true) then 1 else 0 end,
          case when ec.validUntil < :today then 1 else 0 end,
          e.lastname, e.firstname, ec.validFrom desc
      """)
  List<PersonSearchRow> findPaletteCandidates(String word1, String word2, String word3, boolean all,
      String login, boolean peopleLead, String adminSign, LocalDate today, Pageable page);

  /**
   * The given contracts with everything {@link de.hbt.salat.employee.auth.EmployeecontractAuthorization}
   * and {@link de.hbt.salat.employee.auth.EmployeeAuthorization} read, in one query instead of one per
   * association and contract.
   */
  @Query("""
      select distinct ec from Employeecontract ec
      join fetch ec.employee e left join fetch e.salatUser
      left join fetch ec.supervisors s left join fetch s.salatUser
      where ec.id in :ids
      """)
  List<Employeecontract> findAllForAuthorizationByIdIn(Collection<Long> ids);

}
