package org.tb.persistence;

import java.util.Date;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.tb.bdom.Suborder;

@Repository
public interface SuborderRepository extends CrudRepository<Suborder, Long> {

  @Query("select s from Suborder s where s.standard is true and (s.untilDate is null or s.untilDate >= :refDate) order by s.sign")
  List<Suborder> findAllStandardSubordersByUntilDateGreaterThanEqual(Date refDate);

}
