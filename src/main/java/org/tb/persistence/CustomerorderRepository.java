package org.tb.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.tb.bdom.Customerorder;

@Repository
public interface CustomerorderRepository extends CrudRepository<Customerorder, Long> {

  @Query("select c from Customerorder c where c.responsible_hbt.id = :responsibleHbtId and c.statusreport in (:statusreports)")
  List<Customerorder> findAllByResponsibleHbtAndStatusReportIn(long responsibleHbtId, List<Integer> statusreports);

}
