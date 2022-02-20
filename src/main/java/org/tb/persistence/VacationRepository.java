package org.tb.persistence;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.tb.bdom.Vacation;

@Repository
public interface VacationRepository extends CrudRepository<Vacation, Long> {

}
