package org.tb.order.persistence;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.tb.order.domain.Employeeorder;

@Repository
public interface EmployeeorderRepository extends CrudRepository<Employeeorder, Long>, JpaSpecificationExecutor<Employeeorder> {

  @Query("select eo from Employeeorder eo where "
      + "eo.employeecontract.id = :employeeContractId and eo.suborder.id = :suborderId and "
      + "(eo.untilDate >= :date or eo.untilDate is null) "
      + "order by eo.suborder.customerorder.sign asc, eo.suborder.sign asc, eo.fromDate asc")
  List<Employeeorder> findAllByEmployeecontractIdAndSuborderIdAndUntilDateGreaterThanEqual(long employeeContractId, long suborderId, LocalDate date);

  @Query("select count(distinct eo) from Employeeorder eo where "
         + "eo.employeecontract.id = :employeeContractId and eo.suborder.id = :suborderId")
  long countEmployeeorders(long employeeContractId, long suborderId);

  List<Employeeorder> findAllByEmployeecontractId(long employeeContractId);

  List<Employeeorder> findAllBySuborderId(long suborderId);

  List<Employeeorder> findAllByEmployeecontractIdAndSuborderId(long employeeContractId, long suborderId);

  List<Employeeorder> findAllByEmployeecontractIdAndSuborderCustomerorderSignIn(long employeecontractId, List<String> customerOrderSigns);

  @Query("select eo from Employeeorder eo where eo.suborder.customerorder.id = :customerorderId and eo.employeecontract.id = :employeecontractId")
  List<Employeeorder> findAllByCustomerorderIdAndEmployeecontractId(long customerorderId, long employeecontractId);

}
