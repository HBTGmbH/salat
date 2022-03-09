package org.tb.dailyreport.persistence;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.tb.dailyreport.domain.Vacation;

@Repository
public interface VacationRepository extends CrudRepository<Vacation, Long> {

}
