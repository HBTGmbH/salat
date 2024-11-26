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

  @QueryHints(value = {
          @QueryHint(name = HibernateHints.HINT_CACHEABLE, value = "true"),
          @QueryHint(name = HibernateHints.HINT_CACHE_REGION, value = "EmployeecontractRepository.findAllValidAtAndNotHidden")
    }
  )
  @Query("""
      select e from Employeecontract e where (e.hide = false or e.hide is null)
      or (e.validFrom <= :date and (e.validUntil >= :date or e.validUntil is null))
      """)
  List<Employeecontract> findAllValidAtAndNotHidden(LocalDate date);

  @Query("""
    select ec from Employeecontract ec
    where ec.supervisor.id = :supervisorId and ec.validFrom <= :date and (ec.validUntil is null or ec.validUntil >= :date)
    order by ec.employee.lastname asc, ec.validFrom asc
  """)
  List<Employeecontract> findAllSupervisedValidAt(long supervisorId, LocalDate date);

  List<Employeecontract> findAllByEmployeeId(Long employeeId);

}
