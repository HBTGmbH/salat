package org.tb.employee.persistence;

import jakarta.persistence.QueryHint;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.hibernate.jpa.HibernateHints;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.stereotype.Repository;
import org.tb.employee.domain.Employeecontract;

@Repository
public interface EmployeecontractRepository extends PagingAndSortingRepository<Employeecontract, Long>,
    JpaSpecificationExecutor<Employeecontract>, CrudRepository<Employeecontract, Long> {

  @Query("select e from Employeecontract e where e.employee.id = :employeeId and e.validFrom <= :validAt and (e.validUntil >= :validAt or e.validUntil is null)")
  Optional<Employeecontract> findByEmployeeIdAndValidAt(long employeeId, LocalDate validAt);

  @Query("""
    select ec from Employeecontract ec
    join ec.supervisors s
    where s.id = :supervisorId
    and (ec.hide is null or ec.hide = false)
    order by ec.employee.lastname asc, ec.validFrom asc
  """)
  List<Employeecontract> findAllSupervised(long supervisorId);

  List<Employeecontract> findAllByEmployeeId(Long employeeId);

  /**
   * {@code hide is null} zählt als nicht verborgen — die eine Schreibweise aus
   * {@link org.tb.common.Hiding} (#1104), hier als JPQL, weil ein {@code @Query} nur eine
   * Zeichenkette ist und die Klasse nicht aufrufen kann.
   */
  @Query("""
      select e from Employeecontract e where e.hide is null or e.hide = false
      """)
  List<Employeecontract> findAllNotHidden();

}
