package org.tb.persistence;

import java.util.Date;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.tb.bdom.Suborder;

@Repository
public interface SuborderRepository extends CrudRepository<Suborder, Long>, JpaSpecificationExecutor<Suborder> {

  @Query("""
    select distinct so from Suborder so
    inner join fetch so.customerorder co
    where so.standard is true and (so.untilDate is null or so.untilDate >= :refDate)
    order by co.sign asc, so.sign asc
  """)
  List<Suborder> findAllStandardSubordersByUntilDateGreaterThanEqual(Date refDate);

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
  List<Suborder> findAllByEmployeecontractIdAndEmployeeorderValidAt(long employeecontractId, Date date);

  @Query("""
    select distinct so from Employeeorder eo
    inner join eo.suborder so
    inner join fetch so.customerorder co
    where eo.employeecontract.id = :employeecontractId
    and so.customerorder.id = :customerorderId
    and eo.fromDate <= :date and (eo.untilDate is null or eo.untilDate >= :date)
    order by co.sign asc, so.sign asc
  """)
  List<Suborder> findAllByEmployeecontractIdAndCustomerorderIdAndEmployeeorderValidAt(long employeecontractId, long customerorderId, Date date);

  List<Suborder> findAllByCustomerorderId(long customerorderId, Sort sort);
}
