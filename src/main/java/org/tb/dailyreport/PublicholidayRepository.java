package org.tb.dailyreport;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PublicholidayRepository extends CrudRepository<Publicholiday, Long> {

  Optional<Publicholiday> findByRefdate(LocalDate date);

  @Query("select ph from Publicholiday ph where ph.refdate >= :start and ph.refdate <= :end")
  List<Publicholiday> findAllByRefdateBetween(LocalDate start, LocalDate end);
}
