package org.tb.favorites.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.tb.favorites.domain.Favorite;

@Repository
public interface FavoriteRepository extends CrudRepository<Favorite, Long>,
    JpaSpecificationExecutor<Favorite> {

  List<Favorite> findAllByEmployeeId(long employeeId);
}
