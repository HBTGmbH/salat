package org.tb.dailyreport.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.tb.dailyreport.domain.Publicholiday;

@Repository
public interface PublicholidayRepository extends CrudRepository<Publicholiday, Long> {

  Optional<Publicholiday> findByRefdate(LocalDate date);

  @Query("select ph from Publicholiday ph where ph.refdate >= coalesce(:start, ph.refdate) and ph.refdate <= coalesce(:end, ph.refdate)")
  List<Publicholiday> findAllByRefdateBetween(LocalDate start, LocalDate end);
}
