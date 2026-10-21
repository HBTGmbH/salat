package de.hbt.salat.dailyreport.persistence;

import de.hbt.salat.dailyreport.domain.LastReleaseReminder;
import de.hbt.salat.dailyreport.domain.ReleaseReminder;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface ReleaseReminderRepository extends JpaRepository<ReleaseReminder, Long> {

  @Query("select r.employeecontractId from ReleaseReminder r where r.remindedOn = :remindedOn")
  Set<Long> findEmployeecontractIdsByRemindedOn(LocalDate remindedOn);

  @Query("""
      select new de.hbt.salat.dailyreport.domain.LastReleaseReminder(r.employeecontractId, max(r.remindedOn))
      from ReleaseReminder r where r.employeecontractId in :employeecontractIds
      group by r.employeecontractId
      """)
  List<LastReleaseReminder> findLastByEmployeecontractIdIn(Collection<Long> employeecontractIds);

  @Modifying
  @Query("delete from ReleaseReminder r where r.employeecontractId = :employeecontractId")
  void deleteByEmployeecontractId(long employeecontractId);
}
