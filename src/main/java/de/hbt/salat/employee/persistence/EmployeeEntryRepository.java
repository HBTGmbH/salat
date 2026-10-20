package de.hbt.salat.employee.persistence;

import de.hbt.salat.employee.domain.EmployeeEntry;
import de.hbt.salat.employee.domain.EmployeeEntryDate;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

/** Reads the entry dates before Salat (#1501); they are written by hand, never by the application. */
@Repository
public interface EmployeeEntryRepository extends CrudRepository<EmployeeEntry, Long> {

  @Query("select new de.hbt.salat.employee.domain.EmployeeEntryDate(e.employee.id, e.entryDate) from EmployeeEntry e")
  List<EmployeeEntryDate> findAllEntryDates();

  @Query("select e.entryDate from EmployeeEntry e where e.employee.id = :employeeId")
  Optional<LocalDate> findEntryDateByEmployeeId(long employeeId);
}
