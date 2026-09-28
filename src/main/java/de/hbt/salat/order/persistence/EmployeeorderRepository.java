package de.hbt.salat.order.persistence;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import de.hbt.salat.order.domain.Employeeorder;

@Repository
public interface EmployeeorderRepository extends CrudRepository<Employeeorder, Long>, JpaSpecificationExecutor<Employeeorder> {

  @Query("""
      select eo from Employeeorder eo where
      eo.employeecontract.id = :employeeContractId and eo.suborder.id = :suborderId and
      (eo.untilDate >= :date or eo.untilDate is null)
      order by eo.suborder.customerorder.sign asc, eo.suborder.sign asc, eo.fromDate asc
      """)
  List<Employeeorder> findAllByEmployeecontractIdAndSuborderIdAndUntilDateGreaterThanEqual(long employeeContractId, long suborderId, LocalDate date);

  @Query("""
      select count(distinct eo) from Employeeorder eo where
      eo.employeecontract.id = :employeeContractId and eo.suborder.id = :suborderId
      """)
  long countEmployeeorders(long employeeContractId, long suborderId);

  List<Employeeorder> findAllByEmployeecontractId(long employeeContractId);

  List<Employeeorder> findAllBySuborderId(long suborderId);

  List<Employeeorder> findAllByEmployeecontractIdAndSuborderId(long employeeContractId, long suborderId);

  List<Employeeorder> findAllByEmployeecontractIdAndSuborderCustomerorderSignIn(long employeecontractId, List<String> customerOrderSigns);

  @Query("select eo from Employeeorder eo where eo.suborder.customerorder.id = :customerorderId and eo.employeecontract.id = :employeecontractId")
  List<Employeeorder> findAllByCustomerorderIdAndEmployeecontractId(long customerorderId, long employeecontractId);

  /**
   * Which of the given suborders the contract may book on the day — an employee order valid then,
   * the condition of the booking form (#1157). Ids only: the palette asks for a handful of hits and
   * needs no entity of them. Callers must not pass an empty collection.
   */
  @Query("""
      select distinct eo.suborder.id from Employeeorder eo
      where eo.employeecontract.id = :employeecontractId and eo.suborder.id in :suborderIds
      and eo.fromDate <= :date and (eo.untilDate is null or eo.untilDate >= :date)
      """)
  List<Long> findBookableSuborderIds(long employeecontractId, Collection<Long> suborderIds, LocalDate date);

}
