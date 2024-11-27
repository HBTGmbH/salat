package org.tb.statistic.persistence;

import java.util.Optional;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.tb.statistic.domain.StatisticValue;

@Repository
public interface StatisticValueRepository extends CrudRepository<StatisticValue, Long> {

  Optional<StatisticValue> findByCategoryAndKeyAndObjectId(String category, String key, long objectId);

}
