package org.tb.persistence;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.tb.bdom.Statusreport;

@Repository
public interface StatusreportRepository extends CrudRepository<Statusreport, Long> {

  @Query("select max(s.untildate) from Statusreport s where s.sort = 1 and s.released is not null and s.customerorder.id = :id")
  Optional<Date> getMaxUntilDateForCustomerOrderId(long id);

  @Query("select s from Statusreport s where s.sort = 3 and s.released is not null and s.customerorder.id = :id "
      + "order by s.customerorder.sign asc, s.sort asc, s.fromdate asc, s.untildate asc, s.sender.sign asc")
  List<Statusreport> getReleasedFinalStatusReportsByCustomerOrderId(long id);

  @Query("select s from Statusreport s where s.sort = 3 and s.released is null and s.customerorder.id = :customerOrderId "
      + "and s.sender.id = :senderId and s.untildate > :date order by s.untildate desc")
  List<Statusreport> getUnreleasedFinalStatusReports(long customerOrderId, long senderId, Date date);

  @Query("select s from Statusreport s where s.sort = 1 and s.released is null and s.customerorder.id = :customerOrderId "
      + "and s.sender.id = :senderId and s.untildate > :date order by s.untildate desc")
  List<Statusreport> getUnreleasedPeriodicalStatusReports(long customerOrderId, long senderId, Date date);

  @Query("select s from Statusreport s where s.released is not null and s.accepted is null and s.recipient.id = :employeeId "
      + "order by s.customerorder.sign asc, s.sort asc, s.fromdate asc, s.untildate asc, s.sender.sign asc")
  List<Statusreport> getReleasedStatusReportsByRecipientId(long employeeId);

  List<Statusreport> findAllByCustomerorderId(long customerorderId);

}
