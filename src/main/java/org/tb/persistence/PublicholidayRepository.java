package org.tb.persistence;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.tb.bdom.Publicholiday;

@Repository
public interface PublicholidayRepository extends CrudRepository<Publicholiday, Long> {

  Optional<Publicholiday> findByRefdate(Date date);

  @Query("select ph from Publicholiday ph where ph.refdate >= :start and ph.refdate <= :end")
  List<Publicholiday> findAllByRefdateBetween(Date start, Date end);
}
