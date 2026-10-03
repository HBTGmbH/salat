package de.hbt.salat.dailyreport.persistence;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import de.hbt.salat.dailyreport.domain.Referenceday;

@Repository
public interface ReferencedayRepository extends CrudRepository<Referenceday, Long> {

  Optional<Referenceday> findByRefdate(LocalDate refdate);

  List<Referenceday> findAllByRefdateIn(Collection<LocalDate> refdates);

}
