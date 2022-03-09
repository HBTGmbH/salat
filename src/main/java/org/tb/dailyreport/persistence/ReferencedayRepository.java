package org.tb.dailyreport.persistence;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.tb.dailyreport.domain.Referenceday;

@Repository
public interface ReferencedayRepository extends CrudRepository<Referenceday, Long> {

  Optional<Referenceday> findByRefdate(LocalDate refdate);

}
