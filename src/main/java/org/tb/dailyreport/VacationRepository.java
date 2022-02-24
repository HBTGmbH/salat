package org.tb.dailyreport;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VacationRepository extends CrudRepository<Vacation, Long> {

}
