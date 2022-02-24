package org.tb.order;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EmployeeorderRepository extends CrudRepository<Employeeorder, Long>, JpaSpecificationExecutor<Employeeorder> {

  @Query("select eo from Employeeorder eo where "
      + "eo.employeecontract.id = :employeeContractId and eo.suborder.id = :suborderId and "
      + "(eo.untilDate >= :date or eo.untilDate is null) "
      + "order by eo.suborder.customerorder.sign asc, eo.suborder.sign asc, eo.fromDate asc")
  List<Employeeorder> findAllByEmployeecontractIdAndSuborderIdAndUntilDateGreaterThanEqual(long employeeContractId, long suborderId, LocalDate date);

  List<Employeeorder> findAllByEmployeecontractId(long employeeContractId);

  List<Employeeorder> findAllBySuborderId(long suborderId);

  List<Employeeorder> findAllByEmployeecontractIdAndSuborderId(long employeeContractId, long suborderId);

  List<Employeeorder> findAllByEmployeecontractIdAndSuborderCustomerorderSignIn(long employeecontractId, List<String> customerOrderSigns);

  @Query("select eo from Employeeorder eo where eo.suborder.customerorder.id = :customerorderId and eo.employeecontract.id = :employeecontractId")
  List<Employeeorder> findAllByCustomerorderIdAndEmployeecontractId(long customerorderId, long employeecontractId);

  @Query("select eo from Employeeorder eo where (eo.employeeOrderContent.committed_emp <> true and eo.employeecontract.employee.id = :employeeId) "
      + "or (eo.employeeOrderContent.committed_mgmt <> true and eo.employeeOrderContent.contactTechHbt.id = :employeeId)")
  List<Employeeorder> findAllByEmployeeIdAndEmployeeOrderContentUncommitted(long employeeId);

  Optional<Employeeorder> findByEmployeeOrderContentId(long employeeOrderContentId);

}
