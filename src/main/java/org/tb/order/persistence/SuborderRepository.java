package org.tb.order.persistence;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.tb.order.domain.Suborder;

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
}
