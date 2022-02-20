package org.tb.persistence;

import java.util.Date;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.tb.bdom.Employeeorder;

@Repository
public interface EmployeeorderRepository extends CrudRepository<Employeeorder, Long> {

  @Query("select eo from Employeeorder eo where "
      + "eo.employeecontract.id = :employeeContractId and eo.suborder.id = :suborderId and "
      + "(eo.untilDate >= :date or eo.untilDate is null) "
      + "order by eo.suborder.customerorder.sign asc, eo.suborder.sign asc, eo.fromDate asc")
  List<Employeeorder> findAllByEmployeecontractIdAndSuborderIdAndUntilDateGreaterThanEqual(long employeeContractId, long suborderId, Date date);

  List<Employeeorder> findAllByEmployeecontractIdAndSuborderId(long employeeContractId, long suborderId);

  List<Employeeorder> findAllByEmployeecontractIdAndSuborderCustomerorderSignIn(long employeecontractId, List<String> customerOrderSigns);

  @Query("select eo from Employeeorder eo where (eo.employeeOrderContent.committed_emp <> true and eo.employeecontract.employee.id = :employeeId) "
      + "or (eo.employeeOrderContent.committed_mgmt <> true and eo.employeeOrderContent.contactTechHbt.id = :employeeId)")
  List<Employeeorder> findAllByEmployeeIdAndEmployeeOrderContentUncommitted(long employeeId);
}
