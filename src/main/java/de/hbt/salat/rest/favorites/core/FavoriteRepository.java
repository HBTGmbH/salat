package de.hbt.salat.rest.favorites.core;

import java.util.List;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FavoriteRepository extends CrudRepository<Favorite, Long>,
    JpaSpecificationExecutor<Favorite> {

  List<Favorite> findAllByEmployeeId(long employeeId);
}
