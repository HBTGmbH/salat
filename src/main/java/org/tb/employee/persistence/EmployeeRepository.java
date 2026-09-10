package org.tb.employee.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.tb.employee.domain.Employee;

@Repository
public interface EmployeeRepository extends PagingAndSortingRepository<Employee, Long>, JpaSpecificationExecutor<Employee>,
    CrudRepository<Employee, Long> {

  Optional<Employee> findBySign(String sign);

  @Query("SELECT e FROM Employee e WHERE e.salatUser.loginname = :loginname")
  Optional<Employee> findByLoginname(@Param("loginname") String loginname);

  /**
   * Every sign in use (→ {@code EmployeeService#getAllEmployeeSigns}). A projection rather than
   * {@code findAll()}: the employees themselves are not wanted, and loading them would drag every
   * {@link org.tb.auth.domain.SalatUser} along with a select of its own.
   */
  @Query("SELECT e.sign FROM Employee e WHERE e.sign IS NOT NULL")
  List<String> findAllSigns();

  /**
   * Wie {@code findAll()}, lädt den {@link org.tb.auth.domain.SalatUser} aber im selben
   * Statement mit. {@code Employee.salatUser} ist ein {@code @ManyToOne} über eine
   * {@code @JoinTable} und damit EAGER; ohne Join-Fetch löst Hibernate die Assoziation
   * mit einem Sekundär-Select pro Zeile auf (N+1).
   */
  @Query("SELECT e FROM Employee e LEFT JOIN FETCH e.salatUser")
  List<Employee> findAllWithSalatUser();

}
